package com.reps.app.domain.progress

import com.reps.app.core.util.StreakCalculator
import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.StreakUnit
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ProgressionRulesTest {

    private val monday = LocalDate.of(2026, 11, 2)

    private fun set(kg: Double, reps: Int, done: Boolean = true) = ExerciseSet(weightKg = kg, reps = reps, completed = done)

    private fun session(date: LocalDate, vararg exercises: Pair<String, List<ExerciseSet>>, id: String = date.toString()) =
        WorkoutSession(
            id = id,
            date = date,
            exercises = exercises.mapIndexed { i, (exId, sets) -> WorkoutExercise(exId, i, sets) },
        )

    // --- personal records ---

    @Test
    fun firstEverSet_isNotARecord() {
        assertFalse(PersonalRecords.isRecord(previousBest = null, candidate = set(100.0, 5)))
        val history = PersonalRecords.history(listOf(session(monday, "bench" to listOf(set(100.0, 5)))))
        assertEquals(1, history.size)
        assertFalse(history.single().isImprovement)
        assertEquals(0, PersonalRecords.improvementCount(listOf(session(monday, "bench" to listOf(set(100.0, 5))))))
    }

    @Test
    fun heavierWeight_isARecord_andReportsKgGain() {
        val sessions = listOf(
            session(monday, "bench" to listOf(set(100.0, 5))),
            session(monday.plusDays(3), "bench" to listOf(set(105.0, 3))),
        )
        val latest = PersonalRecords.current(sessions).single()
        assertTrue(latest.isImprovement)
        assertEquals(5.0, latest.weightGainKg!!, 1e-9)
        assertNull(latest.repGain)
    }

    @Test
    fun moreRepsAtSameWeight_isARecord_andReportsRepGain() {
        val event = PersonalRecords.history(
            listOf(
                session(monday, "bench" to listOf(set(100.0, 5))),
                session(monday.plusDays(2), "bench" to listOf(set(100.0, 7))),
            ),
        ).last()
        assertTrue(event.isImprovement)
        assertEquals(2, event.repGain)
        assertNull(event.weightGainKg)
    }

    @Test
    fun lighterWeightForMoreReps_isNotARecord() {
        assertFalse(PersonalRecords.isRecord(set(100.0, 5), set(90.0, 12)))
    }

    @Test
    fun bodyweightReps_canSetARecord() {
        val sessions = listOf(
            session(monday, "pullup" to listOf(set(0.0, 8))),
            session(monday.plusDays(2), "pullup" to listOf(set(0.0, 10))),
        )
        assertEquals(1, PersonalRecords.improvementCount(sessions))
        assertTrue(PersonalRecords.current(sessions).single().isBodyweight)
    }

    @Test
    fun uncompletedSets_doNotCount() {
        val prev = mapOf("bench" to set(100.0, 5))
        val s = session(monday, "bench" to listOf(set(140.0, 5, done = false)))
        assertTrue(PersonalRecords.recordsIn(s, prev).isEmpty())
    }

    // --- streaks ---

    @Test
    fun scheduledStreak_breaksOnAMissedPlannedDay() {
        val plan = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        val dates = setOf(monday.minusDays(7), monday.minusDays(5), monday.minusDays(3)) // Mon, Wed, Fri last week
        // Today is Thursday; Wednesday (yesterday) was planned and skipped.
        val streak = StreakCalculator.compute(dates + monday, plan, today = monday.plusDays(3))
        assertEquals(StreakUnit.SCHEDULED_DAYS, streak.unit)
        assertEquals(0, streak.count)
    }

    @Test
    fun scheduledStreak_ignoresUnplannedDays_andTodayIsNotAMiss() {
        val plan = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        val dates = setOf(monday.minusDays(7), monday.minusDays(5), monday) // Mon, Wed, Mon
        val streak = StreakCalculator.compute(dates, plan, today = monday.plusDays(2)) // Wednesday, not yet trained
        assertEquals(3, streak.count)
    }

    @Test
    fun withoutSchedule_streakCountsWeeks_andAnEmptyWeekBreaksIt() {
        val dates = setOf(monday.minusWeeks(3), monday.minusWeeks(1), monday)
        val streak = StreakCalculator.compute(dates, emptySet(), today = monday.plusDays(1))
        assertEquals(StreakUnit.WEEKS, streak.unit)
        assertEquals(2, streak.count) // this week and last week; week -2 was empty
    }

    @Test
    fun withoutSchedule_currentWeekWithoutSessionYet_keepsLastWeeksStreak() {
        val dates = setOf(monday.minusWeeks(2), monday.minusWeeks(1))
        assertEquals(2, StreakCalculator.compute(dates, emptySet(), today = monday).count)
    }

    @Test
    fun sparseTrainingOverMonths_isNotASevenDayStreak() {
        // Regression: seven sessions a month apart used to read as a 7-day streak.
        val dates = (0 until 7).map { monday.minusMonths(it.toLong()) }.toSet()
        val totals = TrainingTotals.from(
            dates.map { session(it, "squat" to listOf(set(60.0, 5))) },
            scheduledDays = emptySet(),
            today = monday,
        )
        assertFalse(Achievements.progress(totals).first { it.id == AchievementId.STREAK_7 }.unlocked)
        assertEquals(1, totals.weekStreak)
    }

    // --- achievements ---

    @Test
    fun emptySession_earnsNothing() {
        val totals = TrainingTotals.from(listOf(session(monday, "bench" to listOf(set(100.0, 5, done = false)))), emptySet(), monday)
        assertTrue(Achievements.newlyEarned(totals, emptySet()).isEmpty())
    }

    @Test
    fun firstWorkout_unlocksFirstWorkoutOnly_notFirstPr() {
        val totals = TrainingTotals.from(listOf(session(monday, "bench" to listOf(set(100.0, 5)))), emptySet(), monday)
        assertEquals(listOf(AchievementId.FIRST_WORKOUT), Achievements.newlyEarned(totals, emptySet()))
    }

    @Test
    fun alreadyUnlocked_isNotReturnedAgain() {
        val totals = TrainingTotals.from(listOf(session(monday, "bench" to listOf(set(100.0, 5)))), emptySet(), monday)
        assertTrue(Achievements.newlyEarned(totals, setOf(AchievementId.FIRST_WORKOUT)).isEmpty())
    }

    @Test
    fun volumeAndReps_areSummedFromCompletedSetsOnly() {
        val totals = TrainingTotals.from(
            listOf(session(monday, "dl" to listOf(set(200.0, 5), set(200.0, 5), set(500.0, 5, done = false)))),
            emptySet(),
            monday,
        )
        assertEquals(2_000.0, totals.volumeKg, 1e-9)
        assertEquals(10L, totals.reps)
    }
}
