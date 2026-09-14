package com.jackmarcus.backend.database

import org.jetbrains.exposed.sql.*

object Messages : Table("messages") {
    val id = integer("id").autoIncrement()
    val senderId = varchar("sender_id", 128).references(Users.id)
    val receiverId = varchar("receiver_id", 128).references(Users.id)
    val content = text("content")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}
