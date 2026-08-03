package com.owncloud.android.presentation.common.compose

import android.content.res.Configuration
import androidx.compose.ui.tooling.preview.Preview

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
@Preview(name = "Light", showBackground = true)
@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
annotation class HomeCloudPreview

