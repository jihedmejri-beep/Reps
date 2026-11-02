package com.reps.app.domain.progress

import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.WorkoutSession
import java.time.LocalDate

/**
 * The single definition of a personal record, shared by the session save (which
 * notifies), the Progress screen (which lists) and achievements (which count).
 *
 * A set ranks by weight, then reps: 100 x 6 beats 100 x 5, and 100 x 5 beats
 * 90 x 12. Bodyweight sets (0 kg) therefore rank by reps, which is what makes a
 * calisthenics record possible - the old weight x reps "volume" rule scored every
 * 0 kg set as 0 and could never fire for pull-ups.
 *
 * A record has to beat something. The first set ever logged on an exercise is a
 * *first best*, not a PR: previously every new exercise "set a record" and sent a
 * notification on day one.
 */
object PersonalRecords {

    val setOrder: Comparator<ExerciseSet> = compareBy<ExerciseSet>({ it.weightKg }, { it.reps })

    /** The best completed set with at least one rep, or null when nothing was done. */
    fun bestOf(sets: List<ExerciseSet>): ExerciseSet? =
        sets.filter { it.completed && it.reps > 0 }.maxWithOrNull(setOrder)

    fun isRecord(previousBest: ExerciseSet?, candidate: ExerciseSet?): Boolean =
        previousBest != null && candidate != null && setOrder.compare(candidate, previousBest) > 0

    /**
     * Exercise ids in [session] whose best set beats [previousBestByExercise], the
     * best set per exercise across every earlier session.
     */
    fun recordsIn(session: WorkoutSession, previousBestByExercise: Map<String, ExerciseSet>): List<String> =
        session.exercises.mapNotNull { exercise ->
            exercise.exerciseId.takeIf {
                isRecord(previousBestByExercise[it], bestOf(exercise.sets))
            }
        }.distinct()

    /** Replays [sessions] in order and returns every record event, oldest first. */
    fun history(sessions: List<WorkoutSession>): List<RecordEvent> {
        val best = mutableMapOf<String, ExerciseSet>()
        val events = mutableListOf<RecordEvent>()
        for (session in sessions.sortedWith(compareBy({ it.date }, { it.endedAtMs }))) {
            for (exercise in session.exercises) {
                val candidate = bestOf(exercise.sets) ?: continue
                val previous = best[exercise.exerciseId]
                if (previous == null || setOrder.compare(candidate, previous) > 0) {
                    events += RecordEvent(exercise.exerciseId, candidate, previous, session.date, session.id)
                    best[exercise.exerciseId] = candidate
                }
            }
        }
        return events
    }

    /** The standing best per exercise, most recently set first. */
    fun current(sessions: List<WorkoutSession>): List<RecordEvent> =
        history(sessions).associateBy { it.exerciseId }.values.sortedByDescending { it.date }

    /** Genuine improvements only - first bests excluded. */
    fun improvementCount(sessions: List<WorkoutSession>): Int = history(sessions).count { it.isImprovement }
}

/**
 * One moment an exercise's best changed. [previous] null means this was the
 * first set ever logged for it.
 */
data class RecordEvent(
    val exerciseId: String,
    val set: ExerciseSet,
    val previous: ExerciseSet?,
    val date: LocalDate,
    val sessionId: String,
) {
    val isImprovement: Boolean get() = previous != null

    /** Kilograms gained over the previous best, when the weight went up. */
    val weightGainKg: Double? get() = previous?.let { set.weightKg - it.weightKg }?.takeIf { it > 0.0 }

    /** Reps gained at the same weight, when that is how the record was set. */
    val repGain: Int? get() = previous
        ?.takeIf { it.weightKg == set.weightKg }
        ?.let { set.reps - it.reps }
        ?.takeIf { it > 0 }

    val isBodyweight: Boolean get() = set.weightKg == 0.0
}
