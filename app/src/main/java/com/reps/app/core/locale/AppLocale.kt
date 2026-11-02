package com.reps.app.core.locale

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.reps.app.domain.model.AppLanguage
import java.util.Locale

/**
 * The app's own UI language, independent of the device's.
 *
 * Android 13+ owns per-app languages natively ([LocaleManager]): the system
 * stores the choice, lists it under the app in Settings and re-localises every
 * context, so this only forwards to it. Below 13 there is no such service, so the
 * choice is kept in a small SharedPreferences file - read synchronously, because
 * it is needed in `attachBaseContext` before anything else can run - and applied
 * by wrapping the Activity and Application contexts.
 *
 * No override means "follow the device", which is also what a fresh install does.
 */
object AppLocale {

    private const val PREFS = "reps_locale"
    private const val KEY_TAG = "tag"

    private val supportedTags = AppLanguage.entries.map { it.tag }.toSet()

    /** Wraps [base] in the chosen language below Android 13; a no-op otherwise. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = storedTag(base) ?: return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(config)
    }

    /**
     * Switches the UI to [language]. On 13+ the system re-localises the running
     * Activity through a configuration change; below that the Activity is
     * recreated so [wrap] runs again with the new choice.
     */
    fun apply(context: Context, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(language.tag)
            return
        }
        if (storedTag(context) == language.tag) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_TAG, language.tag).apply()
        context.findActivity()?.recreate()
    }

    /** The language the UI is actually rendering in right now: the override, else the device's, else English. */
    fun effective(context: Context): AppLanguage {
        val override = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .takeIf { !it.isEmpty }?.get(0)?.language
        } else {
            storedTag(context)
        }
        val tag = override ?: context.resources.configuration.locales[0].language
        return if (tag in supportedTags) AppLanguage.fromTag(tag) else AppLanguage.ENGLISH
    }

    private fun storedTag(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TAG, null)
            ?.takeIf { it in supportedTags }

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}
