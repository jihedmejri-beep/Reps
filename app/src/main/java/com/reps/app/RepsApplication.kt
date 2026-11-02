package com.reps.app

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.reps.app.core.locale.AppLocale
import com.reps.app.data.datastore.UserPreferencesDataStore
import com.reps.app.data.notifications.HydrationReminderScheduler
import com.reps.app.data.notifications.NotificationHelper
import com.reps.app.data.notifications.WorkoutReminderScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class RepsApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    @Inject
    lateinit var hiltWorkerFactory: HiltWorkerFactory

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var preferences: UserPreferencesDataStore

    @Inject
    lateinit var workoutReminders: WorkoutReminderScheduler

    @Inject
    lateinit var hydrationReminders: HydrationReminderScheduler

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Notifications and workers read strings from this context; keep it in the app's language. */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper.ensureChannels()
        // Reminders default to on, but used to be scheduled only when the toggle
        // was flipped, so a fresh install never received one. KEEP leaves an
        // already-running schedule alone.
        appScope.launch {
            if (preferences.notificationsEnabled.first()) {
                workoutReminders.ensureScheduled()
                hydrationReminders.ensureScheduled()
            }
        }
    }

    /**
     * One loader for every image in the app, so call sites stay format-agnostic.
     *
     * Three decoders are registered because the catalogue genuinely serves three
     * kinds of file: PNG/JPEG/WebP demonstration photos (handled natively), a
     * handful of GIFs, and the muscle/body SVGs.
     *
     * The disk cache is what makes reopening an exercise free. Demonstration
     * images are deliberately not shipped in the APK - the catalogue's asset
     * tree is ~208 MB - so they are fetched once and then served locally. The
     * 128 MB ceiling is roughly the whole set of images the catalogue actually
     * references, and Coil evicts least-recently-used beyond it.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(SvgDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_FRACTION)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("exercise_media").toOkioPath())
                    .maxSizeBytes(MEDIA_DISK_CACHE_BYTES)
                    .build()
            }
            // A network image popping in is jarring next to the rest of the
            // app's motion; a short fade matches how everything else arrives.
            .crossfade(true)
            .build()

    private companion object {
        const val MEMORY_CACHE_FRACTION = 0.25
        const val MEDIA_DISK_CACHE_BYTES = 128L * 1024 * 1024
    }
}
