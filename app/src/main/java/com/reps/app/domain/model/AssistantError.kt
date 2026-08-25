package com.reps.app.domain.model

/**
 * Failures the UI needs to tell apart. The assistant depends on two external
 * services, and "USDA is down" deserves a different message from "you are
 * offline" - retrying only helps for some of these.
 */
enum class AssistantError {
    Network,
    NotSignedIn,
    RateLimited,
    ModelUnavailable,
    FoodDatabaseUnavailable,
    Unknown,
    ;

    val isRetryable: Boolean
        get() = this != NotSignedIn
}
