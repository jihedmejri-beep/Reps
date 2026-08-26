package com.reps.app.data.user

import com.reps.app.data.auth.UserSession
import com.reps.app.data.user.db.UserDataDatabase
import com.reps.app.data.user.db.WeightEntryEntity
import com.reps.app.data.user.db.toDomain
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.repository.WeightRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Weigh-ins, persisted locally. The one-entry-per-day rule is enforced by the
 * database's unique (account, date) index rather than by read-modify-write, so
 * logging a day twice rewrites it even if two writes race.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class LocalWeightRepository @Inject constructor(
    private val session: UserSession,
    private val database: UserDataDatabase,
) : WeightRepository {

    private val dao get() = database.weightEntryDao()

    override fun observeEntries(): Flow<List<WeightEntry>> =
        session.uidFlow.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else dao.observeAll(uid).map { rows -> rows.map { it.toDomain() } }
        }

    override suspend fun logWeight(entry: WeightEntry) {
        val uid = session.currentUid ?: return
        dao.upsert(
            WeightEntryEntity(
                id = entry.id,
                uid = uid,
                dateIso = entry.date.toString(),
                weightKg = entry.weightKg,
            ),
        )
    }

    override suspend fun deleteEntry(entryId: String) {
        val uid = session.currentUid ?: return
        dao.delete(entryId, uid)
    }
}
