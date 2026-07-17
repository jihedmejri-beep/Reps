package com.reps.app.domain.model

import java.time.LocalDate

/** Always kilograms. Imperial input is converted before it reaches here. */
data class WeightEntry(
    val id: String = "",
    val date: LocalDate = LocalDate.now(),
    val weightKg: Double = 0.0,
)
