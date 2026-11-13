package app.vueltax.backend.rates

import app.vueltax.backend.db.ExchangeRates
import app.vueltax.backend.db.query
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/** Una tasa: 1 [base] = [rate] [quote] en [date], según [source]. */
data class RateQuote(
    val date: LocalDate,
    val base: String,
    val quote: String,
    val rate: BigDecimal,
    val source: String,
)

class RateRepository(private val database: Database) {

    /** Inserta o actualiza por (base, fecha, quote, fuente). Devuelve cuántas tasas guardó. */
    suspend fun save(quotes: List<RateQuote>): Int {
        val unique = quotes.associateBy { listOf(it.base, it.date, it.quote, it.source) }.values
        if (unique.isEmpty()) return 0
        database.query {
            val now = OffsetDateTime.now()
            ExchangeRates.batchUpsert(
                unique,
                ExchangeRates.base,
                ExchangeRates.rateDate,
                ExchangeRates.quote,
                ExchangeRates.sourceName,
            ) { quote ->
                this[ExchangeRates.rateDate] = quote.date
                this[ExchangeRates.base] = quote.base
                this[ExchangeRates.quote] = quote.quote
                this[ExchangeRates.rate] = quote.rate
                this[ExchangeRates.sourceName] = quote.source
                this[ExchangeRates.fetchedAt] = now
            }
        }
        return unique.size
    }

    /**
     * Para cada (quote, fuente), la tasa más reciente publicada en [date] o antes, sin
     * retroceder más de [LOOKBACK_DAYS] días (fines de semana y festivos no tienen tasa).
     */
    suspend fun latestOnOrBefore(base: String, date: LocalDate): List<RateQuote> = database.query {
        ExchangeRates.selectAll()
            .where {
                (ExchangeRates.base eq base) and
                    (ExchangeRates.rateDate lessEq date) and
                    (ExchangeRates.rateDate greaterEq date.minusDays(LOOKBACK_DAYS))
            }
            .map {
                RateQuote(
                    date = it[ExchangeRates.rateDate],
                    base = it[ExchangeRates.base],
                    quote = it[ExchangeRates.quote],
                    rate = it[ExchangeRates.rate],
                    source = it[ExchangeRates.sourceName],
                )
            }
            .groupBy { it.quote to it.source }
            .map { (_, candidates) -> candidates.maxBy { it.date } }
            .sortedWith(compareBy({ it.quote }, { it.source }))
    }

    companion object {
        const val LOOKBACK_DAYS = 7L
    }
}
