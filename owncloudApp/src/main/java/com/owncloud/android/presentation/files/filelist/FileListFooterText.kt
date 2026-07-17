package com.owncloud.android.presentation.files.filelist

import android.content.Context
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo

/**
 * Aggregated file/folder count label shown in the file-list footer.
 * Shared by [FileListAdapter] and Compose [com.owncloud.android.presentation.files.filelist.compose.FileList] hosts.
 */
object FileListFooterText {

    fun fromFiles(context: Context, list: List<OCFileWithSyncInfo>): String {
        var filesCount = 0
        var foldersCount = 0
        for (fileWithSyncInfo in list) {
            if (fileWithSyncInfo.file.isFolder) {
                foldersCount++
            } else if (!fileWithSyncInfo.file.isHidden) {
                filesCount++
            }
        }
        return fromCounts(context, filesCount, foldersCount)
    }

    fun fromCounts(context: Context, filesCount: Int, foldersCount: Int): String =
        when {
            filesCount <= 0 -> {
                when {
                    foldersCount <= 0 -> ""
                    foldersCount == 1 -> context.getString(R.string.file_list__footer__folder)
                    else -> context.getString(R.string.file_list__footer__folders, foldersCount)
                }
            }

            filesCount == 1 -> {
                when {
                    foldersCount <= 0 -> context.getString(R.string.file_list__footer__file)
                    foldersCount == 1 -> context.getString(R.string.file_list__footer__file_and_folder)
                    else -> context.getString(R.string.file_list__footer__file_and_folders, foldersCount)
                }
            }

            else -> {
                when {
                    foldersCount <= 0 -> context.getString(R.string.file_list__footer__files, filesCount)
                    foldersCount == 1 -> context.getString(R.string.file_list__footer__files_and_folder, filesCount)
                    else -> context.getString(
                        R.string.file_list__footer__files_and_folders,
                        filesCount,
                        foldersCount,
                    )
                }
            }
        }
}
