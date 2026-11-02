package com.reps.app.domain.model

/**
 * Failures the UI needs to tell apart. The assistant depends on two external
 * services, and "USDA is down" deserves a different message from "you are
 * offline" - retrying only helps for some of these.
 */
enum class AssistantError {
    Network,
    Timeout,
    NotSignedIn,
    RateLimited,
    ModelUnavailable,
    FoodDatabaseUnavailable,

    /** This build has no assistant endpoint (e.g. a release with no server deployed yet). */
    NotConfigured,

    /** The message was rejected before sending, e.g. over the backend's length limit. */
    MessageTooLong,
    Unknown,
    ;

    val isRetryable: Boolean
        get() = this != NotSignedIn && this != NotConfigured && this != MessageTooLong
}
