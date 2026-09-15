package com.jackmarcus.backend.database

import org.jetbrains.exposed.sql.*

object Transcripts : Table("transcripts") {
    val id = integer("id").autoIncrement()
    val userId = varchar("user_id", 128).references(Users.id)
    val content = text("content")
    val language = varchar("language", 16).default("unknown")
    val timestamp = long("timestamp")

    override val primaryKey = PrimaryKey(id)
}
