package com.reps.app.core.content

import com.reps.app.domain.model.ExerciseSet
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutExercise
import com.reps.app.domain.model.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DailyRemarkTest {

    private val today = LocalDate.of(2026, 11, 4) // Wednesday

    private fun session(date: LocalDate, name: String = "Push", done: Boolean = true, prs: List<String> = emptyList()) =
        WorkoutSession(
            id = "$date-$name",
            name = name,
            date = date,
            prsHit = prs,
            exercises = listOf(WorkoutExercise("bench", 0, listOf(ExerciseSet(weightKg = 60.0, reps = 5, completed = done)))),
        )

    private fun pick(sessions: List<WorkoutSession>, templates: List<Workout> = emptyList()) =
        DailyRemark.pick(sessions, templates, today) { id -> if (id == "bench") "Bench Press" else null }

    @Test
    fun noHistory_invitesTheFirstWorkout() = assertEquals(Remark.FirstWorkout, pick(emptyList()))

    @Test
    fun emptySessionsDoNotCountAsHistory() =
        assertEquals(Remark.FirstWorkout, pick(listOf(session(today, done = false))))

    @Test
    fun aRecordToday_isNamed() =
        assertEquals(Remark.NewBest("Bench Press"), pick(listOf(session(today.minusDays(3)), session(today, prs = listOf("bench")))))

    @Test
    fun aSessionToday_isAcknowledged() =
        assertEquals(Remark.LoggedToday("Pull"), pick(listOf(session(today, name = "Pull"))))

    @Test
    fun aPlannedDay_pointsAtThePlan() {
        val plan = Workout(id = "t", name = "Legs", scheduledDays = setOf(DayOfWeek.WEDNESDAY))
        assertEquals(Remark.PlannedToday("Legs"), pick(listOf(session(today.minusDays(1))), listOf(plan)))
    }

    @Test
    fun aLongGap_getsAWayBack_notBlame() =
        assertEquals(Remark.WelcomeBack(9), pick(listOf(session(today.minusDays(9)))))

    @Test
    fun moreThanLastWeek_saysSo() {
        val sessions = listOf(session(today.minusDays(8)), session(today.minusDays(2)), session(today.minusDays(1)))
        assertEquals(Remark.AheadOfLastWeek(thisWeek = 2, lastWeek = 1), pick(sessions))
    }

    @Test
    fun twoWeeksInARow_isAStreakRemark() {
        // Yesterday and the same weekday last week: level with last week, two weeks running.
        assertEquals(Remark.WeekStreak(2), pick(listOf(session(today.minusDays(1)), session(today.minusDays(7)))))
    }

    @Test
    fun nothingTrueToSay_meansNoRemark() {
        // Last Sunday only: no gap yet, nothing this week to compare, a one-week run.
        assertNull(pick(listOf(session(today.minusDays(3)))))
    }
}
