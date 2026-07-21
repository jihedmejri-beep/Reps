package com.reps.app.core.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.reps.app.core.theme.RepsOutline
import com.reps.app.core.theme.RepsSurfaceElevated
import com.reps.app.core.theme.RepsTextPrimary
import com.reps.app.core.theme.RepsTheme

/**
 * The app's one modal-sheet surface: add weight, add meal, edit a profile field,
 * pick from a list. Ports the prototype's `sheets.js`, which every one of those
 * flows opens through rather than each screen rolling its own dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepsBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = RepsSurfaceElevated,
        contentColor = RepsTextPrimary,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(color = RepsOutline) },
    ) {
        Column(
            Modifier
                .padding(horizontal = RepsTheme.dimens.screenPadding)
                .padding(bottom = 28.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.headlineSmall,
                    color = RepsTextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            content()
        }
    }
}
