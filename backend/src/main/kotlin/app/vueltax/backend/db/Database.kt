package app.vueltax.backend.db

import app.vueltax.backend.DatabaseSettings
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

object Db {
    fun dataSource(settings: DatabaseSettings): HikariDataSource = HikariDataSource(
        HikariConfig().apply {
            jdbcUrl = settings.url
            username = settings.user
            password = settings.password
            maximumPoolSize = 10
            poolName = "api"
        },
    )

    /** Flyway es el único dueño del esquema; Exposed solo consulta. */
    fun migrate(dataSource: DataSource) {
        Flyway.configure().dataSource(dataSource).load().migrate()
    }

    fun connect(dataSource: DataSource): Database = Database.connect(dataSource)
}

/** Ejecuta una transacción JDBC (bloqueante) fuera de los hilos de Ktor. */
suspend fun <T> Database.query(block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(this@query) { block() } }
