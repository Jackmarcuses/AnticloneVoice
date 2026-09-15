package com.jackmarcus.backend.database

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object TranscriptDatabase {
    fun saveTranscript(userId: String, content: String, language: String) {
        transaction {
            Transcripts.insert {
                it[Transcripts.userId] = userId
                it[Transcripts.content] = content
                it[Transcripts.language] = language
                it[Transcripts.timestamp] = System.currentTimeMillis()
            }
        }
    }
}
