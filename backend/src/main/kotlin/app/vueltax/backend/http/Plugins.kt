package app.vueltax.backend.http

import app.vueltax.backend.Settings
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.net.URI
import java.security.MessageDigest
import kotlin.time.Duration.Companion.minutes

const val ADMIN_AUTH = "admin"
val PUBLIC_RATE_LIMIT = RateLimitName("public")
val ADMIN_RATE_LIMIT = RateLimitName("admin")

fun Application.installPlugins(settings: Settings) {
    // Solo detrás de un proxy propio (Caddy): fuera de él, X-Forwarded-For lo puede
    // falsificar cualquier cliente para saltarse el rate limit.
    if (settings.trustForwardedHeaders) install(XForwardedHeaders)

    // Sin datos sensibles: método, ruta sin query string y estado. Ni IP, ni cabeceras,
    // ni cuerpo. El token de administración nunca se escribe.
    install(CallLogging) {
        level = Level.INFO
        disableDefaultColors()
        filter { it.request.path() != "/health" }
        format { call ->
            "${call.request.httpMethod.value} ${call.request.path()} -> ${call.response.status()?.value ?: "-"}"
        }
    }

    // CORS cerrado: sin orígenes configurados no se instala el plugin, no se emite
    // Access-Control-Allow-Origin y ningún navegador puede leer la API desde otro origen.
    // La app Android no usa CORS.
    if (settings.corsAllowedOrigins.isNotEmpty()) {
        install(CORS) {
            settings.corsAllowedOrigins.forEach { origin ->
                val uri = URI(origin)
                allowHost(uri.authority, schemes = listOf(uri.scheme))
            }
            allowMethod(HttpMethod.Get)
            allowHeader(HttpHeaders.IfNoneMatch)
            exposeHeader(HttpHeaders.ETag)
        }
    }

    // La IP solo se usa en memoria como clave del limitador; no se guarda ni se registra.
    install(RateLimit) {
        register(PUBLIC_RATE_LIMIT) {
            rateLimiter(limit = settings.publicRateLimitPerMinute, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteHost }
        }
        register(ADMIN_RATE_LIMIT) {
            rateLimiter(limit = settings.adminRateLimitPerMinute, refillPeriod = 1.minutes)
            requestKey { it.request.origin.remoteHost }
        }
    }

    install(Authentication) {
        bearer(ADMIN_AUTH) {
            val expected = settings.adminToken.toByteArray()
            authenticate { credential ->
                // Comparación en tiempo constante.
                if (MessageDigest.isEqual(credential.token.toByteArray(), expected)) UserIdPrincipal("admin") else null
            }
        }
    }

    install(StatusPages) {
        val log = LoggerFactory.getLogger("app.vueltax.backend.http.Errors")
        exception<Throwable> { call, cause ->
            log.error("Error no controlado en ${call.request.httpMethod.value} ${call.request.path()}", cause)
            call.respondError(HttpStatusCode.InternalServerError, "Error interno")
        }
        status(HttpStatusCode.NotFound) { call, status -> call.respondError(status, "No encontrado") }
        status(HttpStatusCode.MethodNotAllowed) { call, status -> call.respondError(status, "Método no permitido") }
        status(HttpStatusCode.Unauthorized) { call, status -> call.respondError(status, "No autorizado") }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respondError(status, "Demasiadas solicitudes; reintenta más tarde")
        }
    }
}
