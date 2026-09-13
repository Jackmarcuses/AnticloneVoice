package com.jackmarcus.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.net.URI

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"
        val rawUrl = System.getenv("JDBC_DATABASE_URL") 
            ?: "postgresql://postgres.dzpkgschzppvjonksvww:01ZA92YB34CD@aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres"
        
        // Use Java's URI parser to correctly split the URL
        val dbUri = URI(rawUrl.replace("jdbc:", ""))
        val userInfo = dbUri.userInfo?.split(":")
        
        val user = System.getenv("JDBC_DATABASE_USER") ?: userInfo?.getOrNull(0) ?: "postgres"
        val password = System.getenv("JDBC_DATABASE_PASSWORD") ?: userInfo?.getOrNull(1) ?: "01ZA92YB34CD"
        
        // Reconstruct a clean JDBC URL without the user:pass@ part
        val host = dbUri.host
        val port = dbUri.port
        val path = dbUri.path
        val cleanJdbcUrl = "jdbc:postgresql://$host:$port$path"

        val database = Database.connect(createHikariDataSource(cleanJdbcUrl, driverClassName, user, password))
        
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
        addDataSourceProperty("tcpKeepAlive", "true")
        connectionTimeout = 30000
        validate()
    })

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
