package com.reps.app.ai

import com.reps.app.domain.model.Goal

data class ChatRequest(
    val message: String,
    val conversationId: String? = null,
    val userContext: UserContext? = null,
)

data class UserContext(
    val goal: Goal? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val calorieTarget: Double? = null,
    val proteinTarget: Double? = null,
    val carbsTarget: Double? = null,
    val fatTarget: Double? = null,
    val caloriesConsumed: Double? = null,
    val proteinConsumed: Double? = null,
)