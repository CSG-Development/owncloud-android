package com.owncloud.android.data.tags.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.owncloud.android.data.files.db.OCFileEntity

@Dao
interface TagDao {

    @Query("SELECT * FROM tags WHERE accountOwner = :accountOwner")
    fun getTagsForAccount(accountOwner: String): List<OCTagEntity>

    @Query("SELECT * FROM tags WHERE accountOwner = :accountOwner AND tagId = :serverTagId")
    fun getTagByServerTagId(accountOwner: String, serverTagId: String): OCTagEntity?

    @Query(
        "SELECT t.* FROM tags t " +
                "INNER JOIN file_tags ft ON t.id = ft.tagId " +
                "WHERE ft.fileId = :fileId"
    )
    fun getTagsForFile(fileId: Long): List<OCTagEntity>

    @Query(
        "SELECT f.* FROM files f " +
                "INNER JOIN file_tags ft ON f.id = ft.fileId " +
                "WHERE ft.tagId = :tagId"
    )
    fun getFilesByTag(tagId: Long): List<OCFileEntity>

    @Query("SELECT id FROM files WHERE remoteId IN (:remoteIds)")
    fun getFileLocalIdsByRemoteIds(remoteIds: List<String>): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun assignTagToFile(fileTag: OCFileTagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun assignTagsToFiles(fileTags: List<OCFileTagEntity>)

    @Query("DELETE FROM file_tags WHERE fileId = :fileId AND tagId = :tagId")
    fun removeTagFromFile(fileId: Long, tagId: Long)

    @Query("DELETE FROM file_tags WHERE tagId = :localTagId")
    fun removeAllFileAssociationsForTag(localTagId: Long)

    @Transaction
    fun replaceFileAssociationsForTag(localTagId: Long, fileLocalIds: List<Long>) {
        removeAllFileAssociationsForTag(localTagId)
        val fileTags = fileLocalIds.map { OCFileTagEntity(fileId = it, tagId = localTagId) }
        assignTagsToFiles(fileTags)
    }

    @Transaction
    fun replaceTagsForFile(fileLocalId: Long, tagLocalIds: List<Long>) {
        deleteAllTagsForFile(fileLocalId)
        val fileTags = tagLocalIds.map { OCFileTagEntity(fileId = fileLocalId, tagId = it) }
        assignTagsToFiles(fileTags)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTag(tag: OCTagEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTags(tags: List<OCTagEntity>)

    @Query("DELETE FROM tags WHERE accountOwner = :accountOwner AND tagId NOT IN (:remoteTagIds)")
    fun deleteStaleTagsForAccount(accountOwner: String, remoteTagIds: List<String>)

    @Query("DELETE FROM tags WHERE accountOwner = :accountOwner")
    fun deleteTagsForAccount(accountOwner: String)

    @Query("DELETE FROM tags WHERE accountOwner = :accountOwner AND tagId = :serverTagId")
    fun deleteTagByServerTagId(accountOwner: String, serverTagId: String)

    @Query(
        "UPDATE tags SET displayName = :displayName, userVisible = :userVisible, userAssignable = :userAssignable " +
                "WHERE accountOwner = :accountOwner AND tagId = :serverTagId"
    )
    fun updateTagByServerTagId(
        accountOwner: String,
        serverTagId: String,
        displayName: String?,
        userVisible: Boolean,
        userAssignable: Boolean,
    )

    @Query("DELETE FROM file_tags WHERE fileId = :fileId")
    fun deleteAllTagsForFile(fileId: Long)
}
