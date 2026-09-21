package com.owncloud.android.presentation.editphoto

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.owncloud.android.R
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudSlider
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import android.graphics.Color as AndroidColor

internal enum class EditPhotoTool {
    None,
    Pen,
    Marker,
    Pencil,
    Eraser,
    Text,
    Emoji,
}

internal val EditPhotoTool.isBrushTool: Boolean
    get() = this == EditPhotoTool.Pen ||
        this == EditPhotoTool.Marker ||
        this == EditPhotoTool.Pencil

@Composable
internal fun EditPhotoBrushSettings(
    selectedColor: Int,
    brushSize: Float,
    brushOpacity: Int,
    enabled: Boolean,
    onColorSelected: (Int) -> Unit,
    onSizeChange: (Float) -> Unit,
    onOpacityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
    ) {
        EditPhotoColorRow(
            selectedColor = selectedColor,
            enabled = enabled,
            onColorSelected = onColorSelected,
        )
        EditPhotoLabeledSlider(
            label = stringResource(R.string.homecloud_editphoto_brush_size),
            value = brushSize,
            onValueChange = onSizeChange,
            valueRange = MIN_BRUSH_SIZE..MAX_BRUSH_SIZE,
            enabled = enabled,
        )
        EditPhotoLabeledSlider(
            label = stringResource(R.string.homecloud_editphoto_brush_opacity),
            value = brushOpacity.toFloat(),
            onValueChange = { onOpacityChange(it.toInt()) },
            valueRange = 0f..MAX_BRUSH_OPACITY.toFloat(),
            enabled = enabled,
        )
    }
}

@Composable
internal fun EditPhotoEraserSettings(
    eraserSize: Float,
    enabled: Boolean,
    onSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    EditPhotoLabeledSlider(
        label = stringResource(R.string.homecloud_editphoto_brush_size),
        value = eraserSize,
        onValueChange = onSizeChange,
        valueRange = MIN_BRUSH_SIZE..MAX_BRUSH_SIZE,
        enabled = enabled,
        modifier = modifier,
    )
}

@Composable
internal fun EditPhotoColorRow(
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
internal fun EditPhotoToolsRow(
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
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditPhotoToolButton(
            icon = Icons.Filled.Create,
            label = stringResource(R.string.homecloud_editphoto_tool_pen),
            selected = selectedTool == EditPhotoTool.Pen,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Pen) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.Brush,
            label = stringResource(R.string.homecloud_editphoto_tool_marker),
            selected = selectedTool == EditPhotoTool.Marker,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Marker) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.Edit,
            label = stringResource(R.string.homecloud_editphoto_tool_pencil),
            selected = selectedTool == EditPhotoTool.Pencil,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Pencil) },
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
            icon = Icons.AutoMirrored.Filled.Undo,
            label = stringResource(R.string.homecloud_editphoto_tool_undo),
            selected = false,
            enabled = enabled && canUndo,
            onClick = onUndo,
        )
        EditPhotoToolButton(
            icon = Icons.AutoMirrored.Filled.Redo,
            label = stringResource(R.string.homecloud_editphoto_tool_redo),
            selected = false,
            enabled = enabled && canRedo,
            onClick = onRedo,
        )
    }
}

@Composable
private fun EditPhotoLabeledSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
        )
        HomeCloudSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
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

@HomeCloudPreview
@Composable
private fun EditPhotoBrushSettingsPreview() {
    HomeCloudTheme {
        Surface {
            EditPhotoBrushSettings(
                selectedColor = AndroidColor.WHITE,
                brushSize = PEN_DEFAULT_SIZE,
                brushOpacity = PEN_DEFAULT_OPACITY,
                enabled = true,
                onColorSelected = {},
                onSizeChange = {},
                onOpacityChange = {},
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun EditPhotoEraserSettingsPreview() {
    HomeCloudTheme {
        Surface {
            EditPhotoEraserSettings(
                eraserSize = DEFAULT_ERASER_SIZE,
                enabled = true,
                onSizeChange = {},
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun EditPhotoToolsRowPreview() {
    HomeCloudTheme {
        Surface {
            EditPhotoToolsRow(
                selectedTool = EditPhotoTool.Pen,
                canUndo = true,
                canRedo = false,
                enabled = true,
                onToolSelected = {},
                onUndo = {},
                onRedo = {},
            )
        }
    }
}

internal const val PEN_DEFAULT_SIZE = 25f
internal const val PEN_DEFAULT_OPACITY = 255
internal const val MARKER_DEFAULT_SIZE = 50f
internal const val MARKER_DEFAULT_OPACITY = 80
internal const val PENCIL_DEFAULT_SIZE = 12f
internal const val PENCIL_DEFAULT_OPACITY = 180
internal const val DEFAULT_ERASER_SIZE = 25f
internal const val MIN_BRUSH_SIZE = 5f
internal const val MAX_BRUSH_SIZE = 80f
internal const val MAX_BRUSH_OPACITY = 255

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

private const val DISABLED_ICON_ALPHA = 0.38f
private val COLOR_SWATCH_SIZE = 28.dp
private val SELECTED_SWATCH_BORDER = 2.dp
private val UNSELECTED_SWATCH_BORDER = 1.dp
