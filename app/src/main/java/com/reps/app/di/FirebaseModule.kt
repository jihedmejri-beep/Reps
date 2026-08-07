package com.reps.app.di

import com.google.firebase.functions.FirebaseFunctions
import com.reps.app.core.constants.AppConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    /**
     * Pinned to the region the functions are deployed in. Left to the default,
     * the SDK targets us-central1 anyway, but naming it here means moving the
     * backend is a one-line change rather than a silent 404.
     */
    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(AppConstants.Functions.REGION)
}
