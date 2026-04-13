package com.owncloud.android.domain.tags.usecases

import com.owncloud.android.domain.exceptions.UnauthorizedException
import com.owncloud.android.domain.tags.TagRepository
import com.owncloud.android.domain.tags.model.OCTag
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RefreshTagsForFileUseCaseTest {

    private val repository: TagRepository = mockk()
    private val useCase = RefreshTagsForFileUseCase(repository)
    private val params = RefreshTagsForFileUseCase.Params(
        accountName = "user@server",
        fileRemoteId = 12345L,
        fileLocalId = 1L,
    )

    @Test
    fun `refresh tags for file - ok - returns tags from repository`() {
        val expectedTags = listOf(
            OCTag(id = "1", displayName = "urgent", userVisible = true, userAssignable = true),
            OCTag(id = "2", displayName = "reviewed", userVisible = true, userAssignable = false),
        )
        every { repository.refreshTagsForFile(any(), any(), any()) } returns expectedTags

        val result = useCase(params)

        assertTrue(result.isSuccess)
        assertEquals(expectedTags, result.getDataOrNull())
        verify(exactly = 1) {
            repository.refreshTagsForFile("user@server", 12345L, 1L)
        }
    }

    @Test
    fun `refresh tags for file - ok - empty list when file has no tags`() {
        every { repository.refreshTagsForFile(any(), any(), any()) } returns emptyList()

        val result = useCase(params)

        assertTrue(result.isSuccess)
        assertEquals(emptyList<OCTag>(), result.getDataOrNull())
        verify(exactly = 1) {
            repository.refreshTagsForFile("user@server", 12345L, 1L)
        }
    }

    @Test
    fun `refresh tags for file - ko - propagates exception from repository`() {
        every { repository.refreshTagsForFile(any(), any(), any()) } throws UnauthorizedException()

        val result = useCase(params)

        assertTrue(result.isError)
        assertTrue(result.getThrowableOrNull() is UnauthorizedException)
        verify(exactly = 1) {
            repository.refreshTagsForFile("user@server", 12345L, 1L)
        }
    }
}
