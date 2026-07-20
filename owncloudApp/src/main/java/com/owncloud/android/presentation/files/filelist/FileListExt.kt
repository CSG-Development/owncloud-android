package com.owncloud.android.presentation.files.filelist

/**
 * True when [oldList] and [newList] contain the same multiset of items in a different order
 * (e.g. sort-only change). Progress-only updates that keep order and equality return false.
 */
fun <T> isOnlyListOrderChanged(oldList: List<T>, newList: List<T>): Boolean {
    if (oldList.size != newList.size) return false
    if (oldList === newList || oldList == newList) return false
    val oldFreq = oldList.groupingBy { it }.eachCount()
    val newFreq = newList.groupingBy { it }.eachCount()
    return oldFreq == newFreq
}
