package com.owncloud.android.presentation.files.filelist

import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import com.owncloud.android.R
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.files.model.OCFileSyncInfo
import com.owncloud.android.domain.files.model.OCFileWithSyncInfo

/**
 * Shared ActionMode glue for file-list multi-select (Main / Favorites / Search).
 * Screen-specific chrome and menu filtering stay in [Host].
 */
class FileListActionModeController(
    private val host: Host,
) {

    interface Host {
        fun requireAppCompatActivity(): AppCompatActivity
        fun getCheckedItems(): List<OCFileWithSyncInfo>
        fun clearSelection()
        fun onActionItemClicked(itemId: Int?): Boolean
        /**
         * After title/checkedFiles are updated. Host should filter menu options
         * and apply any screen-specific prepare logic.
         */
        fun onPrepareMultiSelect(checkedItems: List<OCFileWithSyncInfo>, menu: Menu?)
        fun onEnterMultiSelect()
        fun onExitMultiSelect()
    }

    var actionMode: ActionMode? = null
        private set

    var menu: Menu? = null
        private set

    /** Last prepared selection as [OCFile]s (for menu observers). */
    var checkedFiles: List<OCFile> = emptyList()
        private set

    private var statusBarColorBeforeMode: Int? = null

    val isActive: Boolean
        get() = actionMode != null

    val callback: ActionMode.Callback = object : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            actionMode = mode
            val activity = host.requireAppCompatActivity()
            activity.menuInflater.inflate(R.menu.file_actions_menu, menu)
            this@FileListActionModeController.menu = menu
            mode?.invalidate()
            statusBarColorBeforeMode = activity.window.statusBarColor
            host.onEnterMultiSelect()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            val checkedItems = host.getCheckedItems()
            val checkedCount = checkedItems.size
            mode?.title = host.requireAppCompatActivity().resources.getQuantityString(
                R.plurals.items_selected_count,
                checkedCount,
                checkedCount,
            )
            checkedFiles = checkedItems.map { it.file }
            host.onPrepareMultiSelect(checkedItems, menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean =
            host.onActionItemClicked(item?.itemId)

        override fun onDestroyActionMode(mode: ActionMode?) {
            actionMode = null
            statusBarColorBeforeMode?.let { color ->
                host.requireAppCompatActivity().window.statusBarColor = color
            }
            statusBarColorBeforeMode = null
            host.onExitMultiSelect()
            host.clearSelection()
            this@FileListActionModeController.menu = null
            checkedFiles = emptyList()
        }
    }

    /** Start ActionMode if needed, finish when selection is empty, otherwise invalidate. */
    fun syncWithSelectionCount(selectedCount: Int) {
        if (selectedCount == 0) {
            finish()
        } else {
            startIfNeeded()
            actionMode?.invalidate()
        }
    }

    fun startIfNeeded() {
        if (actionMode == null) {
            actionMode = host.requireAppCompatActivity().startSupportActionMode(callback)
        }
    }

    fun finish() {
        actionMode?.finish()
    }

    fun invalidate() {
        actionMode?.invalidate()
    }

    companion object {
        fun toSyncInfoList(checkedItems: List<OCFileWithSyncInfo>): List<OCFileSyncInfo> =
            checkedItems.map {
                OCFileSyncInfo(
                    fileId = it.file.id!!,
                    uploadWorkerUuid = it.uploadWorkerUuid,
                    downloadWorkerUuid = it.downloadWorkerUuid,
                    isSynchronizing = it.isSynchronizing,
                )
            }
    }
}
