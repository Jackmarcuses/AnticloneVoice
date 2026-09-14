package com.jackmarcus.anti_clonevoice.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jackmarcus.anti_clonevoice.data.repository.ChatRepository
import com.jackmarcus.anti_clonevoice.data.remote.models.Message
import com.jackmarcus.anti_clonevoice.data.local.SecureStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private var currentContactId: String? = null
    val currentUserId = secureStorage.getUserId() ?: ""

    init {
        viewModelScope.launch {
            chatRepository.messages.collect { newMessage ->
                // Filter messages for current chat
                if (newMessage.senderId == currentContactId || newMessage.receiverId == currentContactId) {
                    _messages.update { it + newMessage }
                }
            }
        }
        
        // Connect to WebSocket
        if (currentUserId.isNotEmpty()) {
            chatRepository.connect(currentUserId)
        }
    }

    fun loadMessages(contactId: String) {
        currentContactId = contactId
        viewModelScope.launch {
            val history = chatRepository.getMessages(contactId)
            _messages.value = history
        }
    }

    fun sendMessage(content: String) {
        val contactId = currentContactId ?: return
        if (content.isBlank()) return
        chatRepository.sendMessage(contactId, content)
    }

    override fun onCleared() {
        super.onCleared()
        chatRepository.disconnect()
    }
}
