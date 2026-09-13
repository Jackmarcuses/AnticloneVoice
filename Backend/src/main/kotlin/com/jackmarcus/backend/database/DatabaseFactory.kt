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
        var jdbcUrl = System.getenv("JDBC_DATABASE_URL") 
            ?: "jdbc:postgresql://db.dzpkgschzppvjonksvww.supabase.co:5432/postgres"
        
        // Auto-fix the URL if it's missing the jdbc: prefix or has the wrong protocol
        if (!jdbcUrl.startsWith("jdbc:")) {
            jdbcUrl = if (jdbcUrl.startsWith("postgresql://")) {
                "jdbc:$jdbcUrl"
            } else if (jdbcUrl.startsWith("postgres://")) {
                jdbcUrl.replace("postgres://", "jdbc:postgresql://")
            } else {
                "jdbc:postgresql://$jdbcUrl"
            }
        }

        val user = System.getenv("JDBC_DATABASE_USER") ?: "postgres"
        val password = System.getenv("JDBC_DATABASE_PASSWORD") ?: "01ZA92YB34CD"

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
        // Add these lines for Supabase stability
        addDataSourceProperty("tcpKeepAlive", "true")
        connectionTimeout = 30000
        validate()
    })

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
