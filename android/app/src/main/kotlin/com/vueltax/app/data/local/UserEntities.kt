package com.vueltax.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Datos personales del usuario: solo existen en la base de datos cifrada del dispositivo.
// Las fechas se guardan como texto ISO-8601 (YYYY-MM-DD) y los enums por su nombre.

@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val countryCode: String,
    val arrivalDate: String,
    val departureDate: String,
    val exitMode: String,
)

@Entity(tableName = "traveler")
data class TravelerEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val nationality: String,
    val residenceCountry: String,
)

@Entity(
    tableName = "purchase",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val storeName: String,
    val invoiceNumber: String,
    val purchaseDate: String,
    val totalMinor: Long,
    val taxMinor: Long,
    val currency: String,
    val category: String?,
    val paidElectronically: Boolean,
    val imagePath: String?,
)

@Entity(
    tableName = "refund_request",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class RefundRequestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val status: String,
    val estimatedRefundMinor: Long,
    val currency: String,
    val refundPointId: Long?,
)
