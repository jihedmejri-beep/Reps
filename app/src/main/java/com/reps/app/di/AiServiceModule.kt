package com.reps.app.di

import com.reps.app.ai.RepsAiApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiServiceModule {
    @Provides
    @Singleton
    fun provideRepsAiApiService(): RepsAiApiService = RepsAiApiService()
}