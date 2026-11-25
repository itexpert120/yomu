package com.itexpert120.yomu.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.icons.StackIcons

/**
 * Stack's text field: the label above (13/600), a 52dp raised field with radius 16, the clear
 * button inside, and supporting/error text below (12; danger with [isError], which also rings the
 * field). Focus rings the field in ink. By default the IME action moves focus to the next field (or
 * closes the keyboard for [ImeAction.Done]) and single-line fields get the clear button.
 * [showLabel] = false hides the visible label (search fields) while keeping it for accessibility;
 * [leadingIcon] adds a muted icon at the start (e.g. search).
 */
@Composable
fun YomuTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    imeAction: ImeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    onImeAction: (() -> Unit)? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions? = null,
    keyboardActions: KeyboardActions? = null,
    // Replaces the default clear button (e.g. a show/hide password toggle).
    trailingIcon: (@Composable () -> Unit)? = null,
    showLabel: Boolean = true,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val stack = YomuTheme.stack
    val focusManager = LocalFocusManager.current
    val defaultAction: () -> Unit = {
        when (imeAction) {
            ImeAction.Next -> focusManager.moveFocus(FocusDirection.Down)
            else -> focusManager.clearFocus()
        }
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val trailing: (@Composable () -> Unit)? = trailingIcon ?: if (singleLine && value.isNotEmpty() && enabled) {
        {
            YomuIconButton(
                icon = StackIcons.Close,
                contentDescription = "Clear $label",
                onClick = { onValueChange("") },
                tint = stack.muted,
                modifier = Modifier.size(40.dp),
            )
        }
    } else {
        null
    }
    val ring = when {
        isError -> stack.danger
        focused -> stack.ink
        else -> null
    }
    val effectivePlaceholder = placeholder.ifEmpty { if (showLabel) "" else label }

    Column(modifier = modifier.fillMaxWidth()) {
        if (showLabel) {
            Text(
                text = label,
                style = StackType.Label,
                color = stack.ink,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    contentDescription = label
                    if (isError) error(supportingText ?: "Invalid $label")
                },
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = stack.ink),
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = keyboardOptions ?: KeyboardOptions(capitalization = capitalization, imeAction = imeAction),
            keyboardActions = keyboardActions ?: KeyboardActions(onAny = { (onImeAction ?: defaultAction)() }),
            cursorBrush = SolidColor(stack.ink),
            interactionSource = interaction,
            decorationBox = { inner ->
                Row(
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .background(stack.raised, FieldShape)
                        .then(if (ring != null) Modifier.border(1.5.dp, ring, FieldShape) else Modifier)
                        .padding(start = if (leadingIcon != null) 12.dp else 16.dp, end = if (trailing != null) 6.dp else 16.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            leadingIcon,
                            contentDescription = null,
                            tint = stack.muted,
                            modifier = Modifier.padding(vertical = 14.dp).size(24.dp),
                        )
                    }
                    Box(Modifier.weight(1f).padding(vertical = 15.dp)) {
                        if (value.isEmpty() && effectivePlaceholder.isNotEmpty()) {
                            Text(effectivePlaceholder, style = MaterialTheme.typography.bodyLarge, color = stack.faint, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
                        }
                        inner()
                    }
                    if (trailing != null) {
                        Box(Modifier.padding(vertical = 6.dp)) {
                            CompositionLocalProvider(LocalContentColor provides stack.muted) { trailing() }
                        }
                    }
                }
            },
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                style = StackType.Caption,
                color = if (isError) stack.danger else stack.muted,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp),
            )
        }
    }
}

private val FieldShape = RoundedCornerShape(16.dp)
