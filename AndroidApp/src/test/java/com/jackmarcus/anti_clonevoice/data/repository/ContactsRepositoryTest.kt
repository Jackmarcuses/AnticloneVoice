package com.jackmarcus.anti_clonevoice.data.repository

import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.ContactsService
import com.jackmarcus.anti_clonevoice.data.remote.models.ContactResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class ContactsRepositoryTest {

    private lateinit var repository: ContactsRepository
    private val service = mockk<ContactsService>()
    private val storage = mockk<SecureStorage>()

    @Before
    fun setup() {
        repository = ContactsRepository(storage, service)
        every { storage.getToken() } returns "fake_token"
    }

    @Test
    fun `getContacts returns success when service call succeeds`() = runBlocking {
        val mockContacts = listOf(ContactResponse("id1", "user1", true))
        coEvery { service.getContacts(any()) } returns Response.success(mockContacts)

        val result = repository.getContacts()

        assertTrue(result.isSuccess)
        assertEquals(mockContacts, result.getOrNull())
    }

    @Test
    fun `addContact returns success when service call succeeds`() = runBlocking {
        coEvery { service.addContact(any(), any()) } returns Response.success(mockk())

        val result = repository.addContact("id2")

        assertTrue(result.isSuccess)
    }
}
