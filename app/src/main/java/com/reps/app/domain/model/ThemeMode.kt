package com.reps.app.domain.model

/**
 * How the app decides between its light and dark palettes.
 *
 * [SYSTEM] is the default and follows the phone's own light/dark setting; the
 * Profile toggle overrides it with an explicit [LIGHT] or [DARK].
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
