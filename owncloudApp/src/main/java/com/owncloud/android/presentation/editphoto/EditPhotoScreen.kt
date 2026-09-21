package com.owncloud.android.presentation.editphoto

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudSlider
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import ja.burhanrashid52.photoeditor.PhotoFilter
import ja.burhanrashid52.photoeditor.shape.ArrowPointerLocation
import ja.burhanrashid52.photoeditor.shape.ShapeType
import java.util.Locale
import android.graphics.Color as AndroidColor

private enum class EditPhotoTool {
    None,
    Shape,
    Eraser,
    Text,
    Emoji,
    Filter,
}

private enum class EditPhotoShape {
    Brush,
    Line,
    Oval,
    Rectangle,
    Arrow,
}

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
    modifier: Modifier = Modifier,
) {
    var selectedTool by rememberSaveable { mutableStateOf(EditPhotoTool.None) }
    var selectedShape by rememberSaveable { mutableStateOf(EditPhotoShape.Brush) }
    var brushColor by rememberSaveable { mutableIntStateOf(AndroidColor.WHITE) }
    var brushSize by rememberSaveable { mutableFloatStateOf(DEFAULT_BRUSH_SIZE) }
    var brushOpacity by rememberSaveable { mutableIntStateOf(MAX_BRUSH_OPACITY) }
    var selectedFilter by rememberSaveable { mutableStateOf(PhotoFilter.NONE.name) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var textSession by remember { mutableStateOf<EditPhotoTextSession?>(null) }

    val toolsEnabled = uiState is EditPhotoUiState.Ready && uiState.isImageLoaded

    LaunchedEffect(selectedTool, selectedShape, brushColor, brushSize, brushOpacity, toolsEnabled) {
        if (!toolsEnabled) return@LaunchedEffect
        when (selectedTool) {
            EditPhotoTool.Shape -> editorHandle.applyShape(
                shapeType = selectedShape.toShapeType(),
                color = brushColor,
                size = brushSize,
                opacity = brushOpacity,
            )
            EditPhotoTool.Eraser -> editorHandle.enableEraser(brushSize)
            EditPhotoTool.None,
            EditPhotoTool.Text,
            EditPhotoTool.Emoji,
            EditPhotoTool.Filter -> editorHandle.disableDrawing()
        }
    }

    EditPhotoScreenView(
        uiState = uiState,
        errorMessage = errorMessage,
        editorHandle = editorHandle,
        selectedTool = selectedTool,
        selectedShape = selectedShape,
        brushColor = brushColor,
        brushSize = brushSize,
        brushOpacity = brushOpacity,
        selectedFilter = selectedFilter,
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
            val nextTool = if (selectedTool == tool) EditPhotoTool.None else tool
            selectedTool = nextTool
            if (nextTool == EditPhotoTool.Text) {
                if (textSession == null) {
                    textSession = EditPhotoTextSession(view = null, text = "", color = brushColor)
                }
            } else {
                textSession = null
            }
        },
        onShapeSelected = { selectedShape = it },
        onBrushColorChange = { brushColor = it },
        onBrushSizeChange = { brushSize = it },
        onBrushOpacityChange = { brushOpacity = it },
        onFilterSelected = { filter ->
            selectedFilter = filter.name
            editorHandle.setFilter(filter)
        },
        onUndo = {
            val nothingLeftToUndo = editorHandle.undo()
            canUndo = !nothingLeftToUndo
            canRedo = true
        },
        onRedo = {
            val nothingLeftToRedo = editorHandle.redo()
            canRedo = !nothingLeftToRedo
            canUndo = true
        },
        onEditTextRequested = { view, text, color ->
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
    selectedShape: EditPhotoShape,
    brushColor: Int,
    brushSize: Float,
    brushOpacity: Int,
    selectedFilter: String,
    canUndo: Boolean,
    canRedo: Boolean,
    textSession: EditPhotoTextSession?,
    toolsEnabled: Boolean,
    onErrorDismissed: () -> Unit,
    onImageLoaded: (success: Boolean) -> Unit,
    onHistoryChanged: (canUndo: Boolean, canRedo: Boolean) -> Unit,
    onToolSelected: (EditPhotoTool) -> Unit,
    onShapeSelected: (EditPhotoShape) -> Unit,
    onBrushColorChange: (Int) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onBrushOpacityChange: (Int) -> Unit,
    onFilterSelected: (PhotoFilter) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onEditTextRequested: (View, String, Int) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onTextConfirmed: (String, Int) -> Unit,
    onTextDismissed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localFilePath = uiState.localFilePath()
    val showLoading = uiState is EditPhotoUiState.Loading ||
        (uiState is EditPhotoUiState.Ready && !uiState.isImageLoaded)
    val isPreview = LocalInspectionMode.current

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black),
        ) {
            if (localFilePath != null && !isPreview) {
                EditPhotoEditorHost(
                    localFilePath = localFilePath,
                    handle = editorHandle,
                    onImageLoaded = onImageLoaded,
                    onEditTextRequested = onEditTextRequested,
                    onHistoryChanged = onHistoryChanged,
                    modifier = Modifier.fillMaxSize(),
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
        if (selectedTool == EditPhotoTool.Shape) {
            EditPhotoShapePanel(
                selectedShape = selectedShape,
                brushColor = brushColor,
                brushSize = brushSize,
                brushOpacity = brushOpacity,
                enabled = toolsEnabled,
                onShapeSelected = onShapeSelected,
                onBrushColorChange = onBrushColorChange,
                onBrushSizeChange = onBrushSizeChange,
                onBrushOpacityChange = onBrushOpacityChange,
            )
        }
        if (selectedTool == EditPhotoTool.Filter) {
            EditPhotoFilterStrip(
                selectedFilter = selectedFilter,
                enabled = toolsEnabled,
                onFilterSelected = onFilterSelected,
            )
        }
        EditPhotoToolsRow(
            selectedTool = selectedTool,
            canUndo = canUndo,
            canRedo = canRedo,
            enabled = toolsEnabled,
            onToolSelected = onToolSelected,
            onUndo = onUndo,
            onRedo = onRedo,
        )
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
}

@Composable
private fun EditPhotoShapePanel(
    selectedShape: EditPhotoShape,
    brushColor: Int,
    brushSize: Float,
    brushOpacity: Int,
    enabled: Boolean,
    onShapeSelected: (EditPhotoShape) -> Unit,
    onBrushColorChange: (Int) -> Unit,
    onBrushSizeChange: (Float) -> Unit,
    onBrushOpacityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dimensionResource(R.dimen.standard_half_margin)),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
        ) {
            EditPhotoShape.entries.forEach { shape ->
                FilterChip(
                    selected = shape == selectedShape,
                    onClick = { onShapeSelected(shape) },
                    enabled = enabled,
                    label = { Text(text = stringResource(shape.labelRes)) },
                )
            }
        }
        EditPhotoColorRow(
            selectedColor = brushColor,
            enabled = enabled,
            onColorSelected = onBrushColorChange,
        )
        Text(
            text = stringResource(R.string.homecloud_editphoto_brush_size),
            style = MaterialTheme.typography.bodyMedium,
        )
        HomeCloudSlider(
            value = brushSize,
            onValueChange = onBrushSizeChange,
            valueRange = MIN_BRUSH_SIZE..MAX_BRUSH_SIZE,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.homecloud_editphoto_brush_opacity),
            style = MaterialTheme.typography.bodyMedium,
        )
        HomeCloudSlider(
            value = brushOpacity.toFloat(),
            onValueChange = { onBrushOpacityChange(it.toInt()) },
            valueRange = 0f..MAX_BRUSH_OPACITY.toFloat(),
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditPhotoColorRow(
    selectedColor: Int,
    enabled: Boolean,
    onColorSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.homecloud_editphoto_color),
            style = MaterialTheme.typography.bodyMedium,
        )
        EDIT_PHOTO_COLORS.forEach { color ->
            val selected = color == selectedColor
            Box(
                modifier = Modifier
                    .size(COLOR_SWATCH_SIZE)
                    .clip(CircleShape)
                    .background(Color(color))
                    .border(
                        width = if (selected) SELECTED_SWATCH_BORDER else UNSELECTED_SWATCH_BORDER,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    )
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = { onColorSelected(color) },
                    ),
            )
        }
    }
}

@Composable
private fun EditPhotoFilterStrip(
    selectedFilter: String,
    enabled: Boolean,
    onFilterSelected: (PhotoFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(dimensionResource(R.dimen.standard_half_margin)),
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
    ) {
        PhotoFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter.name == selectedFilter,
                onClick = { onFilterSelected(filter) },
                enabled = enabled,
                label = { Text(text = filter.displayLabel()) },
            )
        }
    }
}

@Composable
private fun EditPhotoToolsRow(
    selectedTool: EditPhotoTool,
    canUndo: Boolean,
    canRedo: Boolean,
    enabled: Boolean,
    onToolSelected: (EditPhotoTool) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(dimensionResource(R.dimen.standard_half_margin)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditPhotoToolButton(
            icon = Icons.Filled.Brush,
            label = stringResource(R.string.homecloud_editphoto_tool_shape),
            selected = selectedTool == EditPhotoTool.Shape,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Shape) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.AutoFixOff,
            label = stringResource(R.string.homecloud_editphoto_tool_eraser),
            selected = selectedTool == EditPhotoTool.Eraser,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Eraser) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.TextFields,
            label = stringResource(R.string.homecloud_editphoto_tool_text),
            selected = selectedTool == EditPhotoTool.Text,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Text) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.EmojiEmotions,
            label = stringResource(R.string.homecloud_editphoto_tool_emoji),
            selected = selectedTool == EditPhotoTool.Emoji,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Emoji) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.PhotoFilter,
            label = stringResource(R.string.homecloud_editphoto_tool_filter),
            selected = selectedTool == EditPhotoTool.Filter,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Filter) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.Undo,
            label = stringResource(R.string.homecloud_editphoto_tool_undo),
            selected = false,
            enabled = enabled && canUndo,
            onClick = onUndo,
        )
        EditPhotoToolButton(
            icon = Icons.Filled.Redo,
            label = stringResource(R.string.homecloud_editphoto_tool_redo),
            selected = false,
            enabled = enabled && canRedo,
            onClick = onRedo,
        )
    }
}

@Composable
private fun EditPhotoToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED_ICON_ALPHA)
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurface
            },
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

private fun EditPhotoShape.toShapeType(): ShapeType = when (this) {
    EditPhotoShape.Brush -> ShapeType.Brush
    EditPhotoShape.Line -> ShapeType.Line
    EditPhotoShape.Oval -> ShapeType.Oval
    EditPhotoShape.Rectangle -> ShapeType.Rectangle
    EditPhotoShape.Arrow -> ShapeType.Arrow(pointerLocation = ArrowPointerLocation.END)
}

private val EditPhotoShape.labelRes: Int
    get() = when (this) {
        EditPhotoShape.Brush -> R.string.homecloud_editphoto_shape_brush
        EditPhotoShape.Line -> R.string.homecloud_editphoto_shape_line
        EditPhotoShape.Oval -> R.string.homecloud_editphoto_shape_oval
        EditPhotoShape.Rectangle -> R.string.homecloud_editphoto_shape_rectangle
        EditPhotoShape.Arrow -> R.string.homecloud_editphoto_shape_arrow
    }

private fun PhotoFilter.displayLabel(): String =
    name.split('_').joinToString(" ") { part ->
        part.lowercase(Locale.US).replaceFirstChar { char ->
            if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
        }
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
                selectedTool = EditPhotoTool.Shape,
            ),
            EditPhotoScreenPreviewModel(
                uiState = EditPhotoUiState.Loading,
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
                selectedShape = EditPhotoShape.Brush,
                brushColor = AndroidColor.WHITE,
                brushSize = DEFAULT_BRUSH_SIZE,
                brushOpacity = MAX_BRUSH_OPACITY,
                selectedFilter = PhotoFilter.NONE.name,
                canUndo = false,
                canRedo = false,
                textSession = null,
                toolsEnabled = model.uiState is EditPhotoUiState.Ready &&
                    (model.uiState as EditPhotoUiState.Ready).isImageLoaded,
                onErrorDismissed = {},
                onImageLoaded = {},
                onHistoryChanged = { _, _ -> },
                onToolSelected = {},
                onShapeSelected = {},
                onBrushColorChange = {},
                onBrushSizeChange = {},
                onBrushOpacityChange = {},
                onFilterSelected = {},
                onUndo = {},
                onRedo = {},
                onEditTextRequested = { _, _, _ -> },
                onEmojiSelected = {},
                onTextConfirmed = { _, _ -> },
                onTextDismissed = {},
            )
        }
    }
}

private val previewEditorHandle = PhotoEditorHandle()

private val EDIT_PHOTO_COLORS = listOf(
    AndroidColor.WHITE,
    AndroidColor.BLACK,
    AndroidColor.RED,
    AndroidColor.GREEN,
    AndroidColor.BLUE,
    AndroidColor.YELLOW,
    AndroidColor.MAGENTA,
    AndroidColor.CYAN,
)

private val EDIT_PHOTO_EMOJIS = listOf(
    "😀", "😂", "😍", "😎", "😢", "😡", "👍", "👎", "🎉", "🔥",
    "❤️", "💯", "⭐", "🌈", "☀️", "🌸", "🍕", "⚽", "🎵", "✨",
)

private const val PREVIEW_LOCAL_PATH = "/preview"
private const val DEFAULT_BRUSH_SIZE = 25f
private const val MIN_BRUSH_SIZE = 5f
private const val MAX_BRUSH_SIZE = 80f
private const val MAX_BRUSH_OPACITY = 255
private const val DISABLED_ICON_ALPHA = 0.38f
private val COLOR_SWATCH_SIZE = 28.dp
private val SELECTED_SWATCH_BORDER = 2.dp
private val UNSELECTED_SWATCH_BORDER = 1.dp
private val EMOJI_CELL_SIZE = 48.dp
private val EMOJI_FONT_SIZE = 28.sp
private val DIALOG_CORNER_RADIUS = 16.dp
