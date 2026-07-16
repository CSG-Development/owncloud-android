package com.owncloud.android.presentation.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
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

@HomeCloudPreview
@Composable
private fun HomeCloudThemePreview() {
    HomeCloudTheme {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "HomeCloudTheme",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "onSurface / primary text",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = "error text sample",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                ColorSwatch(label = "primary", color = MaterialTheme.colorScheme.primary)
                ColorSwatch(label = "surface", color = MaterialTheme.colorScheme.surface)
                ColorSwatch(label = "outline", color = MaterialTheme.colorScheme.outline)
                ColorSwatch(label = "onSecondaryContainer", color = MaterialTheme.colorScheme.onSecondaryContainer)
                ColorSwatch(label = "error", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ColorSwatch(label: String, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
