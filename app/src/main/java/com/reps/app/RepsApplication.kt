package com.reps.app

import android.app.Application
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
import dagger.hilt.android.HiltAndroidApp
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class RepsApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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
