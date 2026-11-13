package com.vueltax.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Datos personales del usuario: solo existen en la base de datos cifrada del dispositivo.
// Las fechas se guardan como texto ISO-8601 (YYYY-MM-DD), los enums por su nombre y los
// montos en unidades menores de la moneda (centavos). Un campo en null aún no se conoce.

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
    val taxMinor: Long?,
    val currency: String,
    val kind: String?,
    val category: String?,
    val paidElectronically: Boolean?,
    val authorizedStore: Boolean?,
    val intentDeclaredBeforeInvoice: Boolean?,
    val issuerTaxId: String?,
    val documentKey: String?,
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
