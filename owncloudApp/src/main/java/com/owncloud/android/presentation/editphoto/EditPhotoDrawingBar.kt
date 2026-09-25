package com.owncloud.android.presentation.editphoto

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    Line,
    Oval,
    Rectangle,
    Arrow,
}

internal enum class EditPhotoAddItem {
    Text,
    Emoji,
    Line,
    Oval,
    Rectangle,
    Arrow,
}

internal val EditPhotoTool.isBrushTool: Boolean
    get() = this == EditPhotoTool.Pen ||
        this == EditPhotoTool.Marker ||
        this == EditPhotoTool.Pencil

internal val EditPhotoTool.isShapeTool: Boolean
    get() = this == EditPhotoTool.Line ||
        this == EditPhotoTool.Oval ||
        this == EditPhotoTool.Rectangle ||
        this == EditPhotoTool.Arrow

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
    var customColor by remember {
        mutableStateOf(selectedColor.takeUnless { it in EDIT_PHOTO_COLORS })
    }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    LaunchedEffect(selectedColor) {
        if (selectedColor !in EDIT_PHOTO_COLORS) {
            customColor = selectedColor
        }
    }
    val customColorLabel = stringResource(R.string.homecloud_editphoto_custom_color)
    val shownCustomColor = customColor
    val customSelected = shownCustomColor != null &&
        selectedColor == shownCustomColor &&
        selectedColor !in EDIT_PHOTO_COLORS

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
            EditPhotoColorSwatch(
                background = SolidColor(Color(color)),
                selected = color == selectedColor,
                enabled = enabled,
                onClick = { onColorSelected(color) },
            )
        }
        EditPhotoColorSwatch(
            background = if (shownCustomColor != null) {
                SolidColor(Color(shownCustomColor))
            } else {
                CUSTOM_COLOR_RAINBOW
            },
            selected = customSelected,
            enabled = enabled,
            onClick = { showCustomColorDialog = true },
            contentDescription = customColorLabel,
        )
    }
    if (showCustomColorDialog) {
        EditPhotoCustomColorDialog(
            initialColor = selectedColor,
            onConfirm = { color ->
                customColor = color
                onColorSelected(color)
                showCustomColorDialog = false
            },
            onDismiss = { showCustomColorDialog = false },
        )
    }
}

@Composable
private fun EditPhotoColorSwatch(
    background: Brush,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(COLOR_SWATCH_SIZE)
            .clip(CircleShape)
            .background(background)
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
                onClick = onClick,
            )
            .then(
                if (contentDescription == null) {
                    Modifier
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
    )
}

@Composable
private fun EditPhotoCustomColorDialog(
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val initialHsv = remember(initialColor) {
        FloatArray(HSV_COMPONENTS).also { AndroidColor.colorToHSV(initialColor, it) }
    }
    var hue by remember(initialColor) { mutableFloatStateOf(initialHsv[HUE_INDEX]) }
    var saturation by remember(initialColor) { mutableFloatStateOf(initialHsv[SATURATION_INDEX]) }
    var value by remember(initialColor) { mutableFloatStateOf(initialHsv[VALUE_INDEX]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.homecloud_editphoto_custom_color_title))
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
            ) {
                EditPhotoSaturationValueField(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationValueChange = { newSaturation, newValue ->
                        saturation = newSaturation
                        value = newValue
                    },
                    modifier = Modifier.size(COLOR_FIELD_SIZE),
                )
                EditPhotoHueBar(
                    hue = hue,
                    onHueChange = { hue = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HUE_BAR_HEIGHT),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(AndroidColor.HSVToColor(floatArrayOf(hue, saturation, value)))
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = colorResource(R.color.homecloud_dialog_button_enabled),
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
        shape = RoundedCornerShape(CUSTOM_COLOR_DIALOG_CORNER_RADIUS),
        containerColor = colorResource(R.color.homecloud_dialog_background),
        titleContentColor = colorResource(R.color.homecloud_primary),
        textContentColor = colorResource(R.color.homecloud_primary),
        tonalElevation = 0.dp,
    )
}

@Composable
private fun EditPhotoSaturationValueField(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onSaturationValueChange)
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(COLOR_FIELD_CORNER_RADIUS))
            .pointerInput(Unit) {
                detectPressAndDrag { position ->
                    currentOnChange(
                        (position.x / size.width).coerceIn(0f, 1f),
                        (1f - position.y / size.height).coerceIn(0f, 1f),
                    )
                }
            },
    ) {
        drawRect(Color.hsv(hue, 1f, 1f))
        drawRect(Brush.horizontalGradient(listOf(Color.White, Color.Transparent)))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        drawPickerThumb(
            center = Offset(
                x = (saturation * size.width).coerceIn(thumbRadiusPx(), size.width - thumbRadiusPx()),
                y = ((1f - value) * size.height).coerceIn(thumbRadiusPx(), size.height - thumbRadiusPx()),
            ),
        )
    }
}

@Composable
private fun EditPhotoHueBar(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnHueChange by rememberUpdatedState(onHueChange)
    Canvas(
        modifier = modifier
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectPressAndDrag { position ->
                    val fraction = (position.x / size.width).coerceIn(0f, 1f)
                    currentOnHueChange(fraction * MAX_HUE)
                }
            },
    ) {
        drawRect(Brush.horizontalGradient(HUE_GRADIENT_COLORS))
        drawPickerThumb(
            center = Offset(
                x = (hue / MAX_HUE * size.width).coerceIn(thumbRadiusPx(), size.width - thumbRadiusPx()),
                y = size.height / 2f,
            ),
        )
    }
}

private fun DrawScope.drawPickerThumb(center: Offset) {
    drawCircle(color = Color.White, radius = thumbRadiusPx(), center = center)
    drawCircle(
        color = Color.Black,
        radius = thumbRadiusPx(),
        center = center,
        style = Stroke(width = COLOR_PICKER_THUMB_STROKE.toPx()),
    )
}

private fun DrawScope.thumbRadiusPx(): Float = COLOR_PICKER_THUMB_RADIUS.toPx()

private suspend fun PointerInputScope.detectPressAndDrag(onPosition: (Offset) -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        onPosition(down.position)
        drag(down.id) { change ->
            onPosition(change.position)
            change.consume()
        }
    }
}

@Composable
internal fun EditPhotoToolsRow(
    selectedTool: EditPhotoTool,
    addMenuOpen: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    enabled: Boolean,
    onToolSelected: (EditPhotoTool) -> Unit,
    onAddClick: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditPhotoToolButton(
            icon = ImageVector.vectorResource(R.drawable.ic_pen),
            label = stringResource(R.string.homecloud_editphoto_tool_pen),
            selected = selectedTool == EditPhotoTool.Pen,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Pen) },
        )
        EditPhotoToolButton(
            icon = ImageVector.vectorResource(R.drawable.ic_marker),
            label = stringResource(R.string.homecloud_editphoto_tool_marker),
            selected = selectedTool == EditPhotoTool.Marker,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Marker) },
        )
        EditPhotoToolButton(
            icon = Icons.Outlined.Edit,
            label = stringResource(R.string.homecloud_editphoto_tool_pencil),
            selected = selectedTool == EditPhotoTool.Pencil,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Pencil) },
        )
        EditPhotoToolButton(
            icon = ImageVector.vectorResource(R.drawable.ic_eraser),
            label = stringResource(R.string.homecloud_editphoto_tool_eraser),
            selected = selectedTool == EditPhotoTool.Eraser,
            enabled = enabled,
            onClick = { onToolSelected(EditPhotoTool.Eraser) },
        )
        EditPhotoToolButton(
            icon = Icons.Filled.Add,
            label = stringResource(R.string.homecloud_editphoto_tool_add),
            selected = addMenuOpen || selectedTool.isShapeTool,
            enabled = enabled,
            onClick = onAddClick,
        )
        Spacer(modifier = Modifier.weight(1f))
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
internal fun EditPhotoAddMenu(
    onItemSelected: (EditPhotoAddItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        EditPhotoAddMenuItem(
            icon = Icons.Filled.TextFields,
            label = stringResource(R.string.homecloud_editphoto_tool_text),
            onClick = { onItemSelected(EditPhotoAddItem.Text) },
            modifier = Modifier.fillMaxWidth(),
        )
        EditPhotoAddMenuItem(
            icon = Icons.Filled.EmojiEmotions,
            label = stringResource(R.string.homecloud_editphoto_tool_emoji),
            onClick = { onItemSelected(EditPhotoAddItem.Emoji) },
            modifier = Modifier.fillMaxWidth(),
        )
        EditPhotoAddMenuItem(
            icon = Icons.Filled.HorizontalRule,
            label = stringResource(R.string.homecloud_editphoto_shape_line),
            onClick = { onItemSelected(EditPhotoAddItem.Line) },
            modifier = Modifier.fillMaxWidth(),
        )
        EditPhotoAddMenuItem(
            icon = Icons.Outlined.Circle,
            label = stringResource(R.string.homecloud_editphoto_shape_oval),
            onClick = { onItemSelected(EditPhotoAddItem.Oval) },
            modifier = Modifier.fillMaxWidth(),
        )
        EditPhotoAddMenuItem(
            icon = Icons.Filled.CropSquare,
            label = stringResource(R.string.homecloud_editphoto_shape_rectangle),
            onClick = { onItemSelected(EditPhotoAddItem.Rectangle) },
            modifier = Modifier.fillMaxWidth(),
        )
        EditPhotoAddMenuItem(
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            label = stringResource(R.string.homecloud_editphoto_shape_arrow),
            onClick = { onItemSelected(EditPhotoAddItem.Arrow) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun EditPhotoAddMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .padding(dimensionResource(R.dimen.standard_half_margin)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
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
                addMenuOpen = false,
                canUndo = true,
                canRedo = false,
                enabled = true,
                onToolSelected = {},
                onAddClick = {},
                onUndo = {},
                onRedo = {},
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun EditPhotoAddMenuPreview() {
    HomeCloudTheme {
        Surface {
            EditPhotoAddMenu(
                onItemSelected = {},
            )
        }
    }
}

@HomeCloudPreview
@Composable
private fun EditPhotoCustomColorDialogPreview() {
    HomeCloudTheme {
        EditPhotoCustomColorDialog(
            initialColor = AndroidColor.RED,
            onConfirm = {},
            onDismiss = {},
        )
    }
}

internal const val PEN_DEFAULT_SIZE = 15f
internal const val PEN_DEFAULT_OPACITY = 255
internal const val MARKER_DEFAULT_SIZE = 50f
internal const val MARKER_DEFAULT_OPACITY = 80
internal const val PENCIL_DEFAULT_SIZE = 7f
internal const val PENCIL_DEFAULT_OPACITY = 255
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
private val CUSTOM_COLOR_DIALOG_CORNER_RADIUS = 16.dp
private val COLOR_FIELD_SIZE = 200.dp
private val COLOR_FIELD_CORNER_RADIUS = 8.dp
private val HUE_BAR_HEIGHT = 28.dp
private val COLOR_PICKER_THUMB_RADIUS = 10.dp
private val COLOR_PICKER_THUMB_STROKE = 2.dp
private const val MAX_HUE = 360f
private const val HSV_COMPONENTS = 3
private const val HUE_INDEX = 0
private const val SATURATION_INDEX = 1
private const val VALUE_INDEX = 2

private val HUE_GRADIENT_COLORS = listOf(
    Color.Red,
    Color.Yellow,
    Color.Green,
    Color.Cyan,
    Color.Blue,
    Color.Magenta,
    Color.Red,
)

private val CUSTOM_COLOR_RAINBOW = Brush.sweepGradient(HUE_GRADIENT_COLORS)
