package com.reps.app.domain.model

/**
 * A workout in progress, persisted as it changes so leaving the screen, process
 * death or a swiped-away task never loses logged sets. Finishing or discarding
 * the session deletes it.
 */
data class SessionDraft(
    val templateId: String,
    val name: String,
    /** Wall clock (epoch ms) when the session started; the measured duration runs from here. */
    val startedAtMs: Long,
    val exercises: List<WorkoutExercise>,
    val rest: RestState? = null,
)

/**
 * The rest between two sets, as timestamps rather than a ticking counter: the
 * remaining time is always `endsAtMs - now`, so it cannot drift, keeps counting
 * while the app is in the background and is exact again after process death.
 *
 * Running: [endsAtMs] is set, [pausedRemainingMs] is null.
 * Paused: [pausedRemainingMs] holds what was left, [endsAtMs] is null.
 */
data class RestState(
    val exerciseId: String,
    /** Index of the set just completed; the next set is `setIndex + 1`. */
    val setIndex: Int,
    val durationMs: Long,
    val endsAtMs: Long? = null,
    val pausedRemainingMs: Long? = null,
) {
    fun remainingMs(nowMs: Long): Long = when {
        pausedRemainingMs != null -> pausedRemainingMs
        endsAtMs != null -> (endsAtMs - nowMs).coerceAtLeast(0)
        else -> 0
    }

    fun isFinished(nowMs: Long): Boolean = remainingMs(nowMs) == 0L
    val isPaused: Boolean get() = pausedRemainingMs != null

    /** 1 at the start of the rest, 0 once it is spent. */
    fun progress(nowMs: Long): Float =
        if (durationMs <= 0) 0f else (remainingMs(nowMs).toFloat() / durationMs).coerceIn(0f, 1f)

    fun pause(nowMs: Long): RestState =
        if (isPaused || isFinished(nowMs)) this else copy(endsAtMs = null, pausedRemainingMs = remainingMs(nowMs))

    fun resume(nowMs: Long): RestState =
        pausedRemainingMs?.let { copy(endsAtMs = nowMs + it, pausedRemainingMs = null) } ?: this

    /** Adds (or with a negative [deltaMs] removes) time; the total grows with it so progress stays honest. */
    fun adjust(deltaMs: Long, nowMs: Long): RestState {
        if (isFinished(nowMs)) return this
        val remaining = (remainingMs(nowMs) + deltaMs).coerceAtLeast(0)
        val total = (durationMs + deltaMs).coerceAtLeast(remaining).coerceAtLeast(1)
        return if (isPaused) copy(pausedRemainingMs = remaining, durationMs = total)
        else copy(endsAtMs = nowMs + remaining, durationMs = total)
    }

    fun skip(nowMs: Long): RestState = copy(endsAtMs = nowMs, pausedRemainingMs = null)

    companion object {
        fun start(exerciseId: String, setIndex: Int, restSeconds: Int, nowMs: Long): RestState {
            val duration = restSeconds * 1_000L
            return RestState(exerciseId, setIndex, duration, endsAtMs = nowMs + duration)
        }
    }
}
