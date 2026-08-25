package com.reps.app.data.exercise.db

import java.text.Normalizer
import java.util.Locale

/**
 * Folds a string into the form the catalogue's `*_folded` columns are stored in,
 * so a `LIKE` comparison is case-, accent- and orthography-insensitive.
 *
 * The source export ships an FTS5 index instead, but FTS5 here can only match
 * whole tokens and keeps accents (the dataset's own
 * `docs/android-integration.md` calls that out as a known limitation). Folding
 * both sides and using `LIKE` gives substring matching that works for all three
 * shipped languages, over a table of 2,484 short rows where the cost is
 * irrelevant.
 *
 * **This must stay byte-for-byte equivalent to `fold_text()` in
 * `tools/build_exercise_db.py`.** The converter writes the stored side with it;
 * if the two drift, search silently stops matching. `TextFoldingTest` pins the
 * cases that matter.
 */
object TextFolding {

    /** Arabic orthographic variants that users routinely type interchangeably. */
    private val ARABIC_EQUIVALENTS = mapOf(
        'أ' to 'ا', // أ -> ا
        'إ' to 'ا', // إ -> ا
        'آ' to 'ا', // آ -> ا
        'ٱ' to 'ا', // ٱ -> ا
        'ى' to 'ي', // ى -> ي
        'ئ' to 'ي', // ئ -> ي
        'ة' to 'ه', // ة -> ه
        'ؤ' to 'و', // ؤ -> و
    )

    /** Arabic tatweel: a purely decorative letter-stretcher, never meaningful. */
    private const val TATWEEL = 'ـ'

    fun fold(input: String): String {
        if (input.isEmpty()) return ""
        // NFD splits "é" into "e" + U+0301 so the mark can be dropped on its own.
        // Arabic tashkeel are already standalone combining marks and fall out here too.
        val decomposed = Normalizer.normalize(input.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        val builder = StringBuilder(decomposed.length)
        var lastWasSpace = false
        for (char in decomposed) {
            when {
                Character.getType(char) == Character.NON_SPACING_MARK.toInt() -> Unit
                char == TATWEEL -> Unit
                char.isWhitespace() -> {
                    // Collapse runs so "bench   press" and "bench press" fold alike.
                    if (!lastWasSpace && builder.isNotEmpty()) builder.append(' ')
                    lastWasSpace = true
                }
                else -> {
                    builder.append(ARABIC_EQUIVALENTS[char] ?: char)
                    lastWasSpace = false
                }
            }
        }
        return builder.toString().trimEnd()
    }
}
