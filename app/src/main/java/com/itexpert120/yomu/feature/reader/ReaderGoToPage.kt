package com.itexpert120.yomu.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.core.designsystem.yomuPressable
import com.itexpert120.yomu.core.reader.ReaderPageTarget

/**
 * "Go to page" for a book with printed page numbers: a field like the contents filter (raised, radius
 * 16) taking a page number, with Go at its end. A page that isn't in the book says so under it.
 */
@Composable
internal fun ReaderGoToPageField(
    pages: List<ReaderPageTarget>,
    onGoToPage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stack = YomuTheme.stack
    val focusManager = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    var missing by remember { mutableStateOf<String?>(null) }
    val numeric = remember(pages) { pages.all { it.label.toIntOrNull() != null } }
    val range = remember(pages) { "${pages.first().label}–${pages.last().label}" }
    val go = {
        val wanted = query.trim()
        val page = findPrintedPage(pages, wanted)
        if (page != null) {
            missing = null
            focusManager.clearFocus()
            onGoToPage(page.locatorJson)
        } else if (wanted.isNotEmpty()) {
            missing = "This book has no page $wanted"
        }
    }
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(stack.raised)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(StackIcons.MenuBook, contentDescription = null, tint = stack.muted, modifier = Modifier.size(20.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                val placeholder = "Go to page ($range)"
                if (query.isEmpty()) {
                    Text(text = placeholder, color = stack.faint, style = StackType.Body.copy(fontSize = 16.sp), maxLines = 1)
                }
                BasicTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        missing = null
                    },
                    singleLine = true,
                    textStyle = StackType.Body.copy(fontSize = 16.sp, color = stack.ink),
                    cursorBrush = SolidColor(stack.ink),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Ascii,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { go() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = placeholder },
                )
            }
            if (query.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(CircleShape)
                        .yomuPressable(onClick = go, shape = CircleShape)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Go", color = stack.ink, style = StackType.Label.copy(fontSize = 15.sp))
                }
            }
        }
        missing?.let {
            Text(text = it, color = stack.danger, style = StackType.Caption, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
        }
    }
}
