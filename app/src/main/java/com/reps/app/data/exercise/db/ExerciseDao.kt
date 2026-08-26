package com.reps.app.data.exercise.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One flattened library row. Deliberately not the full [ExerciseEntity] graph:
 * the library list shows a name, a category, its equipment and a thumbnail, so
 * that is all the query reads. Loading muscles/steps/aliases for 828 rows to
 * render 8 visible ones is the exact thing this projection avoids.
 */
data class ExerciseListRow(
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "external_id") val externalId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "category") val category: String?,
    @ColumnInfo(name = "equipment") val equipment: String?,
    @ColumnInfo(name = "primary_muscle") val primaryMuscle: String?,
    @ColumnInfo(name = "primary_muscle_en") val primaryMuscleEn: String?,
    @ColumnInfo(name = "thumb_url") val thumbUrl: String?,
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "asset_path") val assetPath: String?,
)

/**
 * One row per muscle in the catalogue, collapsed across every exercise that
 * uses it. `name_en` and `is_front` are functionally dependent on
 * `muscle_name`, so MAX() over the group just picks the one value they all
 * share.
 */
data class MuscleAnatomyRow(
    @ColumnInfo(name = "muscle_name") val muscleName: String,
    @ColumnInfo(name = "name_en") val nameEn: String?,
    @ColumnInfo(name = "is_front") val isFront: Boolean?,
)

/** The single-language text block for one exercise. */
data class ExerciseTextRow(
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "summary") val summary: String,
    @ColumnInfo(name = "starting_position") val startingPosition: String,
    @ColumnInfo(name = "steps_json") val stepsJson: String,
    @ColumnInfo(name = "tips_json") val tipsJson: String,
    @ColumnInfo(name = "notes_json") val notesJson: String,
)

@Dao
interface ExerciseDao {

    /**
     * The library list, filtered and searched in SQLite rather than in memory.
     *
     * `:query` is expected pre-folded by the caller (see `TextFolding`), which is
     * what lets an unaccented French query match an accented name. Passing an
     * empty string disables the text predicate; passing null for `:category` or
     * `:primaryMuscle` disables that predicate. The two are separate because the
     * Glutes group has no catalogue category and filters on muscle instead - see
     * [com.reps.app.data.exercise.CatalogTaxonomy].
     *
     * The correlated subqueries pick one representative image per exercise.
     * `is_main DESC, sort_order ASC` is a total order even for the 10 exercises
     * that upstream marks more than one image as main.
     */
    @Query(
        """
        SELECT e.id                AS id,
               e.external_id       AS external_id,
               t.name              AS name,
               e.category          AS category,
               (SELECT group_concat(q.equipment_name, ', ')
                  FROM exercise_equipment q
                 WHERE q.exercise_id = e.id)                     AS equipment,
               (SELECT m.muscle_name FROM exercise_muscles m
                 WHERE m.exercise_id = e.id AND m.role = 'primary'
                 ORDER BY m.muscle_name LIMIT 1)                 AS primary_muscle,
               (SELECT m.name_en FROM exercise_muscles m
                 WHERE m.exercise_id = e.id AND m.role = 'primary'
                 ORDER BY m.muscle_name LIMIT 1)                 AS primary_muscle_en,
               (SELECT i.thumb_medium_url FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS thumb_url,
               (SELECT i.remote_url FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS image_url,
               (SELECT i.asset_path FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS asset_path
          FROM exercises e
          JOIN exercise_translations t
            ON t.exercise_id = e.id AND t.language_code = :language
         WHERE (:category IS NULL OR e.category = :category)
           AND (:primaryMuscle IS NULL OR EXISTS (
                    SELECT 1 FROM exercise_muscles pm
                     WHERE pm.exercise_id = e.id
                       AND pm.role = 'primary'
                       AND pm.muscle_name = :primaryMuscle))
           AND (:query = ''
                OR t.name_folded LIKE '%' || :query || '%'
                OR t.keywords_folded LIKE '%' || :query || '%')
         ORDER BY t.name COLLATE NOCASE
        """,
    )
    fun observeLibrary(
        language: String,
        category: String?,
        primaryMuscle: String?,
        query: String,
    ): Flow<List<ExerciseListRow>>

    /** Same projection, for the handful of ids a workout references. */
    @Query(
        """
        SELECT e.id                AS id,
               e.external_id       AS external_id,
               t.name              AS name,
               e.category          AS category,
               (SELECT group_concat(q.equipment_name, ', ')
                  FROM exercise_equipment q
                 WHERE q.exercise_id = e.id)                     AS equipment,
               (SELECT m.muscle_name FROM exercise_muscles m
                 WHERE m.exercise_id = e.id AND m.role = 'primary'
                 ORDER BY m.muscle_name LIMIT 1)                 AS primary_muscle,
               (SELECT m.name_en FROM exercise_muscles m
                 WHERE m.exercise_id = e.id AND m.role = 'primary'
                 ORDER BY m.muscle_name LIMIT 1)                 AS primary_muscle_en,
               (SELECT i.thumb_medium_url FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS thumb_url,
               (SELECT i.remote_url FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS image_url,
               (SELECT i.asset_path FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS asset_path
          FROM exercises e
          JOIN exercise_translations t
            ON t.exercise_id = e.id AND t.language_code = :language
         WHERE e.id IN (:ids)
        """,
    )
    suspend fun rowsByIds(ids: List<String>, language: String): List<ExerciseListRow>

    @Query("SELECT * FROM exercises WHERE id = :exerciseId")
    fun observeExercise(exerciseId: String): Flow<ExerciseEntity?>

    @Query(
        """
        SELECT name, summary, starting_position, steps_json, tips_json, notes_json
          FROM exercise_translations
         WHERE exercise_id = :exerciseId AND language_code = :language
        """,
    )
    fun observeText(exerciseId: String, language: String): Flow<ExerciseTextRow?>

    /** Primary muscles first, then alphabetical, so the UI order is stable. */
    @Query(
        """
        SELECT * FROM exercise_muscles
         WHERE exercise_id = :exerciseId
         ORDER BY CASE role WHEN 'primary' THEN 0 ELSE 1 END, muscle_name
        """,
    )
    fun observeMuscles(exerciseId: String): Flow<List<ExerciseMuscleEntity>>

    @Query("SELECT equipment_name FROM exercise_equipment WHERE exercise_id = :exerciseId ORDER BY equipment_name")
    fun observeEquipment(exerciseId: String): Flow<List<String>>

    @Query("SELECT alias FROM exercise_aliases WHERE exercise_id = :exerciseId ORDER BY alias")
    fun observeAliases(exerciseId: String): Flow<List<String>>

    @Query(
        """
        SELECT * FROM exercise_images
         WHERE exercise_id = :exerciseId
         ORDER BY is_main DESC, sort_order ASC
        """,
    )
    fun observeImages(exerciseId: String): Flow<List<ExerciseImageEntity>>

    /** Same projection as the library, restricted to one muscle in any role. */
    @Query(
        """
        SELECT e.id                AS id,
               e.external_id       AS external_id,
               t.name              AS name,
               e.category          AS category,
               (SELECT group_concat(q.equipment_name, ', ')
                  FROM exercise_equipment q
                 WHERE q.exercise_id = e.id)                     AS equipment,
               (SELECT m.muscle_name FROM exercise_muscles m
                 WHERE m.exercise_id = e.id AND m.role = 'primary'
                 ORDER BY m.muscle_name LIMIT 1)                 AS primary_muscle,
               (SELECT m.name_en FROM exercise_muscles m
                 WHERE m.exercise_id = e.id AND m.role = 'primary'
                 ORDER BY m.muscle_name LIMIT 1)                 AS primary_muscle_en,
               (SELECT i.thumb_medium_url FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS thumb_url,
               (SELECT i.remote_url FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS image_url,
               (SELECT i.asset_path FROM exercise_images i
                 WHERE i.exercise_id = e.id
                 ORDER BY i.is_main DESC, i.sort_order ASC LIMIT 1) AS asset_path
          FROM exercises e
          JOIN exercise_translations t
            ON t.exercise_id = e.id AND t.language_code = :language
         WHERE EXISTS (
                  SELECT 1 FROM exercise_muscles pm
                   WHERE pm.exercise_id = e.id
                     AND pm.muscle_name = :muscle)
         ORDER BY t.name COLLATE NOCASE
        """,
    )
    fun observeByMuscle(language: String, muscle: String): Flow<List<ExerciseListRow>>

    @Query("SELECT * FROM muscle_svg_assets")
    suspend fun muscleSvgAssets(): List<MuscleSvgAssetEntity>

    @Query("SELECT * FROM body_diagrams")
    suspend fun bodyDiagrams(): List<BodyDiagramEntity>

    /** The catalogue's full muscle vocabulary with the side each is drawn on. */
    @Query(
        """
        SELECT muscle_name        AS muscle_name,
               MAX(name_en)       AS name_en,
               MAX(is_front)      AS is_front
          FROM exercise_muscles
         GROUP BY muscle_name
         ORDER BY muscle_name
        """,
    )
    suspend fun muscleAnatomy(): List<MuscleAnatomyRow>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun exerciseCount(): Int
}
