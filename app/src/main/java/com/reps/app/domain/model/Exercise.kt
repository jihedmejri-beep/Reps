package com.reps.app.domain.model

import androidx.annotation.StringRes
import com.reps.app.R

enum class MuscleGroup(@param:StringRes val labelRes: Int) {
    CHEST(R.string.muscle_chest),
    BACK(R.string.muscle_back),
    LEGS(R.string.muscle_legs),
    SHOULDERS(R.string.muscle_shoulders),
    ARMS(R.string.muscle_arms),
    ABS(R.string.muscle_abs),
    GLUTES(R.string.muscle_glutes),
    CARDIO(R.string.muscle_cardio),
}

enum class Difficulty(@param:StringRes val labelRes: Int) {
    BEGINNER(R.string.difficulty_beginner),
    INTERMEDIATE(R.string.difficulty_intermediate),
    ADVANCED(R.string.difficulty_advanced),
}

data class Exercise(
    val id: String = "",
    val name: String = "",
    val muscleGroup: MuscleGroup = MuscleGroup.CHEST,
    val equipment: String = "",
    /** GIF or short video demonstrating the movement. */
    val mediaUrl: String = "",
    val description: String = "",
    val mistakes: List<String> = emptyList(),
    val difficulty: Difficulty = Difficulty.BEGINNER,
)
