package com.owncloud.android.data.tags.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.owncloud.android.data.ProviderMeta.ProviderTableMeta.TAGS_TABLE_NAME

@Entity(
    tableName = TAGS_TABLE_NAME,
    indices = [
        Index(
            value = ["accountOwner", "tagId"],
            unique = true
        )
    ]
)
data class OCTagEntity(
    val tagId: String,
    val accountOwner: String,
    val displayName: String?,
    val userVisible: Boolean,
    val userAssignable: Boolean,
) {
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0
}
