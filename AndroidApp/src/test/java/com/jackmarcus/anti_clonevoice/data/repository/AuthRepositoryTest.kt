package com.jackmarcus.anti_clonevoice.data.repository

import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.AuthService
import com.jackmarcus.anti_clonevoice.data.remote.models.*
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class AuthRepositoryTest {
    private val authService = mockk<AuthService>()
    private val secureStorage = mockk<SecureStorage>(relaxed = true)
    private val repository = AuthRepository(secureStorage, authService)

    @Test
    fun `login success stores token`() = runTest {
        val request = LoginRequest("user", "pass")
        val response = AuthResponse("token123", "id1")
        coEvery { authService.login(request) } returns Response.success(response)

        val result = repository.login(request)

        assertTrue(result.isSuccess)
        assertEquals(response, result.getOrNull())
        verify { secureStorage.saveToken("token123") }
    }

    @Test
    fun `login failure returns error`() = runTest {
        val request = LoginRequest("user", "pass")
        coEvery { authService.login(request) } returns Response.error(401, mockk(relaxed = true))

        val result = repository.login(request)

        assertTrue(result.isFailure)
        verify(exactly = 0) { secureStorage.saveToken(any()) }
    }
}
