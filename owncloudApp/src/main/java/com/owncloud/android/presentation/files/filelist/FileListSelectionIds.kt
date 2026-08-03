package com.owncloud.android.presentation.files.filelist

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Pure id-set operations for file-list multi-select.
 * Callers pass only selectable ids (footer / virtual rows excluded).
 */
object FileListSelectionIds {

    fun toggle(current: Set<Long>, fileId: Long): Set<Long> =
        if (fileId in current) current - fileId else current + fileId

    fun select(current: Set<Long>, fileId: Long): Set<Long> =
        if (fileId in current) current else current + fileId

    fun selectAll(fileIds: Collection<Long>): Set<Long> =
        fileIds.toSet()

    fun inverse(current: Set<Long>, fileIds: Collection<Long>): Set<Long> =
        fileIds.filterTo(mutableSetOf()) { it !in current }

    fun retain(current: Set<Long>, fileIds: Collection<Long>): Set<Long> =
        current.intersect(fileIds.toSet())
}

fun MutableStateFlow<Set<Long>>.toggleFileSelection(fileId: Long) {
    update { FileListSelectionIds.toggle(it, fileId) }
}

fun MutableStateFlow<Set<Long>>.selectFile(fileId: Long) {
    update { FileListSelectionIds.select(it, fileId) }
}

fun MutableStateFlow<Set<Long>>.clearFileSelection() {
    value = emptySet()
}

fun MutableStateFlow<Set<Long>>.selectAllFiles(fileIds: Collection<Long>) {
    value = FileListSelectionIds.selectAll(fileIds)
}

fun MutableStateFlow<Set<Long>>.inverseFileSelection(fileIds: Collection<Long>) {
    update { FileListSelectionIds.inverse(it, fileIds) }
}

fun MutableStateFlow<Set<Long>>.retainFileSelection(fileIds: Collection<Long>) {
    update { current ->
        val retained = FileListSelectionIds.retain(current, fileIds)
        if (retained != current) retained else current
    }
}
