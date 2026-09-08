package com.owncloud.android.presentation.common

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.owncloud.android.domain.appregistry.model.AppRegistryProvider
import com.owncloud.android.domain.files.model.FileMenuOption
import com.owncloud.android.presentation.common.compose.DEFAULT_SELECTION_TOOLBAR_OPTIONS
import com.owncloud.android.presentation.common.compose.FileListSelectionMoreBottomSheet
import com.owncloud.android.presentation.common.compose.HomeCloudTheme
import com.owncloud.android.presentation.common.compose.PREVIEW_FILE_ACTIONS_TOOLBAR_OPTIONS

object FileListSelectionMoreBottomSheetHelper {

    fun show(
        context: Context,
        menuOptions: List<FileMenuOption>,
        hasWritePermission: Boolean,
        toolbarOptions: Set<FileMenuOption> = DEFAULT_SELECTION_TOOLBAR_OPTIONS,
        openInWebProviders: List<AppRegistryProvider> = emptyList(),
        onAction: (menuId: Int) -> Unit,
        onOpenInWeb: (providerName: String) -> Unit = {},
    ) {
        val dialog = BottomSheetDialog(context)
        val composeView = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                HomeCloudTheme {
                    FileListSelectionMoreBottomSheet(
                        menuOptions = menuOptions,
                        hasWritePermission = hasWritePermission,
                        toolbarOptions = toolbarOptions,
                        onAction = { menuId ->
                            dialog.dismiss()
                            onAction(menuId)
                        },
                        openInWebProviders = openInWebProviders,
                        onOpenInWeb = { providerName ->
                            dialog.dismiss()
                            onOpenInWeb(providerName)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.setOnShowListener {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
    }

    fun showForPreview(
        context: Context,
        menuOptions: List<FileMenuOption>,
        hasWritePermission: Boolean,
        openInWebProviders: List<AppRegistryProvider> = emptyList(),
        onAction: (menuId: Int) -> Unit,
        onOpenInWeb: (providerName: String) -> Unit = {},
    ) = show(
        context = context,
        menuOptions = menuOptions,
        hasWritePermission = hasWritePermission,
        toolbarOptions = PREVIEW_FILE_ACTIONS_TOOLBAR_OPTIONS,
        openInWebProviders = openInWebProviders,
        onAction = onAction,
        onOpenInWeb = onOpenInWeb,
    )
}
