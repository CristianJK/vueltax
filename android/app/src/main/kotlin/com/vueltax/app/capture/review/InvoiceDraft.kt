package com.vueltax.app.capture.review

import com.vueltax.app.capture.parser.CaptureSource
import com.vueltax.app.capture.parser.ParsedInvoice
import com.vueltax.app.capture.parser.ReceiptTextParser
import com.vueltax.domain.model.Purchase
import com.vueltax.domain.model.PurchaseKind
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Factura en revisión: lo leído del QR o de la foto más lo que el usuario corrige. Los
 * campos de texto guardan lo que hay escrito en pantalla, aunque todavía no sea válido.
 */
data class InvoiceDraft(
    val country: String,
    val storeName: String = "",
    val invoiceNumber: String = "",
    val issueDate: String = "",
    val total: String = "",
    val tax: String = "",
    val issuerTaxId: String? = null,
    val documentKey: String? = null,
    val kind: PurchaseKind? = null,
    val category: String? = null,
    val paidElectronically: Boolean? = null,
    val authorizedStore: Boolean? = null,
    val intentDeclaredBeforeInvoice: Boolean? = null,
    val imagePath: String? = null,
) {
    val parsedTotal: BigDecimal?
        get() = ReceiptTextParser.parseAmount(total)

    val parsedTax: BigDecimal?
        get() = ReceiptTextParser.parseAmount(tax)

    val parsedDate: LocalDate?
        get() = try {
            LocalDate.parse(issueDate.trim())
        } catch (_: DateTimeParseException) {
            null
        }

    /**
     * Incorpora lo leído de una factura. Los datos de un QR son fiables y sustituyen a lo
     * que hubiera; los del OCR son aproximados y solo rellenan los campos vacíos.
     */
    fun mergedWith(parsed: ParsedInvoice): InvoiceDraft {
        val overwrite = parsed.source == CaptureSource.QR
        fun pick(current: String, read: Any?): String =
            if (read != null && (overwrite || current.isBlank())) read.toString() else current

        return copy(
            country = parsed.country,
            invoiceNumber = pick(invoiceNumber, parsed.invoiceNumber),
            issueDate = pick(issueDate, parsed.issueDate),
            total = pick(total, parsed.total?.toPlainString()),
            tax = pick(tax, parsed.tax?.toPlainString()),
            issuerTaxId = parsed.issuerTaxId ?: issuerTaxId,
            documentKey = parsed.documentKey ?: documentKey,
        )
    }

    /** La factura como la entiende el motor de reglas; null si falta el total o la fecha. */
    fun toPurchase(tripId: Long, currency: String): Purchase? {
        val purchaseTotal = parsedTotal
        val purchaseDate = parsedDate
        if (purchaseTotal == null || purchaseDate == null) return null

        return Purchase(
            tripId = tripId,
            storeName = storeName.trim(),
            invoiceNumber = invoiceNumber.trim(),
            purchaseDate = purchaseDate,
            total = purchaseTotal,
            tax = parsedTax,
            currency = currency,
            kind = kind,
            category = category,
            paidElectronically = paidElectronically,
            authorizedStore = authorizedStore,
            intentDeclaredBeforeInvoice = intentDeclaredBeforeInvoice,
            imagePath = imagePath,
        )
    }
}
