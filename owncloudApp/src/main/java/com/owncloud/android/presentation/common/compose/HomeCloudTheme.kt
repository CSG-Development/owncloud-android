package com.owncloud.android.presentation.common.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource
import com.owncloud.android.R

/**
 * Compose Material3 theme aligned with XML [R.style.Theme_homeCloud].
 * Colors are resolved from resources so DayNight (`values` / `values-night`) stays in sync.
 */
@Composable
fun HomeCloudTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = homeCloudColorScheme(darkTheme = darkTheme),
        content = content,
    )
}

@Composable
fun homeCloudColorScheme(darkTheme: Boolean = isSystemInDarkTheme()): ColorScheme {
    val primary = colorResource(R.color.homecloud_primary)
    val secondary = colorResource(R.color.homecloud_secondary)
    val surface = colorResource(R.color.homecloud_surface)
    val error = colorResource(R.color.homecloud_error)
    val outline = colorResource(R.color.homecloud_color_outline)
    val onSecondaryContainer = colorResource(R.color.homecloud_color_on_secondary_container)
    // Matches Theme.homeCloud: colorOnPrimary = ?attr/colorSurface
    val onPrimary = surface
    // Matches Theme.homeCloud: colorOnSurface / colorOnSurfaceVariant = ?attr/colorPrimary
    val onSurface = primary
    val secondaryContainer = colorResource(android.R.color.white)

    return if (darkTheme) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            surface = surface,
            onSurface = onSurface,
            onSurfaceVariant = onSurface,
            error = error,
            outline = outline,
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            secondary = secondary,
            onSecondary = onPrimary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            surface = surface,
            onSurface = onSurface,
            onSurfaceVariant = onSurface,
            error = error,
            outline = outline,
        )
    }
}
