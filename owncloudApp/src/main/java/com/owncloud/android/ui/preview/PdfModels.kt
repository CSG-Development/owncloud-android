package com.owncloud.android.ui.preview

import android.graphics.Bitmap

data class PdfPageUiModel(
    val pageIndex: Int,
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val content: PdfPageContent,
)

sealed class PdfPageContent {
    data object Loading : PdfPageContent()
    data class Rendered(val bitmap: Bitmap) : PdfPageContent()
    data object Failed : PdfPageContent()
}

sealed class PdfLoadState {
    data object Idle : PdfLoadState()
    data object Loading : PdfLoadState()
    data class Ready(val pageCount: Int, val targetWidthPx: Int) : PdfLoadState()
    data object Error : PdfLoadState()
}

data class PdfPageNavigationState(
    val currentPage: Int = 0,
    val pageCount: Int = 0,
    val canGoPrevious: Boolean = false,
    val canGoNext: Boolean = false,
)

enum class PdfZoomMode {
    FitWidth,
    FitPage,
    Custom,
}

data class PdfZoomState(
    val zoomScale: Float = 1f,
    val displayPercent: Int = 100,
    val mode: PdfZoomMode = PdfZoomMode.FitWidth,
    val canZoomIn: Boolean = true,
    val canZoomOut: Boolean = true,
)
