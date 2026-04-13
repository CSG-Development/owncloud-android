package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.exceptions.UnauthorizedException
import com.owncloud.android.domain.files.FileRepository
import com.owncloud.android.domain.files.model.OCFile
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTagsAndFilesForAccountUseCaseTest {

    private val tagRepository: TagRepository = mockk()
    private val filesRepository: FileRepository = mockk()
    private val useCase = SyncTagsAndFilesForAccountUseCase(tagRepository, filesRepository)
    private val params = SyncTagsAndFilesForAccountUseCase.Params(accountName = "user@server")

    private val tagA = OCTag(id = "tag-1", displayName = "urgent", userVisible = true, userAssignable = true)
    private val tagB = OCTag(id = "tag-2", displayName = "reviewed", userVisible = true, userAssignable = false)

    @Test
    fun `sync - ok - refreshes tags and fetches files for each tag`() {
        every { tagRepository.refreshTagsForAccount("user@server") } returns listOf(tagA, tagB)
        every { tagRepository.refreshFilesByTag("user@server", "tag-1") } returns listOf("remote-file-1", "remote-file-2")
        every { tagRepository.refreshFilesByTag("user@server", "tag-2") } returns listOf("remote-file-3")
        every { filesRepository.getFileByRemoteId("remote-file-1") } returns mockk<OCFile>()
        every { filesRepository.getFileByRemoteId("remote-file-2") } returns null
        every { filesRepository.getFileByRemoteId("remote-file-3") } returns null

        val result = useCase(params)

        assertTrue(result.isSuccess)
        assertEquals(listOf(tagA, tagB), result.getDataOrNull())
        verify(exactly = 1) { tagRepository.refreshTagsForAccount("user@server") }
        verify(exactly = 1) { tagRepository.refreshFilesByTag("user@server", "tag-1") }
        verify(exactly = 1) { tagRepository.refreshFilesByTag("user@server", "tag-2") }
        verify(exactly = 1) { filesRepository.getFileByRemoteId("remote-file-1") }
        verify(exactly = 1) { filesRepository.getFileByRemoteId("remote-file-2") }
        verify(exactly = 1) { filesRepository.getFileByRemoteId("remote-file-3") }
    }

    @Test
    fun `sync - ok - returns tags even when one tag file fetch throws`() {
        every { tagRepository.refreshTagsForAccount("user@server") } returns listOf(tagA, tagB)
        every { tagRepository.refreshFilesByTag("user@server", "tag-1") } throws RuntimeException("network error")
        every { tagRepository.refreshFilesByTag("user@server", "tag-2") } returns listOf("remote-file-3")
        every { filesRepository.getFileByRemoteId("remote-file-3") } returns null

        val result = useCase(params)

        assertTrue(result.isSuccess)
        assertEquals(listOf(tagA, tagB), result.getDataOrNull())
        verify(exactly = 1) { tagRepository.refreshFilesByTag("user@server", "tag-1") }
        verify(exactly = 1) { tagRepository.refreshFilesByTag("user@server", "tag-2") }
        verify(exactly = 1) { filesRepository.getFileByRemoteId("remote-file-3") }
    }

    @Test
    fun `sync - ok - no file fetches when tag list is empty`() {
        every { tagRepository.refreshTagsForAccount("user@server") } returns emptyList()

        val result = useCase(params)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<OCTag>(), result.getDataOrNull())
        verify(exactly = 0) { tagRepository.refreshFilesByTag(any(), any()) }
        verify(exactly = 0) { filesRepository.getFileByRemoteId(any()) }
    }

    @Test
    fun `sync - ko - propagates exception when tag refresh throws`() {
        every { tagRepository.refreshTagsForAccount("user@server") } throws UnauthorizedException()

        val result = useCase(params)

        assertTrue(result.isError)
        assertTrue(result.getThrowableOrNull() is UnauthorizedException)
        verify(exactly = 0) { tagRepository.refreshFilesByTag(any(), any()) }
    }

    @Test
    fun `sync - ok - skips file sync for tag with null id but still returns tag in list`() {
        val tagWithNullId = OCTag(id = null, displayName = "no-id-tag", userVisible = true, userAssignable = true)
        every { tagRepository.refreshTagsForAccount("user@server") } returns listOf(tagWithNullId)

        val result = useCase(params)

        assertTrue(result.isSuccess)
        assertEquals(listOf(tagWithNullId), result.getDataOrNull())
        verify(exactly = 0) { tagRepository.refreshFilesByTag(any(), any()) }
        verify(exactly = 0) { filesRepository.getFileByRemoteId(any()) }
    }
}
