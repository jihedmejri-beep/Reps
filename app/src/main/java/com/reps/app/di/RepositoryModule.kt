package com.reps.app.di

import com.reps.app.data.fake.FakeAuthRepository
import com.reps.app.data.fake.FakeExerciseRepository
import com.reps.app.data.fake.FakeMealRepository
import com.reps.app.data.fake.FakeUserRepository
import com.reps.app.data.fake.FakeWeightRepository
import com.reps.app.data.fake.FakeWorkoutRepository
import com.reps.app.domain.repository.AuthRepository
import com.reps.app.domain.repository.ExerciseRepository
import com.reps.app.domain.repository.MealRepository
import com.reps.app.domain.repository.UserRepository
import com.reps.app.domain.repository.WeightRepository
import com.reps.app.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The frontend is being built ahead of the backend, so every repository is
 * bound to an in-memory fake. Pointing the app at Firebase is a matter of
 * swapping the right-hand side of each binding for its *RepositoryImpl - no
 * ViewModel or screen changes.
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
    abstract fun bindExerciseRepository(impl: FakeExerciseRepository): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: FakeWorkoutRepository): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindWeightRepository(impl: FakeWeightRepository): WeightRepository

    @Binds
    @Singleton
    abstract fun bindMealRepository(impl: FakeMealRepository): MealRepository
}
