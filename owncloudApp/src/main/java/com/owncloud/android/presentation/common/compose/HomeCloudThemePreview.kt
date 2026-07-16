package com.owncloud.android.presentation.common.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
