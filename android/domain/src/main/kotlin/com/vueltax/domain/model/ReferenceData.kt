package com.vueltax.domain.model

import java.math.BigDecimal
import java.time.LocalDate

/** Cuántas unidades de [quote] vale una unidad de [base] en [date]. */
data class ExchangeRate(
    val base: String,
    val quote: String,
    val rate: BigDecimal,
    val date: LocalDate,
    val source: String,
)

data class RefundPoint(
    val id: Long,
    val countryCode: String,
    val airportIata: String,
    val terminal: String?,
    val description: String,
    val lat: Double,
    val lon: Double,
    val openingHours: String,
)
