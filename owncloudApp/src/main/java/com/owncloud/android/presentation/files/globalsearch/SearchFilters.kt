package com.owncloud.android.presentation.files.globalsearch

import com.owncloud.android.R

/**
 * Represents the type filter options for file search.
 */
enum class TypeFilter(val labelResId: Int, val mimePrefix: String) {
    ALL(R.string.filter_type_all, ""),
    IMAGES(R.string.filter_type_images, "image/"),
    VIDEOS(R.string.filter_type_videos, "video/"),
    AUDIO(R.string.filter_type_audio, "audio/"),
    DOCUMENTS(R.string.filter_type_documents, "application/"),
    FOLDERS(R.string.filter_type_folders, "DIR");

    companion object {
        fun fromOrdinal(ordinal: Int): TypeFilter = entries.getOrElse(ordinal) { ALL }
    }
}

/**
 * Represents the date filter options for file search.
 */
enum class DateFilter(val labelResId: Int) {
    ANY(R.string.filter_date_any),
    TODAY(R.string.filter_date_today),
    LAST_WEEK(R.string.filter_date_week),
    LAST_MONTH(R.string.filter_date_month),
    LAST_YEAR(R.string.filter_date_year);

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

        fun fromOrdinal(ordinal: Int): DateFilter = entries.getOrElse(ordinal) { ANY }
    }
}

/**
 * Represents the size filter options for file search.
 */
enum class SizeFilter(val labelResId: Int) {
    ANY(R.string.filter_size_any),
    SMALL(R.string.filter_size_small),      // < 1 MB
    MEDIUM(R.string.filter_size_medium),    // 1-100 MB
    LARGE(R.string.filter_size_large);      // > 100 MB

    fun getMinSize(): Long = when (this) {
        ANY -> 0L
        SMALL -> 0L
        MEDIUM -> MB_IN_BYTES
        LARGE -> 100 * MB_IN_BYTES
    }

    fun getMaxSize(): Long = when (this) {
        ANY -> Long.MAX_VALUE
        SMALL -> MB_IN_BYTES
        MEDIUM -> 100 * MB_IN_BYTES
        LARGE -> Long.MAX_VALUE
    }

    companion object {
        private const val MB_IN_BYTES = 1024 * 1024L

        fun fromOrdinal(ordinal: Int): SizeFilter = entries.getOrElse(ordinal) { ANY }
    }
}

/**
 * Data class holding all active search filters.
 */
data class SearchFiltersState(
    val typeFilter: TypeFilter = TypeFilter.ALL,
    val dateFilter: DateFilter = DateFilter.ANY,
    val sizeFilter: SizeFilter = SizeFilter.ANY,
) {
    val hasActiveFilters: Boolean
        get() = typeFilter != TypeFilter.ALL || dateFilter != DateFilter.ANY || sizeFilter != SizeFilter.ANY
}

