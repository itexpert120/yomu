package com.itexpert120.yomu.feature.annotations

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.itexpert120.yomu.core.designsystem.StackType
import com.itexpert120.yomu.core.designsystem.YomuBottomSheet
import com.itexpert120.yomu.core.designsystem.YomuDialog
import com.itexpert120.yomu.core.designsystem.YomuDialogCard
import com.itexpert120.yomu.core.designsystem.YomuLoadingLine
import com.itexpert120.yomu.core.designsystem.YomuSettingList
import com.itexpert120.yomu.core.designsystem.YomuSettingRow
import com.itexpert120.yomu.core.designsystem.YomuSheetHeader
import com.itexpert120.yomu.core.designsystem.YomuTheme
import com.itexpert120.yomu.core.designsystem.icons.StackIcons
import com.itexpert120.yomu.data.annotations.AnnotationFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Types offered by "Import highlights…": exports are JSON, which some file managers label as plain or binary. */
private val ImportMimeTypes = arrayOf("application/json", "text/plain", "application/octet-stream")

/**
 * Remembers the system file picker for "Import highlights…" ([AnnotationTransferAction.PickFile]);
 * invoking the result opens it, and a picked file is checked through [onAction]. Registered at the
 * screen's root so a result still lands after the menu or sheet that asked has gone.
 */
@Composable
fun rememberAnnotationImportPicker(onAction: (AnnotationTransferAction) -> Unit): () -> Unit {
    val currentOnAction by rememberUpdatedState(onAction)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) currentOnAction(AnnotationTransferAction.CheckImport(uri))
    }
    return { runCatching { launcher.launch(ImportMimeTypes) } }
}

/**
 * The export / import entries for an overflow menu: "Export highlights & notes", "Import
 * highlights…", and "Import Calibre highlights" only when the book's EPUB carries some.
 * [closeMenu] runs before each pick.
 */
@Composable
fun ColumnScope.AnnotationTransferMenuItems(
    calibreAvailable: Boolean,
    onAction: (AnnotationTransferAction) -> Unit,
    closeMenu: () -> Unit,
) {
    AnnotationMenuItem(StackIcons.Share, "Export highlights & notes") {
        closeMenu()
        onAction(AnnotationTransferAction.Export)
    }
    AnnotationMenuItem(StackIcons.Download, "Import highlights…") {
        closeMenu()
        onAction(AnnotationTransferAction.PickFile)
    }
    if (calibreAvailable) {
        AnnotationMenuItem(StackIcons.LibraryBooks, "Import Calibre highlights") {
            closeMenu()
            onAction(AnnotationTransferAction.ImportCalibre)
        }
    }
}

@Composable
private fun AnnotationMenuItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    val ink = YomuTheme.stack.ink
    DropdownMenuItem(
        text = { Text(text, style = StackType.Body, color = ink) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = ink) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/**
 * The surfaces of the export / import flow, for a screen that owns an [AnnotationTransfer]: the
 * format sheet, the import preview dialog, and the share sheet for a finished export. Results are
 * the screen's own notice to show ([AnnotationTransferState.notice]).
 */
@Composable
fun AnnotationTransferHost(
    state: AnnotationTransferState,
    onAction: (AnnotationTransferAction) -> Unit,
    shares: Flow<Intent> = emptyFlow(),
) {
    val context = LocalContext.current
    LaunchedEffect(shares, context) {
        shares.collect { chooser ->
            try {
                context.startActivity(chooser)
            } catch (_: ActivityNotFoundException) {
                // No app can take the file.
            }
        }
    }

    YomuBottomSheet(visible = state.choosingFormat, onDismiss = { onAction(AnnotationTransferAction.CancelExport) }) { dismiss ->
        AnnotationExportSheetContent(
            working = state.working,
            onPick = { onAction(AnnotationTransferAction.ExportAs(it)) },
            dismiss = dismiss,
        )
    }

    state.importing?.let { importing ->
        AnnotationImportDialog(
            importing = importing,
            onConfirm = { onAction(AnnotationTransferAction.ConfirmImport) },
            onDismiss = { onAction(AnnotationTransferAction.CancelImport) },
        )
    }
}

/** "Export highlights & notes": one row per format, each saying what it's for. */
@Composable
internal fun AnnotationExportSheetContent(
    working: Boolean,
    onPick: (AnnotationFormat) -> Unit,
    dismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        YomuSheetHeader(title = "Export highlights & notes", onClose = dismiss)
        if (working) YomuLoadingLine(Modifier.fillMaxWidth().padding(bottom = YomuTheme.space.xs))
        YomuSettingList {
            AnnotationFormat.entries.forEach { format ->
                val (icon, title, about) = format.describe()
                YomuSettingRow(
                    title = title,
                    subtitle = about,
                    enabled = !working,
                    onClick = { onPick(format) },
                    leadingContent = { Icon(icon, contentDescription = null, tint = YomuTheme.stack.muted) },
                ) {
                    Icon(StackIcons.Share, contentDescription = null, tint = YomuTheme.stack.muted)
                }
            }
        }
    }
}

private fun AnnotationFormat.describe(): Triple<ImageVector, String, String> = when (this) {
    AnnotationFormat.Markdown -> Triple(StackIcons.Notes, "Markdown", "For notes apps like Obsidian or Notion")
    AnnotationFormat.Html -> Triple(StackIcons.Description, "Web page", "Opens in any browser, and prints well")
    AnnotationFormat.Json -> Triple(StackIcons.Book, "Open Reader file", "To import into Open Reader on another device")
}

/**
 * The import preview: what the file holds and what importing changes, with a warning when the file
 * was made from another book.
 */
@Composable
internal fun AnnotationImportDialog(
    importing: AnnotationImportUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = importDialogCopy(importing)
    YomuDialog(
        title = copy.title,
        text = copy.body,
        confirmLabel = copy.confirmLabel,
        dismissLabel = copy.dismissLabel,
        onConfirm = if (importing.preview.changesNothing) onDismiss else onConfirm,
        onDismiss = onDismiss,
    ) {
        copy.warning?.let { ImportWarning(it) }
    }
}

/** The dialog's card alone, for previews (a Dialog window can't render in one). */
@Composable
internal fun AnnotationImportDialogCard(importing: AnnotationImportUi) {
    val copy = importDialogCopy(importing)
    YomuDialogCard(
        title = copy.title,
        text = copy.body,
        confirmLabel = copy.confirmLabel,
        dismissLabel = copy.dismissLabel,
        onConfirm = {},
        onDismiss = {},
    ) {
        copy.warning?.let { ImportWarning(it) }
    }
}

@Composable
private fun ImportWarning(text: String) {
    Text(
        text = text,
        style = StackType.BodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = YomuTheme.stack.ink,
    )
}

internal data class ImportDialogCopy(
    val title: String,
    val body: String,
    val warning: String?,
    val confirmLabel: String,
    val dismissLabel: String?,
)

internal fun importDialogCopy(importing: AnnotationImportUi): ImportDialogCopy {
    val preview = importing.preview
    val source = if (importing.fromCalibre) "Calibre saved" else "The file holds"
    val warning = if (!preview.bookMatches && !importing.fromCalibre) {
        val book = listOfNotNull(
            preview.fileBookTitle?.takeIf { it.isNotBlank() }?.let { "“$it”" },
            preview.fileBookAuthor?.takeIf { it.isNotBlank() }?.let { "by $it" },
        ).joinToString(" ")
        if (book.isEmpty()) {
            "This file may be from another book or edition. Highlights whose text isn't in this book won't show."
        } else {
            "This file is from $book, not this copy of the book. Highlights whose text isn't in this book won't show."
        }
    } else {
        null
    }
    return ImportDialogCopy(
        title = if (importing.fromCalibre) "Import Calibre highlights?" else "Import highlights?",
        body = "$source ${importContents(preview)}. ${importChanges(preview)}",
        warning = warning,
        confirmLabel = if (preview.changesNothing) "Done" else "Import",
        dismissLabel = if (preview.changesNothing) null else "Cancel",
    )
}
