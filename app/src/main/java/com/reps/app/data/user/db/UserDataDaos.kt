package com.reps.app.data.user.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * DAOs for the writable user database. Trees (a workout with its exercises and
 * sets, a meal with its items, a chat with its lines) are loaded through Room
 * `@Relation` projections and written by the repositories inside one
 * `db.withTransaction` - delete-then-insert per tree keeps children from ever
 * drifting out of sync with their parent.
 */

/** A template plus its ordered exercises, each with its ordered sets. */
data class TemplateExerciseWithSets(
    @Embedded val exercise: WorkoutTemplateExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "templateExerciseId")
    val sets: List<WorkoutTemplateSetEntity>,
)

data class TemplateWithExercises(
    @Embedded val template: WorkoutTemplateEntity,
    @Relation(entity = WorkoutTemplateExerciseEntity::class, parentColumn = "id", entityColumn = "templateId")
    val exercises: List<TemplateExerciseWithSets>,
)

/** A performed session plus its ordered exercises, each with its ordered sets. */
data class SessionExerciseWithSets(
    @Embedded val exercise: WorkoutSessionExerciseEntity,
    @Relation(parentColumn = "id", entityColumn = "sessionExerciseId")
    val sets: List<WorkoutSessionSetEntity>,
)

data class SessionWithExercises(
    @Embedded val session: WorkoutSessionEntity,
    @Relation(entity = WorkoutSessionExerciseEntity::class, parentColumn = "id", entityColumn = "sessionId")
    val exercises: List<SessionExerciseWithSets>,
)

data class MealWithItems(
    @Embedded val meal: MealEntity,
    @Relation(parentColumn = "id", entityColumn = "mealId")
    val items: List<MealItemEntity>,
)

data class ConversationWithMessages(
    @Embedded val conversation: AssistantConversationEntity,
    @Relation(parentColumn = "id", entityColumn = "conversationId")
    val messages: List<AssistantMessageEntity>,
)

@Dao
interface UserAccountDao {

    /** Emails are stored lowercase; callers must normalise before calling. */
    @Query("SELECT * FROM user_accounts WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: UserAccountEntity)
}

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    fun observe(uid: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE uid = :uid")
    suspend fun get(uid: String): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfileEntity)
}

@Dao
interface WorkoutDao {

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE uid = :uid ORDER BY createdAtMs ASC")
    fun observeTemplates(uid: String): Flow<List<TemplateWithExercises>>

    @Transaction
    @Query("SELECT * FROM workout_templates WHERE id = :templateId AND uid = :uid LIMIT 1")
    fun observeTemplate(uid: String, templateId: String): Flow<TemplateWithExercises?>

    // --- template writes (composed into one transaction by the repository) ---

    /** Ownership-checked deletes: an account can only ever remove its own rows. */
    @Query(
        """
        DELETE FROM workout_template_exercises
        WHERE templateId IN (SELECT id FROM workout_templates WHERE id = :templateId AND uid = :uid)
        """,
    )
    suspend fun deleteTemplateExercisesScoped(templateId: String, uid: String)

    @Query("DELETE FROM workout_templates WHERE id = :templateId AND uid = :uid")
    suspend fun deleteTemplateRowScoped(templateId: String, uid: String)

    @Query("DELETE FROM workout_templates WHERE id = :templateId")
    suspend fun deleteTemplateRow(templateId: String)

    @Query("DELETE FROM workout_template_exercises WHERE templateId = :templateId")
    suspend fun deleteTemplateExercises(templateId: String)

    @Insert
    suspend fun insertTemplate(template: WorkoutTemplateEntity)

    @Insert
    suspend fun insertTemplateExercises(exercises: List<WorkoutTemplateExerciseEntity>): List<Long>

    @Insert
    suspend fun insertTemplateSets(sets: List<WorkoutTemplateSetEntity>)

    // --- sessions ---

    @Transaction
    @Query("SELECT * FROM workout_sessions WHERE uid = :uid ORDER BY dateIso ASC, rowid ASC")
    fun observeSessions(uid: String): Flow<List<SessionWithExercises>>

    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity)

    @Insert
    suspend fun insertSessionExercises(exercises: List<WorkoutSessionExerciseEntity>): List<Long>

    @Insert
    suspend fun insertSessionSets(sets: List<WorkoutSessionSetEntity>)

    /**
     * The best completed-set volume ever recorded for an exercise. Volume is
     * weight x reps, computed in SQL so the scan stays inside SQLite.
     */
    @Query(
        """
        SELECT MAX(s.weightKg * s.reps)
        FROM workout_session_sets AS s
        JOIN workout_session_exercises AS e ON e.id = s.sessionExerciseId
        JOIN workout_sessions AS w ON w.id = e.sessionId
        WHERE e.exerciseId = :exerciseId AND w.uid = :uid AND s.completed = 1
        """,
    )
    suspend fun bestVolume(exerciseId: String, uid: String): Double?

    /** Distinct session dates for the account, ISO strings, ascending. */
    @Query("SELECT DISTINCT dateIso FROM workout_sessions WHERE uid = :uid ORDER BY dateIso ASC")
    suspend fun sessionDates(uid: String): List<String>

    /** Every scheduled weekday across the account's templates, as DayOfWeek names. */
    @Query("SELECT scheduledDaysCsv FROM workout_templates WHERE uid = :uid")
    suspend fun allScheduledDaysCsv(uid: String): List<String>
}

@Dao
interface WeightEntryDao {

    @Query("SELECT * FROM weight_entries WHERE uid = :uid ORDER BY dateIso ASC")
    fun observeAll(uid: String): Flow<List<WeightEntryEntity>>

    /**
     * REPLACE against the unique (uid, dateIso) index is what makes logging a
     * day twice rewrite that day instead of stacking two points on one bar.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: WeightEntryEntity)

    @Query("DELETE FROM weight_entries WHERE id = :entryId AND uid = :uid")
    suspend fun delete(entryId: String, uid: String)
}

@Dao
interface MealDao {

    @Transaction
    @Query("SELECT * FROM meals WHERE uid = :uid AND dateIso = :dateIso ORDER BY createdAtMs ASC")
    fun observeForDate(uid: String, dateIso: String): Flow<List<MealWithItems>>

    @Query("DELETE FROM meals WHERE id = :mealId")
    suspend fun deleteMealRow(mealId: String)

    @Query("DELETE FROM meals WHERE id = :mealId AND uid = :uid")
    suspend fun deleteMealRowScoped(mealId: String, uid: String)

    @Insert
    suspend fun insertMeal(meal: MealEntity)

    @Insert
    suspend fun insertItems(items: List<MealItemEntity>)
}

@Dao
interface AssistantConversationDao {

    @Transaction
    @Query("SELECT * FROM assistant_conversations WHERE uid = :uid ORDER BY updatedAtMs DESC")
    fun observeConversations(uid: String): Flow<List<ConversationWithMessages>>

    @Query("SELECT COUNT(*) FROM assistant_conversations WHERE uid = :uid")
    suspend fun count(uid: String): Int

    @Query(
        """
        SELECT id FROM assistant_conversations
        WHERE uid = :uid
        ORDER BY updatedAtMs DESC
        LIMIT -1 OFFSET :keep
        """,
    )
    suspend fun idsBeyondNewest(uid: String, keep: Int): List<String>

    @Query("DELETE FROM assistant_conversations WHERE id = :conversationId")
    suspend fun deleteConversationRow(conversationId: String)

    @Query("DELETE FROM assistant_conversations WHERE id = :conversationId AND uid = :uid")
    suspend fun deleteConversationRowScoped(conversationId: String, uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: AssistantConversationEntity)

    @Insert
    suspend fun insertMessages(messages: List<AssistantMessageEntity>)
}
