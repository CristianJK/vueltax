package com.vueltax.domain.model

import java.time.LocalDate

/** Reglas de devolución de un país, tal como las publica la API (JSON de `rules/schema.json`). */
data class RuleSet(
    val countryCode: String,
    val version: Int,
    val contentJson: String,
)

data class ExchangeRate(
    val base: String,
    val quote: String,
    val rate: Double,
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
