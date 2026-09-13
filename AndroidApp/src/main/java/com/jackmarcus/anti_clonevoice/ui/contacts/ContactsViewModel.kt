package com.jackmarcus.anti_clonevoice.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jackmarcus.anti_clonevoice.data.remote.PresenceManager
import com.jackmarcus.anti_clonevoice.data.remote.models.ContactResponse
import com.jackmarcus.anti_clonevoice.data.repository.ContactsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ContactsUiState(
    val contacts: List<ContactResponse> = emptyList(),
    val searchResults: List<ContactResponse> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class ContactsViewModel(
    private val contactsRepository: ContactsRepository,
    private val presenceManager: PresenceManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    init {
        loadContacts()
        observePresence()
    }

    fun loadContacts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            contactsRepository.getContacts()
                .onSuccess { contacts ->
                    _uiState.update { it.copy(contacts = contacts, isLoading = false, error = null) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    private fun observePresence() {
        presenceManager.connect()
        viewModelScope.launch {
            presenceManager.presenceUpdates.collect { update ->
                _uiState.update { state ->
                    val updatedContacts = state.contacts.map { contact ->
                        if (contact.userId == update.userId) {
                            contact.copy(isOnline = update.status == "online")
                        } else {
                            contact
                        }
                    }
                    // Sort online contacts to the top for a better UX
                    state.copy(contacts = updatedContacts.sortedByDescending { it.isOnline })
                }
            }
        }
    }

    fun addContact(contactId: String) {
        viewModelScope.launch {
            contactsRepository.addContact(contactId)
                .onSuccess { 
                    loadContacts()
                    _uiState.update { it.copy(searchResults = emptyList()) }
                }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    fun searchUsers(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        viewModelScope.launch {
            contactsRepository.searchUsers(query)
                .onSuccess { results ->
                    _uiState.update { it.copy(searchResults = results) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            contactsRepository.deleteContact(contactId)
                .onSuccess { loadContacts() }
                .onFailure { e -> _uiState.update { it.copy(error = e.message) } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        presenceManager.disconnect()
    }
}
