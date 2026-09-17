package com.owncloud.android.presentation.common.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.owncloud.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeCloudSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = homeCloudSliderColors()
    val thumbColor = if (enabled) colors.active else colors.disabled
    val activeTrackColor = if (enabled) colors.active else colors.disabled
    val inactiveTrackColor = if (enabled) colors.inactive else colors.disabled

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = { sliderState ->
            HomeCloudSliderThumb(
                enabled = enabled,
                thumbColor = thumbColor,
            )
        },
        track = { sliderState ->
            HomeCloudSliderTrack(
                fraction = sliderFraction(sliderState.value, sliderState.valueRange),
                activeColor = activeTrackColor,
                inactiveColor = inactiveTrackColor,
            )
        },
    )
}

@Composable
private fun HomeCloudSliderThumb(
    enabled: Boolean,
    thumbColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(HANDLE_SIZE)
            .shadow(
                elevation = if (enabled) HANDLE_ELEVATION else 0.dp,
                shape = CircleShape,
                ambientColor = HANDLE_SHADOW_AMBIENT,
                spotColor = HANDLE_SHADOW_SPOT,
            )
            .background(thumbColor, CircleShape),
    )
}

@Composable
private fun HomeCloudSliderTrack(
    fraction: Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(TRACK_HEIGHT),
    ) {
        val strokeWidth = size.height
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val start = if (isRtl) Offset(size.width, center.y) else Offset(0f, center.y)
        val end = if (isRtl) Offset(0f, center.y) else Offset(size.width, center.y)
        drawLine(
            color = inactiveColor,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = activeColor,
            start = start,
            end = Offset(
                x = start.x + (end.x - start.x) * fraction,
                y = center.y,
            ),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun homeCloudSliderColors(): HomeCloudSliderColors {
    val active = colorResource(R.color.homecloud_link)
    return HomeCloudSliderColors(
        active = active,
        inactive = colorResource(R.color.homecloud_slider_inactive),
        disabled = colorResource(R.color.homecloud_content_grey),
        halo = active.copy(alpha = HANDLE_HALO_ALPHA),
        label = colorResource(R.color.homecloud_surface),
    )
}

private fun sliderFraction(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
): Float {
    val span = range.endInclusive - range.start
    if (span == 0f) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

private data class HomeCloudSliderColors(
    val active: Color,
    val inactive: Color,
    val disabled: Color,
    val halo: Color,
    val label: Color,
)

private data class HomeCloudSliderPreviewModel(
    val value: Float,
    val enabled: Boolean = true,
)

private class HomeCloudSliderPreviewParameterProvider :
    CollectionPreviewParameterProvider<HomeCloudSliderPreviewModel>(
        listOf(
            HomeCloudSliderPreviewModel(value = 0f),
            HomeCloudSliderPreviewModel(value = 70f),
            HomeCloudSliderPreviewModel(value = 40f, enabled = false),
        ),
    )

@HomeCloudPreview
@Composable
private fun HomeCloudSliderPreview(
    @PreviewParameter(HomeCloudSliderPreviewParameterProvider::class)
    model: HomeCloudSliderPreviewModel,
) {
    HomeCloudTheme {
        Surface(color = colorResource(R.color.homecloud_surface)) {
            HomeCloudSlider(
                value = model.value,
                onValueChange = {},
                enabled = model.enabled,
                valueRange = 0f..100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            )
        }
    }
}

private val HANDLE_SIZE = 20.dp
private val HANDLE_ELEVATION = 1.dp
private val TRACK_HEIGHT = 4.dp
private const val HANDLE_HALO_ALPHA = 0.2f
private val HANDLE_SHADOW_AMBIENT = Color.Black.copy(alpha = 0.15f)
private val HANDLE_SHADOW_SPOT = Color.Black.copy(alpha = 0.3f)
