package com.vueltax.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

// Copia local de los datos públicos de la API. `fetchedAt` es la hora de descarga en
// milisegundos desde la época (UTC).

@Entity(tableName = "cached_rule_set")
data class CachedRuleSetEntity(
    @PrimaryKey
    val countryCode: String,
    val version: Int,
    val contentJson: String,
    val fetchedAt: Long,
)

@Entity(tableName = "cached_rate", primaryKeys = ["base", "quote"])
data class CachedRateEntity(
    val base: String,
    val quote: String,
    val rate: Double,
    val rateDate: String,
    val source: String,
    val fetchedAt: Long,
)

@Entity(tableName = "cached_refund_point", indices = [Index("countryCode")])
data class CachedRefundPointEntity(
    @PrimaryKey
    val id: Long,
    val countryCode: String,
    val airportIata: String,
    val terminal: String?,
    val description: String,
    val lat: Double,
    val lon: Double,
    val openingHours: String,
    val fetchedAt: Long,
)
