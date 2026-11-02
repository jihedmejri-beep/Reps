package com.reps.app.core.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.reps.app.core.util.NumberInput

/**
 * A [RepsTextField] for numbers that keeps exactly what the user types.
 *
 * Binding a text field straight to a parsed number re-formats it on every
 * keystroke: "2." snaps to "2" and an emptied field refills itself. Here the text
 * is local; [onValue] receives each parseable value, and the text only re-syncs
 * from [value] when that changes for some other reason.
 */
@Composable
fun NumericTextField(
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    decimal: Boolean = true,
) {
    var text by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (NumberInput.parseDecimal(text) != NumberInput.parseDecimal(value)) text = value
    }
    RepsTextField(
        value = text,
        onValueChange = {
            text = it
            onValue(it)
        },
        label = label,
        placeholder = placeholder,
        keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        modifier = modifier,
    )
}
