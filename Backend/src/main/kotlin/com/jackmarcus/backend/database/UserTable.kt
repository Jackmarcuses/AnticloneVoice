package com.jackmarcus.backend.database

import org.jetbrains.exposed.sql.*

object Users : Table("users") {
    val id = varchar("id", 128)
    val username = varchar("username", 64).uniqueIndex()
    val email = varchar("email", 128)
    val passwordHash = varchar("password_hash", 256)
    val avatarUrl = varchar("avatar_url", 512).default("")

    override val primaryKey = PrimaryKey(id)
}

object Contacts : Table("contacts") {
    val userId = varchar("user_id", 128).references(Users.id)
    val contactId = varchar("contact_id", 128).references(Users.id)

    override val primaryKey = PrimaryKey(userId, contactId)
}
