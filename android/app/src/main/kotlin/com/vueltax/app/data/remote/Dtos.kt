package com.vueltax.app.data.remote

import com.vueltax.app.data.local.CachedRateEntity
import com.vueltax.app.data.local.CachedRefundPointEntity
import kotlinx.serialization.Serializable

@Serializable
data class CountryDto(
    val code: String,
    val name: String,
    val currency: String,
    val taxName: String,
    /** Última versión publicada de las reglas; null si el país aún no tiene. */
    val rulesVersion: Int? = null,
)

@Serializable
data class RulesVersionDto(val country: String, val version: Int)

@Serializable
data class RefundPointDto(
    val id: Long,
    val country: String,
    val airportIata: String,
    val terminal: String? = null,
    val description: String,
    val lat: Double,
    val lon: Double,
    val openingHours: String,
)

@Serializable
data class RateDto(val quote: String, val rate: Double, val date: String, val source: String)

@Serializable
data class RatesDto(val base: String, val date: String, val rates: List<RateDto>)

fun RefundPointDto.toEntity(fetchedAt: Long) = CachedRefundPointEntity(
    id = id,
    countryCode = country,
    airportIata = airportIata,
    terminal = terminal,
    description = description,
    lat = lat,
    lon = lon,
    openingHours = openingHours,
    fetchedAt = fetchedAt,
)

fun RateDto.toEntity(base: String, fetchedAt: Long) = CachedRateEntity(
    base = base,
    quote = quote,
    rate = rate,
    rateDate = date,
    source = source,
    fetchedAt = fetchedAt,
)
