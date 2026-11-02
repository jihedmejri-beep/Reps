package com.reps.app.domain.progress

import com.reps.app.core.util.StreakCalculator
import com.reps.app.domain.model.WorkoutSession
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Every achievement, with the exact condition that earns it. Conditions only read
 * logged sessions - nothing estimated - and once earned an unlock is stored and
 * never re-locks (see `achievement_unlocks`).
 *
 * Sessions with no completed set are ignored throughout: "Finish anyway" on an
 * empty session is not a workout.
 */
enum class AchievementId(val target: Long) {
    /** One session with at least one completed set. */
    FIRST_WORKOUT(1),

    /** Ten such sessions. */
    TEN_WORKOUTS(10),

    /** Fifty such sessions. */
    FIFTY_WORKOUTS(50),

    /** Seven planned days honoured in a row (needs a schedule). */
    STREAK_7(7),

    /** Four calendar weeks in a row with a session, with or without a schedule. */
    WEEKS_4(4),

    /** First genuine record: beating an earlier best, not the first set ever. */
    FIRST_PR(1),

    /** 10,000 kg lifted across all completed sets (weight x reps). */
    VOLUME_10T(10_000),

    /** 1,000 completed reps, weighted or bodyweight. */
    REPS_1000(1_000),
}

/** Progress toward one achievement. [current] is in the same unit as [AchievementId.target]. */
data class AchievementProgress(val id: AchievementId, val current: Long) {
    val unlocked: Boolean get() = current >= id.target
    val fraction: Float get() = (current.toFloat() / id.target).coerceIn(0f, 1f)
}

/** The measured totals achievements are judged on. */
data class TrainingTotals(
    val workouts: Int,
    val completedSets: Int,
    val reps: Long,
    val volumeKg: Double,
    val records: Int,
    val scheduledStreak: Int,
    val weekStreak: Int,
) {
    companion object {
        fun from(
            sessions: List<WorkoutSession>,
            scheduledDays: Set<DayOfWeek>,
            today: LocalDate = LocalDate.now(),
        ): TrainingTotals {
            val worked = sessions.filter { it.hasWork }
            val sets = worked.flatMap { it.completedSets }
            val dates = worked.map { it.date }.toSet()
            return TrainingTotals(
                workouts = worked.size,
                completedSets = sets.size,
                reps = sets.sumOf { it.reps.toLong() },
                volumeKg = sets.sumOf { it.volume },
                records = PersonalRecords.improvementCount(worked),
                scheduledStreak = if (scheduledDays.isEmpty()) 0 else StreakCalculator.scheduled(dates, scheduledDays, today).count,
                weekStreak = StreakCalculator.weeksInARow(dates, today),
            )
        }
    }
}

object Achievements {

    fun progress(totals: TrainingTotals): List<AchievementProgress> = AchievementId.entries.map { id ->
        AchievementProgress(
            id = id,
            current = when (id) {
                AchievementId.FIRST_WORKOUT, AchievementId.TEN_WORKOUTS, AchievementId.FIFTY_WORKOUTS -> totals.workouts.toLong()
                AchievementId.STREAK_7 -> totals.scheduledStreak.toLong()
                AchievementId.WEEKS_4 -> totals.weekStreak.toLong()
                AchievementId.FIRST_PR -> totals.records.toLong()
                AchievementId.VOLUME_10T -> totals.volumeKg.toLong()
                AchievementId.REPS_1000 -> totals.reps
            },
        )
    }

    /** Achievements [totals] has earned that are not already in [alreadyUnlocked]. */
    fun newlyEarned(totals: TrainingTotals, alreadyUnlocked: Set<AchievementId>): List<AchievementId> =
        progress(totals).filter { it.unlocked && it.id !in alreadyUnlocked }.map { it.id }
}
