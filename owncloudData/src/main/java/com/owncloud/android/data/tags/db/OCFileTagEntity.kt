package com.owncloud.android.data.tags.db

import androidx.room.Entity
import androidx.room.ForeignKey
import com.owncloud.android.data.ProviderMeta.ProviderTableMeta.FILE_TAGS_TABLE_NAME
import com.owncloud.android.data.files.db.OCFileEntity

@Entity(
    tableName = FILE_TAGS_TABLE_NAME,
    primaryKeys = ["fileId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = OCFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["fileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = OCTagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class OCFileTagEntity(
    val fileId: Long,
    val tagId: Long,
)
