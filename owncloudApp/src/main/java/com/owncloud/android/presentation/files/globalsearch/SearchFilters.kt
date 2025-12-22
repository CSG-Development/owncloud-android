package com.owncloud.android.presentation.files.globalsearch

import com.owncloud.android.R

/**
 * Represents the type filter options for file search (multiselect).
 */
enum class TypeFilter(
    val id: String,
    val labelResId: Int,
    val iconResId: Int?,
    val mimePatterns: List<String>
) {
    FILE("file", R.string.homecloud_filter_type_file, R.drawable.ic_file, listOf()),
    FOLDER("folder", R.string.homecloud_filter_type_folder, R.drawable.ic_folder_outlined, listOf("DIR")),
    DOCUMENT("document", R.string.homecloud_filter_type_document, R.drawable.ic_document, listOf(
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml",
        "application/vnd.oasis.opendocument.text",
        "text/"
    )),
    SPREADSHEET("spreadsheet", R.string.homecloud_filter_type_spreadsheet, R.drawable.ic_spreadsheet, listOf(
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml",
        "application/vnd.oasis.opendocument.spreadsheet"
    )),
    PRESENTATION("presentation", R.string.homecloud_filter_type_presentation, R.drawable.ic_presentation, listOf(
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml",
        "application/vnd.oasis.opendocument.presentation"
    )),
    PDF("pdf", R.string.homecloud_filter_type_pdf, R.drawable.ic_pdf, listOf("application/pdf")),
    IMAGE("image", R.string.homecloud_filter_type_image, R.drawable.ic_image, listOf("image/")),
    VIDEO("video", R.string.homecloud_filter_type_video, R.drawable.ic_video, listOf("video/")),
    AUDIO("audio", R.string.homecloud_filter_type_audio, R.drawable.ic_audio, listOf("audio/")),
    ARCHIVE("archive", R.string.homecloud_filter_type_archive, R.drawable.ic_archive, listOf(
        "application/zip",
        "application/x-rar",
        "application/x-7z-compressed",
        "application/x-tar",
        "application/gzip"
    ));

    companion object {
        fun fromId(id: String): TypeFilter? = entries.find { it.id == id }

    }
}

/**
 * Represents the date filter options for file search (single select).
 */
enum class DateFilter(val id: String, val labelResId: Int, val iconResId: Int = R.drawable.ic_calendar) {
    ANY("any", R.string.homecloud_filter_date_any),
    TODAY("today", R.string.homecloud_filter_date_today),
    LAST_WEEK("week", R.string.homecloud_filter_date_week),
    LAST_MONTH("month", R.string.homecloud_filter_date_month),
    LAST_YEAR("year", R.string.homecloud_filter_date_year);

    fun getMinDate(): Long {
        val now = System.currentTimeMillis()
        return when (this) {
            ANY -> 0L
            TODAY -> now - DAY_IN_MILLIS
            LAST_WEEK -> now - (7 * DAY_IN_MILLIS)
            LAST_MONTH -> now - (30 * DAY_IN_MILLIS)
            LAST_YEAR -> now - (365 * DAY_IN_MILLIS)
        }
    }

    companion object {
        private const val DAY_IN_MILLIS = 24 * 60 * 60 * 1000L

        fun fromId(id: String): DateFilter = entries.find { it.id == id } ?: ANY

    }
}

/**
 * Represents the size filter options for file search (single select).
 */
enum class SizeFilter(val id: String, val labelResId: Int, val iconResId: Int? = null) {
    ANY("any", R.string.homecloud_filter_size_any, R.drawable.ic_any_size),
    GT_10MB("gt_10mb", R.string.homecloud_filter_size_gt_10mb),
    LT_10MB("lt_10mb", R.string.homecloud_filter_size_lt_10mb),
    LT_100MB("lt_100mb", R.string.homecloud_filter_size_lt_100mb),
    GT_100MB("gt_100mb", R.string.homecloud_filter_size_gt_100mb),
    LT_500MB("lt_500mb", R.string.homecloud_filter_size_lt_500mb),
    GT_500MB("gt_500mb", R.string.homecloud_filter_size_gt_500mb),
    LT_1GB("lt_1gb", R.string.homecloud_filter_size_lt_1gb),
    GT_1GB("gt_1gb", R.string.homecloud_filter_size_gt_1gb);

    fun getMinSize(): Long = when (this) {
        ANY -> 0L
        GT_10MB -> 10 * MB_IN_BYTES
        LT_10MB -> 0L
        LT_100MB -> 0L
        GT_100MB -> 100 * MB_IN_BYTES
        LT_500MB -> 0L
        GT_500MB -> 500 * MB_IN_BYTES
        LT_1GB -> 0L
        GT_1GB -> GB_IN_BYTES
    }

    fun getMaxSize(): Long = when (this) {
        ANY -> Long.MAX_VALUE
        GT_10MB -> Long.MAX_VALUE
        LT_10MB -> 10 * MB_IN_BYTES
        LT_100MB -> 100 * MB_IN_BYTES
        GT_100MB -> Long.MAX_VALUE
        LT_500MB -> 500 * MB_IN_BYTES
        GT_500MB -> Long.MAX_VALUE
        LT_1GB -> GB_IN_BYTES
        GT_1GB -> Long.MAX_VALUE
    }

    companion object {
        private const val MB_IN_BYTES = 1024 * 1024L
        private const val GB_IN_BYTES = 1024 * MB_IN_BYTES

        fun fromId(id: String): SizeFilter = entries.find { it.id == id } ?: ANY

    }
}

/**
 * Data class holding all active search filters.
 */
data class SearchFiltersState(
    val selectedTypeIds: Set<TypeFilter> = emptySet(),
    val dateFilter: DateFilter = DateFilter.ANY,
    val sizeFilter: SizeFilter = SizeFilter.ANY,
) {
    val hasActiveFilters: Boolean
        get() = selectedTypeIds.isNotEmpty() || dateFilter != DateFilter.ANY || sizeFilter != SizeFilter.ANY

    /**
     * Get combined MIME patterns for all selected types.
     */
    fun getMimePatterns(): List<String> {
        return selectedTypeIds.flatMap { it.mimePatterns }
    }
}
