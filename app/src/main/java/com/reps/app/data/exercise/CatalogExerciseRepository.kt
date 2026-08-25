package com.reps.app.data.exercise

import android.util.Log
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.data.exercise.db.ExerciseDao
import com.reps.app.data.exercise.db.ExerciseImageEntity
import com.reps.app.data.exercise.db.ExerciseListRow
import com.reps.app.data.exercise.db.ExerciseMuscleEntity
import com.reps.app.data.exercise.db.TextFolding
import com.reps.app.domain.model.Exercise
import com.reps.app.domain.model.ExerciseDetail
import com.reps.app.domain.model.ExerciseMedia
import com.reps.app.domain.model.MuscleGroup
import com.reps.app.domain.model.MuscleTarget
import com.reps.app.domain.repository.ExerciseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the bundled catalogue (`assets/reps_exercises.db`) through Room.
 *
 * Every query is keyed on the user's current language, so switching language in
 * Profile re-emits the whole library in the new one without any screen knowing
 * that happened. All three languages are complete for all 828 exercises, so
 * there is no fallback path to get wrong.
 *
 * A failure to read the catalogue - a corrupt or missing asset - degrades to an
 * empty library rather than taking the app down with it. That is the difference
 * between a broken Workouts tab and a crash loop on launch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class CatalogExerciseRepository @Inject constructor(
    private val dao: ExerciseDao,
    private val media: MediaUrlResolver,
    preferences: UserPreferencesDataStore,
) : ExerciseRepository {

    private val language: Flow<String> = preferences.language
        .map { it.tag }
        .distinctUntilChanged()

    override fun observeExercises(
        muscleGroup: MuscleGroup?,
        query: String,
    ): Flow<List<Exercise>> = language.flatMapLatest { tag ->
        dao.observeLibrary(
            language = tag,
            category = muscleGroup?.let(CatalogTaxonomy::categoryFor),
            primaryMuscle = muscleGroup?.let(CatalogTaxonomy::primaryMuscleFor),
            // Folded here, once, rather than per row in SQL.
            query = TextFolding.fold(query),
        ).map { rows -> rows.map(::toExercise) }
    }.safeCatch("observeExercises", emptyList())

    /**
     * Derived from the detail query rather than given its own: both are keyed on
     * a single indexed id, so this costs a few extra small lookups on one row
     * instead of a third copy of the library projection's SQL.
     */
    override fun observeExercise(exerciseId: String): Flow<Exercise?> =
        observeExerciseDetail(exerciseId).map { it?.exercise }

    override fun observeExerciseDetail(exerciseId: String): Flow<ExerciseDetail?> =
        language.flatMapLatest { tag ->
            combine(
                dao.observeExercise(exerciseId),
                dao.observeText(exerciseId, tag),
                dao.observeMuscles(exerciseId),
                dao.observeEquipment(exerciseId),
                dao.observeImages(exerciseId),
            ) { entity, text, muscles, equipment, images ->
                if (entity == null || text == null) return@combine null
                val resolved = images.map(::toMedia)
                ExerciseDetail(
                    exercise = Exercise(
                        id = entity.id,
                        name = text.name,
                        muscleGroup = CatalogTaxonomy.muscleGroupFor(entity.category),
                        equipment = equipment.joinToString(", "),
                        mediaUrl = resolved.firstOrNull()?.fullUrl.orEmpty(),
                        thumbnailUrl = resolved.firstOrNull()?.thumbnailUrl.orEmpty(),
                        category = entity.category.orEmpty(),
                        difficulty = null,
                    ),
                    externalId = entity.externalId,
                    summary = text.summary,
                    startingPosition = text.startingPosition,
                    steps = parseJsonArray(text.stepsJson),
                    tips = parseJsonArray(text.tipsJson),
                    notes = parseJsonArray(text.notesJson),
                    primaryMuscles = muscles.filter { it.role == ROLE_PRIMARY }.map(::toMuscle),
                    secondaryMuscles = muscles.filterNot { it.role == ROLE_PRIMARY }.map(::toMuscle),
                    equipment = equipment,
                    media = resolved,
                    licenseName = entity.licenseName.orEmpty(),
                    licenseUrl = entity.licenseUrl.orEmpty(),
                )
            }.flatMapLatest { detail ->
                // Aliases are a separate small query; folding it in here keeps
                // combine() within its five-flow overload.
                if (detail == null) flowOf(null)
                else dao.observeAliases(exerciseId).map { detail.copy(aliases = it) }
            }
        }.safeCatch("observeExerciseDetail", null)

    override suspend fun getByIds(ids: List<String>): List<Exercise> {
        if (ids.isEmpty()) return emptyList()
        return runCatching {
            val tag = language.first()
            val byId = dao.rowsByIds(ids.distinct(), tag).associateBy { it.id }
            // Preserve the caller's order: a session must play back in the order
            // the user built, not in catalogue order.
            ids.mapNotNull { byId[it]?.let(::toExercise) }
        }.getOrElse {
            Log.e(TAG, "getByIds failed", it)
            emptyList()
        }
    }

    private fun toExercise(row: ExerciseListRow) = Exercise(
        id = row.id,
        name = row.name,
        muscleGroup = CatalogTaxonomy.muscleGroupFor(row.category),
        equipment = row.equipment.orEmpty(),
        mediaUrl = media.resolveFull(row.assetPath, row.imageUrl),
        thumbnailUrl = media.resolveThumbnail(row.assetPath, row.thumbUrl, row.imageUrl),
        category = row.category.orEmpty(),
        // The catalogue records no difficulty for any exercise.
        difficulty = null,
    )

    private fun toMuscle(entity: ExerciseMuscleEntity) = MuscleTarget(
        name = entity.muscleName,
        commonName = entity.nameEn?.takeIf { it.isNotBlank() },
        isFront = entity.isFront,
        isPrimary = entity.role == ROLE_PRIMARY,
    )

    private fun toMedia(entity: ExerciseImageEntity) = ExerciseMedia(
        id = entity.id,
        fullUrl = media.resolveFull(entity.assetPath, entity.remoteUrl),
        thumbnailUrl = media.resolveThumbnail(
            entity.assetPath,
            entity.thumbMediumUrl,
            entity.remoteUrl,
        ),
        isMain = entity.isMain,
    )

    /**
     * The catalogue's instruction fields are JSON arrays of strings. A malformed
     * one costs that exercise its steps, not the whole screen.
     */
    private fun parseJsonArray(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        buildList(array.length()) {
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
    }.getOrElse {
        Log.w(TAG, "malformed JSON array in catalogue: ${raw.take(80)}", it)
        emptyList()
    }

    private fun <T> Flow<T>.safeCatch(operation: String, fallback: T): Flow<T> =
        catch { throwable ->
            Log.e(TAG, "$operation failed; serving empty catalogue", throwable)
            emit(fallback)
        }

    private companion object {
        const val TAG = "CatalogExerciseRepo"
        const val ROLE_PRIMARY = "primary"
    }
}
