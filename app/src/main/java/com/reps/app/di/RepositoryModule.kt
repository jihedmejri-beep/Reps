package com.reps.app.di

import com.reps.app.data.exercise.CatalogExerciseRepository
import com.reps.app.data.exercise.CatalogMuscleSvgRepository
import com.reps.app.data.ai.RepsAiNutritionAssistantRepository
import com.reps.app.data.user.LocalAuthRepository
import com.reps.app.data.user.LocalMealRepository
import com.reps.app.data.user.LocalUserRepository
import com.reps.app.data.user.LocalWeightRepository
import com.reps.app.data.user.LocalWorkoutRepository
import com.reps.app.data.user.RoomAssistantConversationRepository
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
 * Repository bindings.
 *
 * User data (accounts, profile, workouts, weight, meals, assistant history) is
 * backed by the local Room database through the `data.user` implementations.
 * The exercise catalogue is real too, shipped in the APK and read through its
 * own Room database - it was never user data, so it has no account scoping.
 *
 * The one remaining fake is the assistant's AI brain: it answers locally so
 * the chat runs without a Groq key or any deployed agent. To go live,
 * implement [NutritionAssistantRepository] against the agent of your choice
 * and swap only that binding below - nothing else changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: LocalAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(impl: LocalUserRepository): UserRepository

    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: CatalogExerciseRepository): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindMuscleSvgRepository(impl: CatalogMuscleSvgRepository): MuscleSvgRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: LocalWorkoutRepository): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindWeightRepository(impl: LocalWeightRepository): WeightRepository

    @Binds
    @Singleton
    abstract fun bindMealRepository(impl: LocalMealRepository): MealRepository

    /**
     * The assistant's AI brain connected to the REPS AI backend.
     * Swapping in your own agent is a change to this binding alone; history
     * storage is separate and already durable.
     */
    @Binds
    @Singleton
    abstract fun bindNutritionAssistantRepository(
        impl: RepsAiNutritionAssistantRepository,
    ): NutritionAssistantRepository

    /** Where past chats are kept: Room-backed, scoped to the signed-in account. */
    @Binds
    @Singleton
    abstract fun bindAssistantConversationRepository(
        impl: RoomAssistantConversationRepository,
    ): AssistantConversationRepository
}
