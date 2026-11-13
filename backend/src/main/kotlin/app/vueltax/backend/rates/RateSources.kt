package app.vueltax.backend.rates

import app.vueltax.backend.CURRENCY_CODE
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Una fuente pública de tasas de cambio. [name] se guarda con cada tasa. */
interface RateSource {
    val name: String
    suspend fun fetch(): List<RateQuote>
}

// isLenient permite leer un número JSON como String y así no perder precisión al pasarlo a BigDecimal.
private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * Frankfurter v2 (https://api.frankfurter.dev). La v1 solo trae las monedas del BCE y no
 * incluye COP ni PEN; la v2 sí.
 */
class FrankfurterSource(
    private val client: HttpClient,
    private val baseUrl: String,
    private val bases: List<String>,
    private val quotes: List<String>,
) : RateSource {
    override val name = NAME

    override suspend fun fetch(): List<RateQuote> = bases.flatMap { base ->
        val wanted = quotes.filter { it != base }
        val body = client.get("${baseUrl.trimEnd('/')}/v2/rates") {
            parameter("base", base)
            parameter("quotes", wanted.joinToString(","))
        }.bodyAsText()
        lenientJson.decodeFromString<List<Row>>(body)
            .filter { it.base == base && it.quote in wanted && CURRENCY_CODE.matches(it.quote) }
            .map { RateQuote(LocalDate.parse(it.date), it.base, it.quote, BigDecimal(it.rate), name) }
    }

    @Serializable
    private data class Row(val date: String, val base: String, val quote: String, val rate: String)

    companion object {
        const val NAME = "frankfurter"
    }
}

/**
 * Tasa Representativa del Mercado (USD→COP) certificada por la Superintendencia
 * Financiera de Colombia, publicada en datos.gov.co (dataset Socrata 32sa-8pi3).
 */
class TrmSource(
    private val client: HttpClient,
    private val url: String,
    private val today: () -> LocalDate = { LocalDate.now(COLOMBIA) },
) : RateSource {
    override val name = NAME

    override suspend fun fetch(): List<RateQuote> {
        val body = client.get(url) {
            parameter("\$order", "vigenciadesde DESC")
            parameter("\$limit", RECENT_RECORDS)
        }.bodyAsText()
        val records = lenientJson.decodeFromString<List<Record>>(body)
        check(records.isNotEmpty()) { "El dataset de la TRM no devolvió registros" }

        // Una TRM rige desde vigenciadesde hasta vigenciahasta (p. ej. de sábado a lunes):
        // se guarda una fila por cada día de vigencia.
        val oldest = today().minusDays(MAX_AGE_DAYS)
        return records.flatMap { record ->
            val from = LocalDate.parse(record.vigenciadesde.take(ISO_DATE_LENGTH))
            val to = LocalDate.parse(record.vigenciahasta.take(ISO_DATE_LENGTH))
            val days = ChronoUnit.DAYS.between(from, to)
            check(days in 0..MAX_VALIDITY_DAYS) { "Vigencia de TRM inesperada: $from a $to" }
            (0..days).map { from.plusDays(it) }
                .filter { it >= oldest }
                .map { RateQuote(it, "USD", "COP", BigDecimal(record.valor), name) }
        }
    }

    @Serializable
    private data class Record(val valor: String, val vigenciadesde: String, val vigenciahasta: String)

    companion object {
        const val NAME = "trm-datos.gov.co"
        private val COLOMBIA = java.time.ZoneId.of("America/Bogota")
        private const val RECENT_RECORDS = 5
        private const val MAX_AGE_DAYS = 10L
        private const val MAX_VALIDITY_DAYS = 10L
        private const val ISO_DATE_LENGTH = 10
    }
}
