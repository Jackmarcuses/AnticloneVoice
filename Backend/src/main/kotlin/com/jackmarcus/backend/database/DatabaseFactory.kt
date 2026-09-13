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
        
        // On Render, we use the Environment Variables. Locally, we use the Supabase URL.
        val jdbcUrl = System.getenv("JDBC_DATABASE_URL") 
            ?: "jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres"
            
        val user = System.getenv("JDBC_DATABASE_USER") 
            ?: "postgres.dzpkgschzppvjonksvww"
            
        val password = System.getenv("JDBC_DATABASE_PASSWORD") 
            ?: "01ZA92YB34CD"

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
        
        // Supabase-specific requirements for Render
        addDataSourceProperty("ssl", "true")
        addDataSourceProperty("sslmode", "require")
        addDataSourceProperty("tcpKeepAlive", "true")
        
        connectionTimeout = 30000
        validate()
    })

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
