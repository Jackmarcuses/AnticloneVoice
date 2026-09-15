package com.jackmarcus.anti_clonevoice.data.repository

import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import com.jackmarcus.anti_clonevoice.data.remote.ContactsService
import com.jackmarcus.anti_clonevoice.data.remote.NetworkClient
import com.jackmarcus.anti_clonevoice.data.remote.models.*

class ContactsRepository(
    private val secureStorage: SecureStorage,
    private val injectedContactsService: ContactsService? = null
) {
    private val contactsService get() = injectedContactsService ?: NetworkClient.contactsService

    suspend fun getContacts(): Result<List<ContactResponse>> {
        val token = secureStorage.getToken() ?: return Result.failure(Exception("No token found"))
        return try {
            val response = contactsService.getContacts("Bearer $token")
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to fetch contacts"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addContact(contactId: String): Result<String> {
        val token = secureStorage.getToken() ?: return Result.failure(Exception("No token found"))
        return try {
            val response = contactsService.addContact("Bearer $token", AddContactRequest(contactId))
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "Contact added")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to add contact"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteContact(contactId: String): Result<String> {
        val token = secureStorage.getToken() ?: return Result.failure(Exception("No token found"))
        return try {
            val response = contactsService.deleteContact("Bearer $token", contactId)
            if (response.isSuccessful) {
                Result.success(response.body()?.message ?: "Contact removed")
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Failed to remove contact"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<ContactResponse>> {
        val token = secureStorage.getToken() ?: return Result.failure(Exception("No token found"))
        return try {
            val response = contactsService.searchUsers("Bearer $token", query)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception(response.errorBody()?.string() ?: "Search failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
