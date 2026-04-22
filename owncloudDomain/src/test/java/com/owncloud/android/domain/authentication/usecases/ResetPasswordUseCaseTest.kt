package com.owncloud.android.domain.authentication.usecases

import com.owncloud.android.domain.authentication.AuthenticationRepository
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify
import org.junit.Assert.assertTrue
import org.junit.Test

class ResetPasswordUseCaseTest {

    private val repository: AuthenticationRepository = spyk()
    private val useCase = ResetPasswordUseCase(repository)
    private val useCaseParams = ResetPasswordUseCase.Params(
        serverPath = "https://example.com",
        email = "user@example.com",
    )

    @Test
    fun `reset password - ko - invalid params`() {
        var invalidParams = useCaseParams.copy(email = "")
        var result = useCase(invalidParams)

        assertTrue(result.isError)
        assertTrue(result.getThrowableOrNull() is IllegalArgumentException)

        invalidParams = useCaseParams.copy(serverPath = "")
        result = useCase(invalidParams)

        assertTrue(result.isError)
        assertTrue(result.getThrowableOrNull() is IllegalArgumentException)

        verify(exactly = 0) { repository.resetPassword(any(), any()) }
    }

    @Test
    fun `reset password - ok`() {
        every { repository.resetPassword(useCaseParams.serverPath, useCaseParams.email) } returns Unit

        val result = useCase(useCaseParams)

        assertTrue(result.isSuccess)
        verify(exactly = 1) { repository.resetPassword(useCaseParams.serverPath, useCaseParams.email) }
    }

    @Test
    fun `reset password - ko - repository throws`() {
        every { repository.resetPassword(any(), any()) } throws RuntimeException("boom")

        val result = useCase(useCaseParams)

        assertTrue(result.isError)
        assertTrue(result.getThrowableOrNull() is RuntimeException)
        verify(exactly = 1) { repository.resetPassword(useCaseParams.serverPath, useCaseParams.email) }
    }
}
