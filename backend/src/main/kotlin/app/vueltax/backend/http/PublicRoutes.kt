package app.vueltax.backend.http

import app.vueltax.backend.COUNTRY_CODE
import app.vueltax.backend.CURRENCY_CODE
import app.vueltax.backend.Services
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

@Serializable
data class RulesVersionView(val country: String, val version: Int)

@Serializable
data class RateView(val quote: String, val rate: Double, val date: String, val source: String)

@Serializable
data class RatesView(val base: String, val date: String, val rates: List<RateView>)

private const val SHORT_CACHE = 60
private const val MEDIUM_CACHE = 300
private const val LONG_CACHE = 3600

/** Endpoints públicos: solo GET, sin autenticación y sin parámetros personales. */
fun Route.publicRoutes(services: Services) {
    route("/v1") {
        get("/countries") {
            call.respondCacheable(apiJson.encodeToString(services.rules.countries()), MEDIUM_CACHE)
        }

        get("/rules/{country}") {
            val country = call.countryParameter() ?: return@get
            val ruleSet = services.rules.latest(country)
                ?: return@get call.respondError(HttpStatusCode.NotFound, "No hay reglas para $country")
            call.respondCacheable(ruleSet.content, MEDIUM_CACHE)
        }

        get("/rules/{country}/version") {
            val country = call.countryParameter() ?: return@get
            val ruleSet = services.rules.latest(country)
                ?: return@get call.respondError(HttpStatusCode.NotFound, "No hay reglas para $country")
            call.respondCacheable(
                apiJson.encodeToString(RulesVersionView(ruleSet.country, ruleSet.version)),
                SHORT_CACHE,
            )
        }

        get("/refund-points") {
            val country = call.request.queryParameters["country"]?.uppercase()
            if (country != null && !COUNTRY_CODE.matches(country)) {
                return@get call.respondError(HttpStatusCode.BadRequest, "country debe ser un código ISO de 2 letras")
            }
            call.respondCacheable(apiJson.encodeToString(services.refundPoints.list(country)), LONG_CACHE)
        }

        get("/rates") {
            val base = call.request.queryParameters["base"]?.uppercase()
            if (base == null || !CURRENCY_CODE.matches(base)) {
                return@get call.respondError(HttpStatusCode.BadRequest, "base debe ser un código ISO 4217, p. ej. USD")
            }
            val rawDate = call.request.queryParameters["date"]
            val date = try {
                rawDate?.let(LocalDate::parse) ?: LocalDate.now(ZoneOffset.UTC)
            } catch (_: DateTimeParseException) {
                return@get call.respondError(HttpStatusCode.BadRequest, "date debe tener formato YYYY-MM-DD")
            }

            val rates = services.rates.latestOnOrBefore(base, date)
            if (rates.isEmpty()) {
                return@get call.respondError(HttpStatusCode.NotFound, "No hay tasas para $base en $date")
            }
            val view = RatesView(
                base = base,
                date = date.toString(),
                rates = rates.map { RateView(it.quote, it.rate.toDouble(), it.date.toString(), it.source) },
            )
            // Una fecha pasada ya no cambia; "hoy" se actualiza con el job diario.
            call.respondCacheable(apiJson.encodeToString(view), if (rawDate == null) MEDIUM_CACHE else LONG_CACHE)
        }
    }
}

private suspend fun ApplicationCall.countryParameter(): String? {
    val country = parameters["country"]?.uppercase()
    if (country == null || !COUNTRY_CODE.matches(country)) {
        respondError(HttpStatusCode.BadRequest, "El país debe ser un código ISO de 2 letras")
        return null
    }
    return country
}
