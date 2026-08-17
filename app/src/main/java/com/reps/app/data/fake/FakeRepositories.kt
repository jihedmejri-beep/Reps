package com.reps.app.data.fake

import com.reps.app.domain.model.Meal
import com.reps.app.domain.model.User
import com.reps.app.domain.model.WeightEntry
import com.reps.app.domain.model.Workout
import com.reps.app.domain.model.WorkoutSession
import com.reps.app.domain.repository.AuthRepository
import com.reps.app.domain.repository.AuthResult
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import com.reps.app.domain.repository.WorkoutRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory stand-ins for the Firestore repositories, so the UI can be built
 * and clicked through before the backend is wired.
 *
 * They hold real MutableStateFlow state rather than returning constants, which
 * means writes are observable and the screens exercise the same reactive paths
 * they will use against Firestore. Swapping them out is a change to
 * RepositoryModule only.
 */

@Singleton
class FakeAuthRepository @Inject constructor() : AuthRepository {

    private val uid = MutableStateFlow<String?>(SampleData.user.uid)

    override fun observeAuthState(): Flow<String?> = uid.asStateFlow()
    override val currentUid: String? get() = uid.value

    override suspend fun signIn(email: String, password: String): AuthResult {
        delay(600) // stand in for network latency, so loading states are visible
        uid.value = SampleData.user.uid
        return AuthResult.Success
    }

    override suspend fun signUp(name: String, email: String, password: String): AuthResult {
        delay(600)
        uid.value = SampleData.user.uid
        return AuthResult.Success
    }

    override suspend fun signInWithGoogle(idToken: String): AuthResult {
        delay(600)
        uid.value = SampleData.user.uid
        return AuthResult.Success
    }

    override suspend fun sendPasswordReset(email: String): AuthResult {
        delay(400)
        return AuthResult.Success
    }

    override suspend fun signOut() {
        uid.value = null
    }
}

@Singleton
class FakeUserRepository @Inject constructor() : UserRepository {

    private val user = MutableStateFlow<User?>(SampleData.user)

    override fun observeUser(): Flow<User?> = user.asStateFlow()

    override suspend fun updateUser(user: User) {
        this.user.value = user
    }
}

@Singleton
class FakeWorkoutRepository @Inject constructor() : WorkoutRepository {

    private val templates = MutableStateFlow(SampleData.workouts)
    private val sessions = MutableStateFlow(SampleData.workoutSessions)

    override fun observeTemplates(): Flow<List<Workout>> = templates.asStateFlow()

    override fun observeTemplate(workoutId: String): Flow<Workout?> =
        templates.map { list -> list.firstOrNull { it.id == workoutId } }

    override fun observeWorkoutFor(date: LocalDate): Flow<Workout?> =
        templates.map { list -> list.firstOrNull { date.dayOfWeek in it.scheduledDays } }

    override suspend fun saveTemplate(workout: Workout) {
        templates.update { list ->
            val index = list.indexOfFirst { it.id == workout.id }
            if (index >= 0) list.toMutableList().apply { this[index] = workout }
            else list + workout
        }
    }

    override suspend fun deleteTemplate(workoutId: String) {
        templates.update { list -> list.filterNot { it.id == workoutId } }
    }

    override fun observeSessions(): Flow<List<WorkoutSession>> = sessions.asStateFlow()

    override suspend fun saveSession(session: WorkoutSession) {
        sessions.update { it + session }
    }

    override suspend fun bestVolumeFor(exerciseId: String): Double? =
        sessions.value
            .flatMap { it.exercises }
            .filter { it.exerciseId == exerciseId }
            .flatMap { it.sets }
            .filter { it.completed }
            .maxOfOrNull { it.volume }
}

@Singleton
class FakeWeightRepository @Inject constructor() : WeightRepository {

    private val entries = MutableStateFlow(SampleData.weightEntries)

    override fun observeEntries(): Flow<List<WeightEntry>> = entries.asStateFlow()

    override suspend fun logWeight(entry: WeightEntry) {
        entries.update { list ->
            // One entry per day: logging again replaces that day rather than
            // stacking two points on the same bar.
            val withoutSameDay = list.filterNot { it.date == entry.date }
            (withoutSameDay + entry).sortedBy { it.date }
        }
    }

    override suspend fun deleteEntry(entryId: String) {
        entries.update { list -> list.filterNot { it.id == entryId } }
    }
}

@Singleton
class FakeMealRepository @Inject constructor() : MealRepository {

    private val meals = MutableStateFlow(SampleData.meals)

    override fun observeMeals(date: LocalDate): Flow<List<Meal>> =
        meals.map { list -> list.filter { it.date == date } }

    override suspend fun logMeal(meal: Meal) {
        meals.update { list ->
            val index = list.indexOfFirst { it.id == meal.id }
            if (index >= 0) list.toMutableList().apply { this[index] = meal }
            else list + meal
        }
    }

    override suspend fun deleteMeal(mealId: String) {
        meals.update { list -> list.filterNot { it.id == mealId } }
    }
}
