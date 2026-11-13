package com.vueltax.domain.model

import java.time.LocalDate

/** Una factura de compra. Los montos van en unidades menores de la moneda (centavos). */
data class Purchase(
    val id: Long = 0,
    val tripId: Long,
    val storeName: String,
    val invoiceNumber: String,
    val purchaseDate: LocalDate,
    val totalMinor: Long,
    val taxMinor: Long,
    val currency: String,
    val category: String?,
    val paidElectronically: Boolean,
    val imagePath: String?,
)
