package com.owncloud.android.presentation.common.compose

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.CollectionPreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.owncloud.android.R

enum class HomeCloudBannerStyle {
    INFO,
    ERROR,
}

data class HomeCloudBannerUiModel(
    @field:StringRes val messageRes: Int,
    val style: HomeCloudBannerStyle,
    @field:StringRes val actionLabelRes: Int? = null,
    val contentKey: Any = listOf(messageRes, style, actionLabelRes),
)

@Composable
fun HomeCloudBanner(
    message: String,
    style: HomeCloudBannerStyle,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = bannerColors(style)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(BANNER_CORNER_RADIUS),
        color = colors.background,
        border = BorderStroke(BANNER_BORDER_WIDTH, colors.border),
        shadowElevation = BANNER_ELEVATION,
    ) {
        Row(
            modifier = Modifier.padding(dimensionResource(R.dimen.standard_margin)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.standard_half_margin)),
        ) {
            HomeCloudBannerMessage(
                message = message,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                HomeCloudBannerAction(
                    label = actionLabel,
                    color = colors.action,
                    onClick = onAction,
                )
            }
            if (onDismiss != null) {
                HomeCloudBannerClose(
                    tint = colors.text,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
fun HomeCloudBanner(
    model: HomeCloudBannerUiModel?,
    modifier: Modifier = Modifier,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val onActionState = rememberUpdatedState(onAction)
    val onDismissState = rememberUpdatedState(onDismiss)

    AnimatedContent(
        targetState = model,
        modifier = modifier,
        transitionSpec = {
            fadeIn(animationSpec = tween(BANNER_FADE_DURATION_MS)) togetherWith
                fadeOut(animationSpec = tween(BANNER_FADE_DURATION_MS)) using null
        },
        contentKey = { it?.contentKey },
        label = "HomeCloudBanner",
    ) { target ->
        if (target != null) {
            HomeCloudBanner(
                message = stringResource(target.messageRes),
                style = target.style,
                actionLabel = target.actionLabelRes?.let { stringResource(it) },
                onAction = onActionState.value.takeIf { target.actionLabelRes != null },
                onDismiss = onDismissState.value,
            )
        }
    }
}

@Composable
private fun HomeCloudBannerMessage(
    message: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = message,
        modifier = modifier,
        color = color,
        fontSize = 14.sp,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HomeCloudBannerAction(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun HomeCloudBannerClose(
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(24.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_close),
            contentDescription = stringResource(R.string.homecloud_close),
            tint = tint,
        )
    }
}

@Composable
private fun bannerColors(style: HomeCloudBannerStyle): BannerColors =
    when (style) {
        HomeCloudBannerStyle.ERROR -> BannerColors(
            background = colorResource(R.color.homecloud_banner_error_background),
            border = colorResource(R.color.homecloud_banner_error_border),
            text = colorResource(R.color.homecloud_banner_error_text),
            action = colorResource(R.color.homecloud_banner_error_action),
        )
        HomeCloudBannerStyle.INFO -> BannerColors(
            background = colorResource(R.color.homecloud_banner_info_background),
            border = colorResource(R.color.homecloud_banner_info_border),
            text = colorResource(R.color.homecloud_banner_info_text),
            action = colorResource(R.color.homecloud_banner_info_action),
        )
    }

private data class BannerColors(
    val background: Color,
    val border: Color,
    val text: Color,
    val action: Color,
)

private class HomeCloudBannerPreviewParameterProvider : CollectionPreviewParameterProvider<HomeCloudBannerUiModel>(
    listOf(
        HomeCloudBannerUiModel(
            messageRes = R.string.homecloud_filelist_extract_error_corrupt,
            style = HomeCloudBannerStyle.ERROR,
        ),
        HomeCloudBannerUiModel(
            messageRes = R.string.homecloud_filelist_extract_error_insufficient_storage,
            style = HomeCloudBannerStyle.ERROR,
            actionLabelRes = R.string.homecloud_retry,
        ),
        HomeCloudBannerUiModel(
            messageRes = R.string.homecloud_filelist_compress_error_generic,
            style = HomeCloudBannerStyle.INFO,
        ),
        HomeCloudBannerUiModel(
            messageRes = R.string.homecloud_filelist_compress_error_network_timeout,
            style = HomeCloudBannerStyle.INFO,
            actionLabelRes = R.string.homecloud_retry,
        ),
    ),
)

private val BANNER_CORNER_RADIUS = 4.dp
private val BANNER_BORDER_WIDTH = 1.dp
private val BANNER_ELEVATION = 4.dp
private const val BANNER_FADE_DURATION_MS = 250

@HomeCloudPreview
@Composable
private fun HomeCloudBannerPreview(
    @PreviewParameter(HomeCloudBannerPreviewParameterProvider::class)
    model: HomeCloudBannerUiModel,
) {
    HomeCloudTheme {
        Surface(
            modifier = Modifier.padding(16.dp),
            color = colorResource(R.color.homecloud_surface),
        ) {
            HomeCloudBanner(
                model = model,
                onAction = {}.takeIf { model.actionLabelRes != null },
                onDismiss = {},
            )
        }
    }
}
