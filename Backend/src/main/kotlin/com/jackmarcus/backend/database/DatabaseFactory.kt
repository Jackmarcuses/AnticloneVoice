package com.jackmarcus.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    private var dataSource: HikariDataSource? = null

    fun reset() {
        dataSource?.close()
        dataSource = null
    }

    fun init(isTest: Boolean = false) {
        if (dataSource != null) return

        if (isTest) {
            val config = HikariConfig().apply {
                driverClassName = "org.h2.Driver"
                jdbcUrl = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                maximumPoolSize = 1
                isAutoCommit = false
            }
            val ds = HikariDataSource(config)
            dataSource = ds
            val database = Database.connect(ds)
            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(Users, Contacts, Messages, Transcripts, VoiceVault)
            }
            return
        }

        // 1. Check for environment variable. If missing, use local H2 for development.
        val rawUrl = System.getenv("JDBC_DATABASE_URL")
        
        if (rawUrl == null) {
            println("JDBC_DATABASE_URL not found. Falling back to local H2 database...")
            val config = HikariConfig().apply {
                this.driverClassName = "org.h2.Driver"
                this.jdbcUrl = "jdbc:h2:file:./data/anticlone_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
                maximumPoolSize = 3
                isAutoCommit = false
            }
            val ds = HikariDataSource(config)
            dataSource = ds
            val database = Database.connect(ds)
            transaction(database) {
                SchemaUtils.createMissingTablesAndColumns(Users, Contacts, Messages, Transcripts, VoiceVault)
            }
            return
        }

        val driverClassName = "org.postgresql.Driver"

        // 2. Parse credentials and host from the URL if it's in the format postgresql://user:pass@host:port/db
        var user = System.getenv("JDBC_DATABASE_USER") ?: "postgres"
        var password = System.getenv("JDBC_DATABASE_PASSWORD") ?: "01ZA92YB34CD"
        
        var cleanUrl = rawUrl
        if (rawUrl.contains("@")) {
            val parts = rawUrl.split("@")
            if (parts.size == 2) {
                val credentialsPart = parts[0].substringAfter("://")
                val credentials = credentialsPart.split(":")
                if (credentials.size == 2) {
                    user = credentials[0]
                    password = credentials[1]
                }
                cleanUrl = rawUrl.substringBefore("://") + "://" + parts[1]
            }
        }

        // 3. FORCE the "jdbc:postgresql://" prefix. The driver CRASHES without this.
        val jdbcUrl = when {
            cleanUrl.startsWith("jdbc:postgresql://") -> cleanUrl
            cleanUrl.startsWith("postgresql://") -> cleanUrl.replace("postgresql://", "jdbc:postgresql://")
            cleanUrl.startsWith("postgres://") -> cleanUrl.replace("postgres://", "jdbc:postgresql://")
            else -> "jdbc:postgresql://$cleanUrl"
        }

        println("Connecting to database: $jdbcUrl")
        
        val ds = createHikariDataSource(jdbcUrl, driverClassName, user, password)
        dataSource = ds
        val database = Database.connect(ds)
        
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Users, Contacts, Messages, Transcripts, VoiceVault)
        }
    }

    private fun createHikariDataSource(
        url: String,
        driver: String,
        user: String,
        pass: String
    ) = HikariDataSource(HikariConfig().apply {
        driverClassName = driver
        jdbcUrl = url
        username = user
        password = pass
        maximumPoolSize = 3
        isAutoCommit = false
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        
        // Fix for Render -> Supabase connection
        addDataSourceProperty("ssl", "true")
        addDataSourceProperty("sslmode", "require")
        addDataSourceProperty("prepareThreshold", "0")
        
        connectionTimeout = 30000
        validate()
    })

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
