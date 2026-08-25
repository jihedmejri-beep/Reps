package com.reps.app.feature.nutrition.assistant

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reps.app.R
import com.reps.app.core.components.RepsBackButton
import com.reps.app.core.theme.PillShape
import com.reps.app.core.theme.RepsGreen
import com.reps.app.core.theme.RepsOnGreen
import com.reps.app.core.theme.RepsTheme

/**
 * Alpha the brand green is knocked back to when it is a surface rather than a
 * fill. Low enough that near-black text stays readable over it on the light
 * paper background and off-white stays readable on the dark one, so a single
 * value works in both themes.
 */
private const val GreenTintAlpha = 0.16f

/** Bubble corner radius, with the corner nearest the sender pulled in as a tail. */
private val BubbleRadius = 18.dp
private val BubbleTailRadius = 6.dp

/** Gutter left on the far side of a bubble so nothing runs edge to edge. */
private val BubbleGutter = 40.dp

/**
 * The assistant's mark: a sparkle in the brand green. Used [solid] in the top
 * bar and on the button that opens the screen, tinted beside each reply.
 */
@Composable
fun AssistantMark(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
    solid: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .background(if (solid) RepsGreen else RepsGreen.copy(alpha = GreenTintAlpha), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = if (solid) RepsOnGreen else RepsGreen,
            modifier = Modifier.size(size * 0.52f),
        )
    }
}

/**
 * Back, the assistant's name, and the overflow holding History and New Chat.
 * Deliberately not a Material `TopAppBar`: the rest of the app builds its
 * headers from a plain row and [RepsBackButton], and matching that is what keeps
 * this screen looking like part of REPS.
 */
@Composable
fun AssistantTopBar(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RepsTheme.dimens.screenPadding, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepsBackButton(onClick = onBack)
            AssistantMark(modifier = Modifier.padding(start = 12.dp), size = 30.dp)
            Text(
                text = stringResource(R.string.assistant_title),
                style = MaterialTheme.typography.titleMedium,
                color = RepsTheme.colors.textPrimary,
                modifier = Modifier.weight(1f).padding(start = 10.dp),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.assistant_menu_cd),
                        tint = RepsTheme.colors.textPrimary,
                    )
                }
                // The menu's own colours are set explicitly: Material's default
                // container reads from a surface role the REPS scheme does not
                // define, which would drop a stock M3 grey into both themes.
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = MaterialTheme.shapes.medium,
                    containerColor = RepsTheme.colors.surfaceElevated,
                    border = BorderStroke(1.dp, RepsTheme.colors.outline),
                ) {
                    MenuRow(R.string.assistant_history) {
                        menuOpen = false
                        onOpenHistory()
                    }
                    MenuRow(R.string.assistant_new_chat) {
                        menuOpen = false
                        onNewChat()
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RepsTheme.colors.outline))
    }
}

@Composable
private fun MenuRow(labelRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = RepsTheme.colors.textPrimary),
    )
}

/**
 * What the screen shows before the first message: the assistant's name, one
 * line of invitation, and prompts that can be tapped instead of typed.
 *
 * [onPrompt] receives the already-localised label, so a tapped suggestion
 * travels through the ViewModel exactly like typed text.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssistantWelcome(
    onPrompt: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            // Scrolls rather than clips: with the keyboard open on a short
            // screen there is not always room for the mark, the copy and four
            // suggestions at once.
            .verticalScroll(rememberScrollState())
            .padding(horizontal = RepsTheme.dimens.screenPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AssistantMark(size = 56.dp, solid = false)
        Text(
            text = stringResource(R.string.assistant_title),
            style = MaterialTheme.typography.headlineSmall,
            color = RepsTheme.colors.textPrimary,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = stringResource(R.string.assistant_welcome_body),
            style = MaterialTheme.typography.bodyLarge,
            color = RepsTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
        FlowRow(
            modifier = Modifier.padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistantSuggestions.forEach { suggestion ->
                val label = stringResource(suggestion.labelRes)
                SuggestionPill(
                    label = label,
                    onClick = { onPrompt(label) },
                )
            }
        }
    }
}

/**
 * A welcome prompt. Close cousin of `RepsChip`, but that one carries a
 * selected/unselected state this has no use for - a suggestion is tapped once
 * and gone.
 */
@Composable
private fun SuggestionPill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .background(RepsTheme.colors.surface, PillShape)
            .border(1.dp, RepsTheme.colors.outline, PillShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RepsTheme.colors.textPrimary,
        )
    }
}

/** One line of the transcript, sided and coloured by who wrote it. */
@Composable
fun MessageRow(message: ChatMessage, modifier: Modifier = Modifier) {
    val fromUser = message.author == ChatAuthor.USER

    Row(
        modifier.fillMaxWidth(),
        // The bubble takes a weight with fill = false, so it shrinks to its text
        // and leaves slack in the row - the arrangement is what decides which
        // side that slack falls on, and so which side the bubble sits against.
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (fromUser) {
            Spacer(Modifier.width(BubbleGutter))
        } else {
            AssistantMark(size = 26.dp, solid = false)
            Spacer(Modifier.width(8.dp))
        }

        Bubble(
            fromUser = fromUser,
            // fill = false lets the bubble shrink to its text; the weight only
            // caps how wide a long message may grow.
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text = message.text(),
                style = MaterialTheme.typography.bodyLarge,
                color = RepsTheme.colors.textPrimary,
            )
        }

        if (!fromUser) Spacer(Modifier.width(BubbleGutter))
    }
}

/** The three dots shown while the reply is pending. */
@Composable
fun TypingRow(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.assistant_typing_cd)
    val transition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier
            .fillMaxWidth()
            // One announcement for the row, not three animating dots.
            .clearAndSetSemantics {
                contentDescription = label
                liveRegion = LiveRegionMode.Polite
            },
        verticalAlignment = Alignment.Top,
    ) {
        AssistantMark(size = 26.dp, solid = false)
        Spacer(Modifier.width(8.dp))
        Bubble(fromUser = false) {
            Row(
                Modifier.height(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                repeat(3) { index ->
                    val dotAlpha by transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(520, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse,
                            // Staggered so the dots ripple rather than blink together.
                            initialStartOffset = StartOffset(index * 150),
                        ),
                        label = "typingDot",
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .alpha(dotAlpha)
                            .background(RepsTheme.colors.textSecondary, CircleShape),
                    )
                }
            }
        }
        Spacer(Modifier.width(BubbleGutter))
    }
}

@Composable
private fun Bubble(
    fromUser: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // Start/end rather than left/right, so the tail stays on the sender's side
    // when the app runs in Arabic.
    val shape = RoundedCornerShape(
        topStart = BubbleRadius,
        topEnd = BubbleRadius,
        bottomStart = if (fromUser) BubbleRadius else BubbleTailRadius,
        bottomEnd = if (fromUser) BubbleTailRadius else BubbleRadius,
    )
    Box(
        modifier
            .background(
                if (fromUser) RepsGreen.copy(alpha = GreenTintAlpha) else RepsTheme.colors.surface,
                shape,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content()
    }
}

/**
 * The chat input. Sits at the bottom of the screen and is kept clear of the
 * keyboard by the screen's `imePadding`.
 */
@Composable
fun AssistantComposer(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    modifier: Modifier = Modifier,
) {
    val hint = stringResource(R.string.assistant_composer_hint)

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(RepsTheme.colors.outline))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = RepsTheme.dimens.screenPadding, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .background(RepsTheme.colors.surface, MaterialTheme.shapes.extraLarge)
                    .border(1.dp, RepsTheme.colors.outline, MaterialTheme.shapes.extraLarge)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = RepsTheme.colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(RepsGreen),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Default,
                    ),
                    // Grows with the text, then scrolls inside itself. Capped by
                    // line count rather than a dp height so a large system font
                    // scale makes the box taller instead of clipping the text.
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { field ->
                        if (value.isEmpty()) {
                            Text(
                                text = hint,
                                style = MaterialTheme.typography.bodyLarge,
                                color = RepsTheme.colors.textTertiary,
                            )
                        }
                        field()
                    },
                )
            }
            SendButton(enabled = canSend, onClick = onSend)
        }
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        targetValue = if (enabled) RepsGreen else RepsTheme.colors.surface,
        animationSpec = tween(180),
        label = "sendContainer",
    )
    val content by animateColorAsState(
        targetValue = if (enabled) RepsOnGreen else RepsTheme.colors.textTertiary,
        animationSpec = tween(180),
        label = "sendContent",
    )

    Box(
        Modifier
            .size(48.dp)
            .background(container, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Send,
            contentDescription = stringResource(R.string.assistant_send_cd),
            tint = content,
            modifier = Modifier.size(20.dp),
        )
    }
}
