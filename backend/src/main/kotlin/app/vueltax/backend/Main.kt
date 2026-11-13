package app.vueltax.backend

import app.vueltax.backend.db.Db
import app.vueltax.backend.db.query
import app.vueltax.backend.http.ADMIN_AUTH
import app.vueltax.backend.http.ADMIN_RATE_LIMIT
import app.vueltax.backend.http.PUBLIC_RATE_LIMIT
import app.vueltax.backend.http.adminRoutes
import app.vueltax.backend.http.installPlugins
import app.vueltax.backend.http.publicRoutes
import app.vueltax.backend.http.respondError
import app.vueltax.backend.http.respondJson
import app.vueltax.backend.rates.FrankfurterSource
import app.vueltax.backend.rates.RateRepository
import app.vueltax.backend.rates.RatesUpdater
import app.vueltax.backend.rates.TrmSource
import app.vueltax.backend.refund.RefundPointRepository
import app.vueltax.backend.rules.RuleSetRepository
import app.vueltax.backend.rules.RuleSetValidator
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.launch
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory

/** Dependencias de las rutas; las pruebas las construyen con fuentes de tasas falsas. */
class Services(
    val database: Database,
    val rules: RuleSetRepository,
    val ruleValidator: RuleSetValidator,
    val refundPoints: RefundPointRepository,
    val rates: RateRepository,
    val ratesUpdater: RatesUpdater,
)

fun main() {
    val settings = Settings.fromEnv()

    val dataSource = Db.dataSource(settings.database)
    Db.migrate(dataSource)
    val database = Db.connect(dataSource)

    val httpClient = HttpClient(CIO) {
        expectSuccess = true
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 10_000
        }
    }
    val rates = RateRepository(database)
    val services = Services(
        database = database,
        rules = RuleSetRepository(database),
        ruleValidator = RuleSetValidator(),
        refundPoints = RefundPointRepository(database),
        rates = rates,
        ratesUpdater = RatesUpdater(
            sources = listOf(
                FrankfurterSource(
                    httpClient,
                    settings.rates.frankfurterUrl,
                    settings.rates.bases,
                    settings.rates.quotes,
                ),
                TrmSource(httpClient, settings.rates.trmUrl),
            ),
            repository = rates,
        ),
    )

    embeddedServer(Netty, port = settings.port, host = "0.0.0.0") {
        module(settings, services)
        if (settings.rates.jobEnabled) {
            launch { services.ratesUpdater.runDaily(settings.rates.jobTimeUtc) }
        }
    }.start(wait = true)
}

fun Application.module(settings: Settings, services: Services) {
    installPlugins(settings)

    val log = LoggerFactory.getLogger("app.vueltax.backend.Health")
    routing {
        get("/health") {
            try {
                services.database.query { exec("SELECT 1") }
                call.respondJson("""{"status":"ok"}""")
            } catch (e: Exception) {
                log.warn("Healthcheck sin base de datos: {}", e.toString())
                call.respondError(HttpStatusCode.ServiceUnavailable, "Base de datos no disponible")
            }
        }

        rateLimit(PUBLIC_RATE_LIMIT) {
            publicRoutes(services)
        }

        rateLimit(ADMIN_RATE_LIMIT) {
            authenticate(ADMIN_AUTH) {
                adminRoutes(services)
            }
        }
    }
}
