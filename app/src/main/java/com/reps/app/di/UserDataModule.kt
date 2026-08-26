package com.reps.app.di

import android.content.Context
import androidx.room.Room
import com.reps.app.data.user.db.UserDataDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The writable user database and its DAOs.
 *
 * Unlike the bundled catalogue there is no asset to copy from and no
 * destructive fallback: this data is the user's, so a future version bump must
 * ship a real migration rather than drop the tables.
 */
@Module
@InstallIn(SingletonComponent::class)
object UserDataModule {

    @Provides
    @Singleton
    fun provideUserDataDatabase(
        @ApplicationContext context: Context,
    ): UserDataDatabase = Room
        .databaseBuilder(
            context,
            UserDataDatabase::class.java,
            UserDataDatabase.DB_NAME,
        )
        .build()

    @Provides
    fun provideUserAccountDao(database: UserDataDatabase) = database.accountDao()

    @Provides
    fun provideUserProfileDao(database: UserDataDatabase) = database.userProfileDao()

    @Provides
    fun provideWorkoutDao(database: UserDataDatabase) = database.workoutDao()

    @Provides
    fun provideWeightEntryDao(database: UserDataDatabase) = database.weightEntryDao()

    @Provides
    fun provideMealDao(database: UserDataDatabase) = database.mealDao()

    @Provides
    fun provideAssistantConversationDao(database: UserDataDatabase) =
        database.assistantConversationDao()
}
