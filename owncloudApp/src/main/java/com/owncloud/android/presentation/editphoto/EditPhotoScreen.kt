package com.owncloud.android.presentation.editphoto

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudBanner
import com.owncloud.android.presentation.common.compose.HomeCloudBannerStyle
import com.owncloud.android.presentation.common.compose.HomeCloudFileExistsDialog
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import ja.burhanrashid52.photoeditor.shape.ArrowPointerLocation
import ja.burhanrashid52.photoeditor.shape.ShapeType
import java.io.File
import android.graphics.Color as AndroidColor

private data class EditPhotoTextSession(
    val view: View?,
    val text: String,
    val color: Int,
)

@Composable
fun EditPhotoScreen(
    uiState: EditPhotoUiState,
    errorMessage: String?,
    editorHandle: PhotoEditorHandle,
    onErrorDismissed: () -> Unit,
    onImageLoaded: (success: Boolean) -> Unit,
    onOverwriteChosen: () -> Unit,
    onSaveAsCopyChosen: () -> Unit,
    onConflictDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTool by rememberSaveable { mutableStateOf(EditPhotoTool.None) }
    var showAddMenu by rememberSaveable { mutableStateOf(false) }
    var brushColor by rememberSaveable { mutableIntStateOf(AndroidColor.WHITE) }
    var penSize by rememberSaveable { mutableFloatStateOf(PEN_DEFAULT_SIZE) }
    var penOpacity by rememberSaveable { mutableIntStateOf(PEN_DEFAULT_OPACITY) }
    var markerSize by rememberSaveable { mutableFloatStateOf(MARKER_DEFAULT_SIZE) }
    var markerOpacity by rememberSaveable { mutableIntStateOf(MARKER_DEFAULT_OPACITY) }
    var pencilSize by rememberSaveable { mutableFloatStateOf(PENCIL_DEFAULT_SIZE) }
    var pencilOpacity by rememberSaveable { mutableIntStateOf(PENCIL_DEFAULT_OPACITY) }
    var eraserSize by rememberSaveable { mutableFloatStateOf(DEFAULT_ERASER_SIZE) }
    var shapeSize by rememberSaveable { mutableFloatStateOf(PEN_DEFAULT_SIZE) }
    var shapeOpacity by rememberSaveable { mutableIntStateOf(PEN_DEFAULT_OPACITY) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var textSession by remember { mutableStateOf<EditPhotoTextSession?>(null) }

    val toolsEnabled = uiState is EditPhotoUiState.Ready && uiState.isImageLoaded
    val activeStrokeSize = if (selectedTool.isShapeTool) {
        shapeSize
    } else {
        selectedTool.brushSize(penSize, markerSize, pencilSize)
    }
    val activeStrokeOpacity = if (selectedTool.isShapeTool) {
        shapeOpacity
    } else {
        selectedTool.brushOpacity(penOpacity, markerOpacity, pencilOpacity)
    }

    LaunchedEffect(
        selectedTool,
        brushColor,
        penSize,
        penOpacity,
        markerSize,
        markerOpacity,
        pencilSize,
        pencilOpacity,
        eraserSize,
        shapeSize,
        shapeOpacity,
        toolsEnabled,
    ) {
        if (!toolsEnabled) return@LaunchedEffect
        when (selectedTool) {
            EditPhotoTool.Pen,
            EditPhotoTool.Marker,
            EditPhotoTool.Pencil -> editorHandle.applyShape(
                shapeType = ShapeType.Brush,
                color = brushColor,
                size = selectedTool.brushSize(penSize, markerSize, pencilSize),
                opacity = selectedTool.brushOpacity(penOpacity, markerOpacity, pencilOpacity),
            )
            EditPhotoTool.Line -> editorHandle.applyShape(
                shapeType = ShapeType.Line,
                color = brushColor,
                size = shapeSize,
                opacity = shapeOpacity,
            )
            EditPhotoTool.Oval -> editorHandle.applyShape(
                shapeType = ShapeType.Oval,
                color = brushColor,
                size = shapeSize,
                opacity = shapeOpacity,
            )
            EditPhotoTool.Rectangle -> editorHandle.applyShape(
                shapeType = ShapeType.Rectangle,
                color = brushColor,
                size = shapeSize,
                opacity = shapeOpacity,
            )
            EditPhotoTool.Arrow -> editorHandle.applyShape(
                shapeType = ShapeType.Arrow(pointerLocation = ArrowPointerLocation.END),
                color = brushColor,
                size = shapeSize,
                opacity = shapeOpacity,
            )
            EditPhotoTool.Eraser -> editorHandle.enableEraser(eraserSize)
            EditPhotoTool.None,
            EditPhotoTool.Text,
            EditPhotoTool.Emoji -> editorHandle.disableDrawing()
        }
    }

    EditPhotoScreenView(
        uiState = uiState,
        errorMessage = errorMessage,
        editorHandle = editorHandle,
        selectedTool = selectedTool,
        showAddMenu = showAddMenu,
        brushColor = brushColor,
        brushSize = activeStrokeSize,
        brushOpacity = activeStrokeOpacity,
        eraserSize = eraserSize,
        canUndo = canUndo,
        canRedo = canRedo,
        textSession = textSession,
        toolsEnabled = toolsEnabled,
        onErrorDismissed = onErrorDismissed,
        onImageLoaded = onImageLoaded,
        onHistoryChanged = { undoEnabled, redoEnabled ->
            canUndo = undoEnabled
            canRedo = redoEnabled
        },
        onToolSelected = { tool ->
            showAddMenu = false
            val nextTool = if (selectedTool == tool) EditPhotoTool.None else tool
            selectedTool = nextTool
            textSession = null
        },
        onAddClick = {
            showAddMenu = true
            if (selectedTool.isShapeTool) {
                selectedTool = EditPhotoTool.None
            }
            textSession = null
        },
        onAddDismissed = { showAddMenu = false },
        onAddItemSelected = { item ->
            showAddMenu = false
            when (item) {
                EditPhotoAddItem.Text -> {
                    selectedTool = EditPhotoTool.Text
                    if (textSession == null) {
                        textSession = EditPhotoTextSession(view = null, text = "", color = brushColor)
                    }
                }
                EditPhotoAddItem.Emoji -> {
                    textSession = null
                    selectedTool = EditPhotoTool.Emoji
                }
                EditPhotoAddItem.Line -> {
                    textSession = null
                    selectedTool = EditPhotoTool.Line
                }
                EditPhotoAddItem.Oval -> {
                    textSession = null
                    selectedTool = EditPhotoTool.Oval
                }
                EditPhotoAddItem.Rectangle -> {
                    textSession = null
                    selectedTool = EditPhotoTool.Rectangle
                }
                EditPhotoAddItem.Arrow -> {
                    textSession = null
                    selectedTool = EditPhotoTool.Arrow
                }
            }
        },
        onBrushColorChange = { brushColor = it },
        onBrushSizeChange = { size ->
            when {
                selectedTool.isShapeTool -> shapeSize = size
                selectedTool == EditPhotoTool.Pen -> penSize = size
                selectedTool == EditPhotoTool.Marker -> markerSize = size
                selectedTool == EditPhotoTool.Pencil -> pencilSize = size
            }
        },
        onBrushOpacityChange = { opacity ->
            when {
                selectedTool.isShapeTool -> shapeOpacity = opacity
                selectedTool == EditPhotoTool.Pen -> penOpacity = opacity
                selectedTool == EditPhotoTool.Marker -> markerOpacity = opacity
                selectedTool == EditPhotoTool.Pencil -> pencilOpacity = opacity
            }
        },
        onEraserSizeChange = { eraserSize = it },
        onUndo = {
            canUndo = editorHandle.undo()
            canRedo = true
        },
        onRedo = {
            canRedo = editorHandle.redo()
            canUndo = true
        },
        onEditTextRequested = { view, text, color ->
            showAddMenu = false
            selectedTool = EditPhotoTool.Text
            textSession = EditPhotoTextSession(view = view, text = text, color = color)
        },
        onEmojiSelected = { emoji ->
            editorHandle.addEmoji(emoji)
            selectedTool = EditPhotoTool.None
        },
        onTextConfirmed = { text, color ->
            val session = textSession
            if (session?.view != null) {
                editorHandle.editText(session.view, text, color)
            } else {
                editorHandle.addText(text, color)
            }
            textSession = null
            selectedTool = EditPhotoTool.None
        },
        onTextDismissed = {
            textSession = null
            selectedTool = EditPhotoTool.None
        },
        onOverwriteChosen = onOverwriteChosen,
        onSaveAsCopyChosen = onSaveAsCopyChosen,
        onConflictDismissed = onConflictDismissed,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPhotoScreenView(
    uiState: EditPhotoUiState,
    errorMessage: String?,
    editorHandle: PhotoEditorHandle,
    selectedTool: EditPhotoTool,
    showAddMenu: Boolean,
    brushColor: Int,
    brushSize: Float,
    brushOpacity: Int,
    eraserSize: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    textSession: EditPhotoTextSession?,
    toolsEnabled: Boolean,
    onErrorDismissed: () -> Unit,
    onImageLoaded: (success: Boolean) -> Unit,
    onHistoryChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    onToolSelected: (EditPhotoTool) -> Unit,
    onAddClick: () -> Unit,
    onAddDismissed: () -> Unit,
    onAddItemSelected: (EditPhotoAddItem) -> Unit,
    onBrushColorChange: (Int) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onBrushOpacityChange: (Int) -> Unit,
    onEraserSizeChange: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onEditTextRequested: (View, String, Int) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onTextConfirmed: (String, Int) -> Unit,
    onTextDismissed: () -> Unit,
    onOverwriteChosen: () -> Unit,
    onSaveAsCopyChosen: () -> Unit,
    onConflictDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localFilePath = uiState.localFilePath()
    val showLoading = uiState is EditPhotoUiState.Saving ||
        (uiState is EditPhotoUiState.Ready && !uiState.isImageLoaded)
    val isPreview = LocalInspectionMode.current
    val drawingBarModifier = Modifier
        .fillMaxWidth()
        .padding(dimensionResource(R.dimen.standard_half_margin))

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            if (localFilePath != null && !isPreview && uiState !is EditPhotoUiState.Downloading) {
                EditPhotoEditorHost(
                    localFilePath = localFilePath,
                    handle = editorHandle,
                    onImageLoaded = onImageLoaded,
                    onEditTextRequested = onEditTextRequested,
                    onHistoryChanged = onHistoryChanged,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (uiState is EditPhotoUiState.Downloading) {
                EditPhotoDownloadProgress(
                    progressPercent = uiState.progress,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .padding(dimensionResource(R.dimen.standard_margin)),
                )
            }
            if (showLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        if (errorMessage != null) {
            HomeCloudBanner(
                message = errorMessage,
                style = HomeCloudBannerStyle.ERROR,
                onDismiss = onErrorDismissed,
                modifier = Modifier.padding(dimensionResource(R.dimen.standard_half_margin)),
            )
        }
        if (selectedTool.isBrushTool || selectedTool.isShapeTool) {
            EditPhotoBrushSettings(
                selectedColor = brushColor,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                enabled = toolsEnabled,
                onColorSelected = onBrushColorChange,
                onSizeChange = onBrushSizeChange,
                onOpacityChange = onBrushOpacityChange,
                modifier = drawingBarModifier,
            )
        }
        if (selectedTool == EditPhotoTool.Eraser) {
            EditPhotoEraserSettings(
                eraserSize = eraserSize,
                enabled = toolsEnabled,
                onSizeChange = onEraserSizeChange,
                modifier = drawingBarModifier,
            )
        }
        EditPhotoToolsRow(
            selectedTool = selectedTool,
            addMenuOpen = showAddMenu,
            canUndo = canUndo,
            canRedo = canRedo,
            enabled = toolsEnabled,
            onToolSelected = onToolSelected,
            onAddClick = onAddClick,
            onUndo = onUndo,
            onRedo = onRedo,
            modifier = drawingBarModifier,
        )
    }
    if (showAddMenu) {
        ModalBottomSheet(onDismissRequest = onAddDismissed) {
            EditPhotoAddMenu(
                onItemSelected = onAddItemSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(dimensionResource(R.dimen.standard_margin)),
            )
        }
    }
    if (selectedTool == EditPhotoTool.Emoji) {
        ModalBottomSheet(onDismissRequest = { onToolSelected(EditPhotoTool.None) }) {
            EditPhotoEmojiGrid(
                onEmojiSelected = onEmojiSelected,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (textSession != null) {
        EditPhotoTextDialog(
            session = textSession,
            onConfirm = onTextConfirmed,
            onDismiss = onTextDismissed,
        )
    }
    if (uiState is EditPhotoUiState.NameConflict) {
        HomeCloudFileExistsDialog(
            fileName = uiState.existingFileName,
            onOverwrite = onOverwriteChosen,
            onSaveAsCopy = onSaveAsCopyChosen,
            onDismiss = onConflictDismissed,
        )
    }
}

@Composable
private fun EditPhotoEmojiGrid(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = EMOJI_CELL_SIZE),
        modifier = modifier.padding(dimensionResource(R.dimen.standard_margin)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
    ) {
        items(EDIT_PHOTO_EMOJIS) { emoji ->
            Text(
                text = emoji,
                fontSize = EMOJI_FONT_SIZE,
                modifier = Modifier
                    .clickable(role = Role.Button) { onEmojiSelected(emoji) }
                    .padding(dimensionResource(R.dimen.standard_half_margin)),
            )
        }
    }
}

@Composable
private fun EditPhotoTextDialog(
    session: EditPhotoTextSession,
    onConfirm: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(session) { mutableStateOf(session.text) }
    var color by remember(session) { mutableIntStateOf(session.color) }
    val isEdit = session.view != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEdit) {
                        R.string.homecloud_editphoto_text_edit_title
                    } else {
                        R.string.homecloud_editphoto_text_title
                    },
                ),
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin))) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(text = stringResource(R.string.homecloud_editphoto_text_hint)) },
                    singleLine = true,
                )
                EditPhotoColorRow(
                    selectedColor = color,
                    enabled = true,
                    onColorSelected = { color = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text, color) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorResource(R.color.homecloud_dialog_button_enabled),
                    disabledContentColor = colorResource(R.color.homecloud_dialog_button_disabled),
                ),
            ) {
                Text(text = stringResource(R.string.homecloud_editphoto_text_done))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorResource(R.color.homecloud_dialog_button_enabled),
                ),
            ) {
                Text(text = stringResource(R.string.homecloud_cancel))
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(DIALOG_CORNER_RADIUS),
        containerColor = colorResource(R.color.homecloud_dialog_background),
        titleContentColor = colorResource(R.color.homecloud_primary),
        textContentColor = colorResource(R.color.homecloud_primary),
        tonalElevation = 0.dp,
    )
}

@Composable
private fun EditPhotoDownloadProgress(
    progressPercent: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.homecloud_editphoto_download_in_progress),
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.standard_margin)))
        if (progressPercent < 0) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                trackColor = colorResource(R.color.homecloud_surface),
            )
        } else {
            LinearProgressIndicator(
                progress = { progressPercent.coerceIn(0, 100) / 100f },
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                trackColor = colorResource(R.color.homecloud_surface),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun EditPhotoTool.brushSize(
    penSize: Float,
    markerSize: Float,
    pencilSize: Float,
): Float = when (this) {
    EditPhotoTool.Pen -> penSize
    EditPhotoTool.Marker -> markerSize
    EditPhotoTool.Pencil -> pencilSize
    else -> penSize
}

private fun EditPhotoTool.brushOpacity(
    penOpacity: Int,
    markerOpacity: Int,
    pencilOpacity: Int,
): Int = when (this) {
    EditPhotoTool.Pen -> penOpacity
    EditPhotoTool.Marker -> markerOpacity
    EditPhotoTool.Pencil -> pencilOpacity
    else -> penOpacity
}

private data class EditPhotoScreenPreviewModel(
    val uiState: EditPhotoUiState,
    val errorMessage: String? = null,
    val selectedTool: EditPhotoTool = EditPhotoTool.None,
)

private class EditPhotoScreenPreviewParameterProvider :
    CollectionPreviewParameterProvider<EditPhotoScreenPreviewModel>(
        listOf(
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.Ready(
                    localFilePath = PREVIEW_LOCAL_PATH,
                    isImageLoaded = true,
                ),
                selectedTool = EditPhotoTool.Pen,
            ),
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.Ready(
                    localFilePath = PREVIEW_LOCAL_PATH,
                    isImageLoaded = true,
                ),
                selectedTool = EditPhotoTool.Eraser,
            ),
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.Ready(
                    localFilePath = PREVIEW_LOCAL_PATH,
                    isImageLoaded = true,
                ),
                selectedTool = EditPhotoTool.Line,
            ),
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.Downloading(progress = INDETERMINATE_DOWNLOAD_PROGRESS),
            ),
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.Downloading(progress = 40),
            ),
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.NameConflict(
                    localFilePath = PREVIEW_LOCAL_PATH,
                    existingFileName = PREVIEW_FILE_NAME,
                    tempOutputFile = File(PREVIEW_FILE_NAME),
                ),
            ),
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.Unavailable(
                    errorMessageRes = R.string.homecloud_editphoto_unavailable,
                ),
                errorMessage = "This photo is not available on this device.",
            ),
        ),
    )

@HomeCloudPreview
@Composable
private fun EditPhotoScreenPreview(
    @PreviewParameter(EditPhotoScreenPreviewParameterProvider::class)
    model: EditPhotoScreenPreviewModel,
) {
    HomeCloudTheme {
        Surface {
            EditPhotoScreenView(
                uiState = model.uiState,
                errorMessage = model.errorMessage,
                editorHandle = previewEditorHandle,
                selectedTool = model.selectedTool,
                showAddMenu = false,
                brushColor = AndroidColor.WHITE,
                brushSize = PEN_DEFAULT_SIZE,
                brushOpacity = PEN_DEFAULT_OPACITY,
                eraserSize = DEFAULT_ERASER_SIZE,
                canUndo = false,
                canRedo = false,
                textSession = null,
                toolsEnabled = (model.uiState as? EditPhotoUiState.Ready)?.isImageLoaded == true,
                onErrorDismissed = {},
                onImageLoaded = {},
                onHistoryChanged = { _, _ -> },
                onToolSelected = {},
                onAddClick = {},
                onAddDismissed = {},
                onAddItemSelected = {},
                onBrushColorChange = {},
                onBrushSizeChange = {},
                onBrushOpacityChange = {},
                onEraserSizeChange = {},
                onUndo = {},
                onRedo = {},
                onEditTextRequested = { _, _, _ -> },
                onEmojiSelected = {},
                onTextConfirmed = { _, _ -> },
                onTextDismissed = {},
                onOverwriteChosen = {},
                onSaveAsCopyChosen = {},
                onConflictDismissed = {},
            )
        }
    }
}

private val previewEditorHandle = PhotoEditorHandle()

private val EDIT_PHOTO_EMOJIS = listOf(
    "😀", "😂", "😍", "😎", "😢", "😡", "👍", "👎", "🎉", "🔥",
    "❤️", "💯", "⭐", "🌈", "☀️", "🌸", "🍕", "⚽", "🎵", "✨",
)

private const val PREVIEW_LOCAL_PATH = "/preview"
private const val PREVIEW_FILE_NAME = "photo.jpg"
private val EMOJI_CELL_SIZE = 48.dp
private val EMOJI_FONT_SIZE = 28.sp
private val DIALOG_CORNER_RADIUS = 16.dp
