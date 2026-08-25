package com.reps.app.di

import com.reps.app.data.assistant.InMemoryAssistantConversationRepository
import com.reps.app.data.exercise.CatalogExerciseRepository
import com.reps.app.data.exercise.CatalogMuscleSvgRepository
import com.reps.app.data.fake.FakeAuthRepository
import com.reps.app.data.fake.FakeMealRepository
import com.reps.app.data.fake.FakeNutritionAssistantRepository
import com.reps.app.data.fake.FakeUserRepository
import com.reps.app.data.fake.FakeWeightRepository
import com.reps.app.data.fake.FakeWorkoutRepository
import com.reps.app.domain.repository.AssistantConversationRepository
import com.reps.app.domain.repository.AuthRepository
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.MuscleSvgRepository
import com.reps.app.domain.repository.NutritionAssistantRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The user's own data (workouts, weight, meals) is still bound to in-memory
 * fakes while the backend is built; pointing those at Firebase is a matter of
 * swapping the right-hand side of each binding - no ViewModel or screen changes.
 *
 * The exercise catalogue is the exception: it is real, shipped in the APK and
 * read through Room. It was never user data, so it had no reason to wait for a
 * backend.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FakeAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: FakeUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: CatalogExerciseRepository): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindMuscleSvgRepository(impl: CatalogMuscleSvgRepository): MuscleSvgRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: FakeWorkoutRepository): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindWeightRepository(impl: FakeWeightRepository): WeightRepository

    @Binds
    @Singleton
    abstract fun bindMealRepository(impl: FakeMealRepository): MealRepository

    /**
     * The assistant's AI brain. The fake answers locally so the screen runs
     * today without a Groq key, a USDA key, or any deployed functions. To go
     * live: deploy /functions, implement [NutritionAssistantRepository] on top
     * of its callables, and swap the right-hand side here - nothing else
     * changes.
     */
    @Binds
    @Singleton
    abstract fun bindNutritionAssistantRepository(
        impl: FakeNutritionAssistantRepository,
    ): NutritionAssistantRepository

    /**
     * Where past chats are kept. In-memory for now; binding a durable store
     * (Room, Firestore) later is a one-line swap here.
     */
    @Binds
    @Singleton
    abstract fun bindAssistantConversationRepository(
        impl: InMemoryAssistantConversationRepository,
    ): AssistantConversationRepository
}
