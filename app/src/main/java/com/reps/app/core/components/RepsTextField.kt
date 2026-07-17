package com.reps.app.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.theme.RepsError
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOutline
import com.reps.app.core.theme.RepsSurface
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTextSecondary
import com.reps.app.core.theme.RepsTextTertiary
import com.reps.app.core.theme.RepsTheme

/**
 * Text input with an optional uppercase label above it, matching the EMAIL /
 * PASSWORD treatment in the auth mockups.
 */
@Composable
fun RepsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: Painter? = null,
    error: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    Column(modifier) {
        label?.let {
            Text(
                text = it.uppercase(),
                style = RepsTheme.textStyles.eyebrow,
                color = RepsTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = singleLine,
            isError = error != null,
            placeholder = placeholder?.let {
                { Text(it, style = MaterialTheme.typography.bodyLarge, color = RepsTextTertiary) }
            },
            leadingIcon = leadingIcon?.let {
                {
                    Icon(
                        painter = it,
                        contentDescription = null,
                        tint = RepsTextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            colors = repsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = RepsError,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Password variant with the visibility toggle the mockups call for. */
@Composable
fun RepsPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    imeAction: ImeAction = ImeAction.Done,
) {
    var visible by remember { mutableStateOf(false) }

    Column(modifier) {
        label?.let {
            Text(
                text = it.uppercase(),
                style = RepsTheme.textStyles.eyebrow,
                color = RepsTextSecondary,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            isError = error != null,
            placeholder = placeholder?.let {
                { Text(it, style = MaterialTheme.typography.bodyLarge, color = RepsTextTertiary) }
            },
            visualTransformation = if (visible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        imageVector = if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = stringResource(
                            if (visible) R.string.auth_hide_password else R.string.auth_show_password,
                        ),
                        tint = RepsTextTertiary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = imeAction,
            ),
            colors = repsFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = RepsError,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun repsFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = RepsTextPrimary,
    unfocusedTextColor = RepsTextPrimary,
    focusedContainerColor = RepsSurface,
    unfocusedContainerColor = RepsSurface,
    disabledContainerColor = RepsSurface,
    errorContainerColor = RepsSurface,
    cursorColor = RepsGreen,
    focusedBorderColor = RepsGreen,
    unfocusedBorderColor = RepsOutline,
    errorBorderColor = RepsError,
)
