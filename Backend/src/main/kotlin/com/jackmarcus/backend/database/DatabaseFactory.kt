package com.jackmarcus.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"
        
        // 1. Get the URL from Render, or use the Supabase Pooler as default
        var rawUrl = System.getenv("JDBC_DATABASE_URL") 
            ?: "postgresql://postgres.dzpkgschzppvjonksvww:01ZA92YB34CD@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres"
        
        // 2. FORCE the "jdbc:postgresql://" prefix. The driver CRASHES without this.
        val jdbcUrl = if (rawUrl.startsWith("jdbc:postgresql://")) {
            rawUrl
        } else if (rawUrl.startsWith("postgresql://")) {
            rawUrl.replace("postgresql://", "jdbc:postgresql://")
        } else {
            "jdbc:postgresql://$rawUrl"
        }

        // 3. Get credentials from environment or use defaults
        val user = System.getenv("JDBC_DATABASE_USER") ?: "postgres.dzpkgschzppvjonksvww"
        val password = System.getenv("JDBC_DATABASE_PASSWORD") ?: "01ZA92YB34CD"

        println("Connecting to database: $jdbcUrl")
        
        val database = Database.connect(createHikariDataSource(jdbcUrl, driverClassName, user, password))
        
        transaction(database) {
            SchemaUtils.create(Users, Contacts)
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
        
        connectionTimeout = 30000
        validate()
    })

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
