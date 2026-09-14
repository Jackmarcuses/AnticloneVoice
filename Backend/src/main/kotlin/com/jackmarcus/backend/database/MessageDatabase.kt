package com.jackmarcus.backend.database

import com.jackmarcus.backend.models.Message
import org.jetbrains.exposed.sql.*

object MessageDatabase {

    private fun resultRowToMessage(row: ResultRow) = Message(
        id = row[Messages.id],
        senderId = row[Messages.senderId],
        receiverId = row[Messages.receiverId],
        content = row[Messages.content],
        timestamp = row[Messages.timestamp]
    )

    suspend fun addMessage(message: Message) = DatabaseFactory.dbQuery {
        Messages.insert {
            it[senderId] = message.senderId
            it[receiverId] = message.receiverId
            it[content] = message.content
            it[timestamp] = message.timestamp
        } get Messages.id
    }

    suspend fun getMessagesBetween(user1: String, user2: String): List<Message> = DatabaseFactory.dbQuery {
        Messages.selectAll().where {
            ((Messages.senderId eq user1) and (Messages.receiverId eq user2)) or
            ((Messages.senderId eq user2) and (Messages.receiverId eq user1))
        }.orderBy(Messages.timestamp, SortOrder.ASC)
            .map(::resultRowToMessage)
    }
}
