package com.vueltax.app.data.local

import com.vueltax.domain.model.Purchase
import com.vueltax.domain.model.PurchaseKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private const val MINOR_UNIT_DIGITS = 2

private fun BigDecimal.toMinorUnits(): Long =
    movePointRight(MINOR_UNIT_DIGITS).setScale(0, RoundingMode.HALF_UP).longValueExact()

private fun Long.fromMinorUnits(): BigDecimal = BigDecimal.valueOf(this, MINOR_UNIT_DIGITS)

fun Purchase.toEntity(issuerTaxId: String? = null, documentKey: String? = null) = PurchaseEntity(
    id = id,
    tripId = tripId,
    storeName = storeName,
    invoiceNumber = invoiceNumber,
    purchaseDate = purchaseDate.toString(),
    totalMinor = total.toMinorUnits(),
    taxMinor = tax?.toMinorUnits(),
    currency = currency,
    kind = kind?.name,
    category = category,
    paidElectronically = paidElectronically,
    authorizedStore = authorizedStore,
    intentDeclaredBeforeInvoice = intentDeclaredBeforeInvoice,
    issuerTaxId = issuerTaxId,
    documentKey = documentKey,
    imagePath = imagePath,
)

fun PurchaseEntity.toDomain() = Purchase(
    id = id,
    tripId = tripId,
    storeName = storeName,
    invoiceNumber = invoiceNumber,
    purchaseDate = LocalDate.parse(purchaseDate),
    total = totalMinor.fromMinorUnits(),
    tax = taxMinor?.fromMinorUnits(),
    currency = currency,
    kind = kind?.let(PurchaseKind::valueOf),
    category = category,
    paidElectronically = paidElectronically,
    authorizedStore = authorizedStore,
    intentDeclaredBeforeInvoice = intentDeclaredBeforeInvoice,
    imagePath = imagePath,
)
