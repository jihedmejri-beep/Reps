package com.reps.app.data.exercise

import android.util.Log
import com.reps.app.data.exercise.db.ExerciseDao
import com.reps.app.domain.model.MuscleDiagram
import com.reps.app.domain.model.MuscleTarget
import com.reps.app.domain.repository.MuscleSvgRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks muscle artwork up in the bundled catalogue.
 *
 * The whole index is 30 overlay rows and 2 body diagrams, so it is read once and
 * held: re-querying it on every exercise the user opens would be two queries for
 * data that cannot change without a new APK.
 */
@Singleton
class CatalogMuscleSvgRepository @Inject constructor(
    private val dao: ExerciseDao,
) : MuscleSvgRepository {

    private val mutex = Mutex()
    private var index: Index? = null

    override suspend fun diagramFor(muscles: List<MuscleTarget>): MuscleDiagram {
        val loaded = index() ?: return MuscleDiagram()

        val frontPrimary = mutableListOf<String>()
        val frontSecondary = mutableListOf<String>()
        val backPrimary = mutableListOf<String>()
        val backSecondary = mutableListOf<String>()

        for (muscle in muscles) {
            // `isFront` is null only if the catalogue never recorded a side; with
            // no side there is no diagram to draw it on, so it is skipped rather
            // than guessed onto the front.
            val onFront = muscle.isFront ?: continue
            val variant = if (muscle.isPrimary) VARIANT_MAIN else VARIANT_SECONDARY
            val asset = loaded.overlays[muscle.name to variant] ?: continue
            when {
                onFront && muscle.isPrimary -> frontPrimary
                onFront -> frontSecondary
                muscle.isPrimary -> backPrimary
                else -> backSecondary
            }.add(asset)
        }

        return MuscleDiagram(
            frontBodyAsset = loaded.frontBody,
            backBodyAsset = loaded.backBody,
            frontPrimary = frontPrimary,
            frontSecondary = frontSecondary,
            backPrimary = backPrimary,
            backSecondary = backSecondary,
        )
    }

    private suspend fun index(): Index? = index ?: mutex.withLock {
        index ?: runCatching {
            val overlays = dao.muscleSvgAssets()
                .associate { (it.muscleName to it.variant) to it.assetPath }
            val bodies = dao.bodyDiagrams().associate { it.side to it.assetPath }
            Index(
                overlays = overlays,
                frontBody = bodies[SIDE_FRONT],
                backBody = bodies[SIDE_BACK],
            )
        }.onFailure {
            Log.e(TAG, "muscle SVG index unavailable; diagrams will be hidden", it)
        }.getOrNull()?.also { index = it }
    }

    private class Index(
        val overlays: Map<Pair<String, String>, String>,
        val frontBody: String?,
        val backBody: String?,
    )

    private companion object {
        const val TAG = "CatalogMuscleSvg"
        const val VARIANT_MAIN = "main"
        const val VARIANT_SECONDARY = "secondary"
        const val SIDE_FRONT = "front"
        const val SIDE_BACK = "back"
    }
}
