package com.owncloud.android.presentation.trash

import com.owncloud.android.domain.trash.model.HCTrashItem

data class TrashItemUi(
    val item: HCTrashItem,
    val isSelected: Boolean = false,
)

fun HCTrashItem.toTrashItemUi(isSelected: Boolean = false): TrashItemUi =
    TrashItemUi(item = this, isSelected = isSelected)
