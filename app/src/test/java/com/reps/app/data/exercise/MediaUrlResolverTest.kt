package com.reps.app.data.exercise

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The catalogue stores two references to every image and exactly one of them
 * works at a time. These tests pin which one wins, because getting it wrong
 * means either every demonstration photo 404s (CDN key against no CDN) or the
 * app keeps hotlinking upstream after the bucket is live.
 */
class MediaUrlResolverTest {

    private val upstream = "https://wger.de/media/exercise-images/1000/abc.png"
    private val thumb = "https://wger.de/media/exercise-images/1000/abc.png.400x400_q85.jpg"
    private val assetPath = "assets/exercises/1000/main.png"

    @Test
    fun `with no CDN configured it serves the upstream url`() {
        val resolver = MediaUrlResolver(assetBaseUrl = "")
        assertEquals(upstream, resolver.resolveFull(assetPath, upstream))
    }

    @Test
    fun `with a CDN configured the asset key wins`() {
        val resolver = MediaUrlResolver(assetBaseUrl = "https://cdn.reps.app")
        assertEquals(
            "https://cdn.reps.app/assets/exercises/1000/main.png",
            resolver.resolveFull(assetPath, upstream),
        )
    }

    @Test
    fun `a trailing slash on the base does not double up`() {
        val resolver = MediaUrlResolver(assetBaseUrl = "https://cdn.reps.app/")
        assertEquals(
            "https://cdn.reps.app/assets/exercises/1000/main.png",
            resolver.resolveFull(assetPath, upstream),
        )
    }

    @Test
    fun `a CDN row with no asset key still falls back to upstream`() {
        val resolver = MediaUrlResolver(assetBaseUrl = "https://cdn.reps.app")
        assertEquals(upstream, resolver.resolveFull(assetPath = null, remoteUrl = upstream))
    }

    @Test
    fun `an exercise with no image at all resolves to blank not null`() {
        // 564 of the catalogue's 828 exercises are in this state; the UI keys
        // its empty placeholder off a blank string.
        val resolver = MediaUrlResolver(assetBaseUrl = "")
        assertEquals("", resolver.resolveFull(assetPath = null, remoteUrl = null))
        assertEquals("", resolver.resolveThumbnail(null, null, null))
    }

    @Test
    fun `thumbnails prefer the small rendition`() {
        val resolver = MediaUrlResolver(assetBaseUrl = "")
        assertEquals(thumb, resolver.resolveThumbnail(assetPath, thumb, upstream))
    }

    @Test
    fun `thumbnails fall back to the full image when there is no rendition`() {
        // 5 of the 336 images have no thumbnail variant upstream.
        val resolver = MediaUrlResolver(assetBaseUrl = "")
        assertEquals(upstream, resolver.resolveThumbnail(assetPath, null, upstream))
        assertEquals(upstream, resolver.resolveThumbnail(assetPath, "", upstream))
    }
}
