package com.vueltax.domain.model

import java.math.BigDecimal
import java.time.LocalDate

enum class PurchaseKind { GOODS, SERVICE }

/**
 * Una factura de compra. Los campos que el usuario aún no ha confirmado van en null: el
 * motor de reglas los pide como información faltante en lugar de suponerlos.
 */
data class Purchase(
    val id: Long = 0,
    val tripId: Long,
    val storeName: String,
    val invoiceNumber: String,
    val purchaseDate: LocalDate,
    /** Total de la factura, impuesto incluido. */
    val total: BigDecimal,
    /** Impuesto que figura en la factura; null si no se conoce (se estima con la tasa general). */
    val tax: BigDecimal?,
    val currency: String,
    val kind: PurchaseKind?,
    /** Categoría del bien o servicio, con los identificadores de las reglas. */
    val category: String?,
    val paidElectronically: Boolean?,
    val authorizedStore: Boolean?,
    val intentDeclaredBeforeInvoice: Boolean?,
    val imagePath: String? = null,
)
