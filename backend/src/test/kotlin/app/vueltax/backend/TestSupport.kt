package app.vueltax.backend

import app.vueltax.backend.db.Db
import app.vueltax.backend.rates.RateRepository
import app.vueltax.backend.rates.RateSource
import app.vueltax.backend.rates.RatesUpdater
import app.vueltax.backend.refund.RefundPointRepository
import app.vueltax.backend.rules.RuleSetRepository
import app.vueltax.backend.rules.RuleSetValidator
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.testcontainers.postgresql.PostgreSQLContainer
import java.io.File
import java.time.LocalTime
import kotlin.time.Duration.Companion.milliseconds

const val TEST_ADMIN_TOKEN = "test-admin-token-0123456789abcdef0123"

/** Un PostgreSQL real (Testcontainers) compartido por todas las pruebas, migrado con Flyway. */
object TestDatabase {
    private val container = PostgreSQLContainer("postgres:18-alpine").apply { start() }

    val database: Database = run {
        val dataSource = Db.dataSource(DatabaseSettings(container.jdbcUrl, container.username, container.password))
        Db.migrate(dataSource)
        Db.connect(dataSource)
    }

    /** Vacía los datos cargados por las pruebas; los países vienen de la migración. */
    fun reset() {
        transaction(database) {
            exec("TRUNCATE rule_set, refund_point, exchange_rate RESTART IDENTITY")
        }
    }
}

fun testSettings(publicRateLimit: Int = 10_000, corsAllowedOrigins: List<String> = emptyList()) = Settings(
    port = 0,
    database = DatabaseSettings("unused", "unused", "unused"),
    adminToken = TEST_ADMIN_TOKEN,
    trustForwardedHeaders = false,
    corsAllowedOrigins = corsAllowedOrigins,
    publicRateLimitPerMinute = publicRateLimit,
    adminRateLimitPerMinute = 10_000,
    rates = RatesSettings(
        jobEnabled = false,
        jobTimeUtc = LocalTime.MIDNIGHT,
        bases = listOf("USD"),
        quotes = listOf("COP"),
        frankfurterUrl = "http://unused.invalid",
        trmUrl = "http://unused.invalid",
    ),
)

fun testServices(sources: List<RateSource> = emptyList()): Services {
    val database = TestDatabase.database
    val rates = RateRepository(database)
    return Services(
        database = database,
        rules = RuleSetRepository(database),
        ruleValidator = RuleSetValidator(),
        refundPoints = RefundPointRepository(database),
        rates = rates,
        ratesUpdater = RatesUpdater(sources, rates, maxAttempts = 3, initialBackoff = 1.milliseconds),
    )
}

fun apiTest(
    settings: Settings = testSettings(),
    sources: List<RateSource> = emptyList(),
    block: suspend ApplicationTestBuilder.() -> Unit,
) = testApplication {
    application { module(settings, testServices(sources)) }
    block()
}

fun HttpRequestBuilder.asAdmin() = bearerAuth(TEST_ADMIN_TOKEN)

suspend fun HttpResponse.json(): JsonElement = Json.parseToJsonElement(bodyAsText())

/** Archivos reales de rules/ (el build pasa la ruta en vueltax.rulesDir). */
object RuleFiles {
    private val dir = File(checkNotNull(System.getProperty("vueltax.rulesDir")) { "Falta vueltax.rulesDir" })

    val countries: List<String> = dir.listFiles { file -> Regex("^[A-Z]{2}\\.json$").matches(file.name) }
        .orEmpty()
        .map { it.nameWithoutExtension }
        .sorted()

    fun read(country: String): String = File(dir, "$country.json").readText()

    /** El rule set de [country] con un campo de primer nivel reemplazado por [rawValue] (JSON). */
    fun with(country: String, field: String, rawValue: String): String {
        val original = Json.parseToJsonElement(read(country)) as kotlinx.serialization.json.JsonObject
        return kotlinx.serialization.json.JsonObject(original + (field to Json.parseToJsonElement(rawValue))).toString()
    }
}
