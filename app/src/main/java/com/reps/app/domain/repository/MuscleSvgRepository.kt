package com.reps.app.domain.repository

import com.reps.app.domain.model.BodyMap
import com.reps.app.domain.model.MuscleDiagram
import com.reps.app.domain.model.MuscleTarget

/**
 * Resolves muscles to the body/overlay artwork that draws them. Backed entirely
 * by assets inside the APK, so it works offline like the rest of the catalogue.
 */
interface MuscleSvgRepository {
    suspend fun diagramFor(muscles: List<MuscleTarget>): MuscleDiagram

    /**
     * The interactive body map's full inventory: both body illustrations and
     * every muscle that has artwork, with its side. Empty muscles list when the
     * artwork index is unavailable.
     */
    suspend fun bodyMap(): BodyMap
}
