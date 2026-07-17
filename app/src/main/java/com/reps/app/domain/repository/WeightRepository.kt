package com.reps.app.domain.repository

import com.reps.app.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow

interface WeightRepository {
    fun observeEntries(): Flow<List<WeightEntry>>
    suspend fun logWeight(entry: WeightEntry)
    suspend fun deleteEntry(entryId: String)
}
