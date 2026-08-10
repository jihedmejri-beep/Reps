package com.reps.app.data.exercise

/**
 * Turns a catalogue image row into the URL the app should actually fetch.
 *
 * Each row carries two independent references to the same picture:
 *
 *  - `asset_path` - the pipeline's CDN-relative key, e.g.
 *    `assets/exercises/1000/main.png`. This is the intended long-term home:
 *    `db/generate_upload_manifest.py` produces an upload manifest for the whole
 *    208 MB asset tree, and `docs/deployment.md` ends with an `aws s3 sync`
 *    step. That bucket does not exist yet, so the key resolves to nothing.
 *  - `remote_url` - the live upstream URL the pipeline downloaded from, e.g.
 *    `https://wger.de/media/exercise-images/1000/<uuid>.png`. This works today.
 *
 * So the resolver prefers [assetBaseUrl] when one is configured and falls back
 * to the upstream URL otherwise. Pointing REPS at its own CDN later is a
 * one-line change here plus the bucket sync - no data migration, because both
 * references are already stored for all 336 images.
 *
 * Neither reference is ever rewritten; this only chooses between them.
 */
class MediaUrlResolver(
    private val assetBaseUrl: String = DEFAULT_ASSET_BASE_URL,
) {

    /**
     * The URL for a full-size demonstration image, or empty when the catalogue
     * has no picture for that exercise - which is the case for 564 of the 828.
     */
    fun resolveFull(assetPath: String?, remoteUrl: String?): String =
        fromAssetTree(assetPath) ?: remoteUrl.orEmpty()

    /**
     * A smaller render for list rows. Falls back to the full image rather than
     * to nothing: 5 images have no thumbnail variant upstream.
     *
     * When a CDN is configured there is only one rendition per asset key, so a
     * thumbnail request resolves to the same file as the full image.
     */
    fun resolveThumbnail(
        assetPath: String?,
        thumbnailUrl: String?,
        remoteUrl: String?,
    ): String = fromAssetTree(assetPath)
        ?: thumbnailUrl?.takeIf { it.isNotBlank() }
        ?: remoteUrl.orEmpty()

    private fun fromAssetTree(assetPath: String?): String? {
        if (assetBaseUrl.isBlank() || assetPath.isNullOrBlank()) return null
        return assetBaseUrl.trimEnd('/') + "/" + assetPath.trimStart('/')
    }

    companion object {
        /**
         * Base URL for the pipeline's `assets/` tree. Blank on purpose: no
         * bucket has been provisioned yet, so the app uses each image's upstream
         * URL. Set this once the asset sync in `docs/deployment.md` has run.
         */
        const val DEFAULT_ASSET_BASE_URL = ""
    }
}
