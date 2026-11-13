package com.vueltax.domain.rules

import com.vueltax.domain.model.ExchangeRate
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tasas de cambio disponibles en el dispositivo. La API las publica todas contra una misma
 * moneda base (USD), así que el cambio entre otras dos monedas se calcula cruzando por ella.
 */
class RateTable(private val rates: List<ExchangeRate>) {
    private val bases = rates.map { it.base }.distinct()

    /**
     * Cuántas unidades de [to] vale una unidad de [from] en [date], usando la tasa más
     * reciente que no sea posterior a esa fecha; null si falta alguna de las dos monedas.
     */
    fun rate(from: String, to: String, date: LocalDate): BigDecimal? {
        if (from == to) return BigDecimal.ONE
        return bases.firstNotNullOfOrNull { base ->
            val fromPerBase = unitsPerBase(base, from, date)
            val toPerBase = unitsPerBase(base, to, date)
            if (fromPerBase == null || toPerBase == null) {
                null
            } else {
                toPerBase.divide(fromPerBase, CALCULATION_CONTEXT)
            }
        }
    }

    private fun unitsPerBase(base: String, currency: String, date: LocalDate): BigDecimal? {
        if (currency == base) return BigDecimal.ONE
        return rates
            .filter { it.base == base && it.quote == currency && !it.date.isAfter(date) }
            .maxByOrNull { it.date }
            ?.rate
    }
}
