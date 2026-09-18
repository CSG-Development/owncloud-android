package com.owncloud.android.presentation.editphoto

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.owncloud.android.presentation.common.compose.HomeCloudPreview
import com.owncloud.android.presentation.common.compose.HomeCloudTheme

@Composable
fun EditPhotoScreen(
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier)
}

@HomeCloudPreview
@Composable
private fun EditPhotoScreenPreview() {
    HomeCloudTheme {
        Surface {
            EditPhotoScreen(modifier = Modifier.fillMaxSize())
        }
    }
}
