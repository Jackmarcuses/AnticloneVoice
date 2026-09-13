package com.jackmarcus.backend.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.heysql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {
    fun init() {
        val driverClassName = "org.postgresql.Driver"
        val jdbcUrl = System.getenv("JDBC_DATABASE_URL") 
            ?: "jdbc:postgresql://db.dzpkgschzppvjonksvww.supabase.co:5432/postgres"
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
        validate()
    })

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
