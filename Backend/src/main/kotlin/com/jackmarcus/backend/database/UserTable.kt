package com.jackmarcus.backend.database

import org.jetbrains.exposed.sql.*

object Users : Table("users") {
    val id = varchar("id", 128)
    val username = varchar("username", 64).uniqueIndex()
    val email = varchar("email", 128)
    val passwordHash = varchar("password_hash", 256)
    val avatarUrl = varchar("avatar_url", 512).default("")
    val voiceEmbedding = text("voice_embedding").nullable()
    val baselineSpeechRate = float("baseline_speech_rate").default(0.0f)
    val pitchVariance = float("pitch_variance").default(0.0f)

    override val primaryKey = PrimaryKey(id)
}

object Contacts : Table("contacts") {
    val userId = varchar("user_id", 128).references(Users.id)
    val contactId = varchar("contact_id", 128).references(Users.id)

    override val primaryKey = PrimaryKey(userId, contactId)
}
