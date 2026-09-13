package com.jackmarcus.backend.database

import com.jackmarcus.backend.models.User
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.concurrent.ConcurrentHashMap

object UserDatabase {
    private val onlineUsers = ConcurrentHashMap.newKeySet<String>()
    private val tempOtps = ConcurrentHashMap<String, String>() // email -> code

    private fun resultRowToUser(row: ResultRow) = User(
        id = row[Users.id],
        username = row[Users.username],
        passwordHash = row[Users.passwordHash],
        email = row[Users.email],
        avatarUrl = row[Users.avatarUrl],
        contacts = emptyList() // Contacts are loaded separately via getContacts
    )

    fun saveOtp(email: String, code: String) {
        tempOtps[email] = code
    }

    fun getOtp(email: String): String? = tempOtps[email]

    fun removeOtp(email: String) {
        tempOtps.remove(email)
    }

    suspend fun addUser(user: User) = DatabaseFactory.dbQuery {
        Users.insert {
            it[id] = user.id
            it[username] = user.username
            it[email] = user.email
            it[passwordHash] = user.passwordHash
            it[avatarUrl] = user.avatarUrl
        }
    }

    suspend fun getUser(username: String): User? = DatabaseFactory.dbQuery {
        Users.select { Users.username eq username }
            .map(::resultRowToUser)
            .singleOrNull()
    }

    suspend fun getUserById(id: String): User? = DatabaseFactory.dbQuery {
        Users.select { Users.id eq id }
            .map(::resultRowToUser)
            .singleOrNull()
    }

    suspend fun addContact(userId: String, contactId: String): Boolean = DatabaseFactory.dbQuery {
        val alreadyExists = Contacts.select { (Contacts.userId eq userId) and (Contacts.contactId eq contactId) }.any()
        if (alreadyExists) return@dbQuery false
        
        Contacts.insert {
            it[this.userId] = userId
            it[this.contactId] = contactId
        }
        true
    }

    suspend fun removeContact(userId: String, contactId: String): Boolean = DatabaseFactory.dbQuery {
        Contacts.deleteWhere { (Contacts.userId eq userId) and (Contacts.contactId eq contactId) } > 0
    }

    suspend fun getContacts(userId: String): List<User> = DatabaseFactory.dbQuery {
        val contactIds = Contacts.select { Contacts.userId eq userId }.map { it[Contacts.contactId] }
        Users.select { Users.id inList contactIds }
            .map(::resultRowToUser)
    }

    suspend fun searchUsers(query: String): List<User> = DatabaseFactory.dbQuery {
        Users.select { Users.username.lowerCase() like "%${query.lowercase()}%" }
            .map(::resultRowToUser)
    }

    suspend fun updateUser(userId: String, username: String?, avatarUrl: String?): Boolean = DatabaseFactory.dbQuery {
        Users.update({ Users.id eq userId }) {
            if (username != null) it[Users.username] = username
            if (avatarUrl != null) it[Users.avatarUrl] = avatarUrl
        } > 0
    }

    fun setUserOnline(userId: String) {
        onlineUsers.add(userId)
    }

    fun setUserOffline(userId: String) {
        onlineUsers.remove(userId)
    }

    fun isOnline(userId: String): Boolean {
        return onlineUsers.contains(userId)
    }
}
