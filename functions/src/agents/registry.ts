import { HttpsError, onCall, type CallableRequest } from "firebase-functions/v2/https";
import { logger } from "firebase-functions";
import { GROQ_API_KEY, REGION, USDA_API_KEY } from "../config";
import { UsdaUnavailableError } from "../nutrition/usda";

/**
 * Every agent is exposed the same way: authenticated, secret-bound, and with
 * failures mapped onto the error vocabulary the app understands.
 *
 * This is the extension point the architecture calls for. A photo analysis or
 * barcode agent is a new handler passed to defineAgent - it inherits auth,
 * secrets, logging, and error mapping, and no existing agent changes.
 */
export function defineAgent<Req, Res>(
  name: string,
  handler: (payload: Req, uid: string) => Promise<Res>,
) {
  return onCall(
    {
      region: REGION,
      secrets: [GROQ_API_KEY, USDA_API_KEY],
      // The agents are chatty and bursty per user; this bounds a runaway loop
      // without throttling normal use.
      maxInstances: 20,
      // Model calls plus a chain of USDA lookups: the default 60s is not enough
      // for a long ingredient list.
      timeoutSeconds: 120,
      memory: "512MiB",
      enforceAppCheck: false,
    },
    async (request: CallableRequest<Req>): Promise<Res> => {
      const uid = request.auth?.uid;
      if (uid === undefined) {
        // The client turns this into AssistantError.NotSignedIn, the one error
        // it will not offer to retry.
        throw new HttpsError("unauthenticated", "Sign in to use the assistant.", {
          reason: "NotSignedIn",
        });
      }

      try {
        return await handler(request.data, uid);
      } catch (error) {
        throw toHttpsError(name, error);
      }
    },
  );
}

function toHttpsError(agent: string, error: unknown): HttpsError {
  if (error instanceof HttpsError) return error;

  if (error instanceof UsdaUnavailableError) {
    logger.error(`${agent}: USDA unavailable`, error);
    return new HttpsError("unavailable", "The food database is not responding.", {
      reason: "FoodDatabaseUnavailable",
    });
  }

  const status = (error as { status?: number }).status;

  if (status === 429) {
    logger.warn(`${agent}: rate limited by Groq`);
    return new HttpsError("resource-exhausted", "Too many requests, try again shortly.", {
      reason: "RateLimited",
    });
  }

  if (typeof status === "number" && status >= 500) {
    logger.error(`${agent}: Groq returned ${status}`, error);
    return new HttpsError("unavailable", "The assistant is temporarily unavailable.", {
      reason: "ModelUnavailable",
    });
  }

  logger.error(`${agent}: unhandled failure`, error);
  return new HttpsError("internal", "Something went wrong.", { reason: "Unknown" });
}
