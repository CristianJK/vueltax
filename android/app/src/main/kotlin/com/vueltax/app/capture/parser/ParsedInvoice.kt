package com.vueltax.app.capture.parser

import java.math.BigDecimal
import java.time.LocalDate

enum class CaptureSource { QR, OCR }

/**
 * Lo que se pudo leer de una factura. Cada campo es null si el código o la foto no lo traen;
 * el usuario lo confirma o completa en la pantalla de revisión.
 *
 * A propósito no se guarda el documento del comprador (pasaporte, RFC...), aunque los
 * códigos QR lo incluyan: la app no lo necesita.
 */
data class ParsedInvoice(
    val country: String,
    val source: CaptureSource,
    val invoiceNumber: String? = null,
    val issueDate: LocalDate? = null,
    val issuerTaxId: String? = null,
    val total: BigDecimal? = null,
    val tax: BigDecimal? = null,
    /** Identificador fiscal único: CUFE/CUDE en Colombia, UUID (folio fiscal) en México. */
    val documentKey: String? = null,
)

/** Lee el contenido de un código QR o de barras de una factura de un país. */
interface InvoiceQrParser {
    /** Devuelve null si el texto no tiene el formato de este país. */
    fun parse(raw: String): ParsedInvoice?
}

object InvoiceQrParsers {
    private val parsers = listOf(MexicoQrParser, ColombiaQrParser, PeruQrParser)

    /** Prueba los formatos de todos los países; null si no es una factura reconocida. */
    fun parse(raw: String): ParsedInvoice? = parsers.firstNotNullOfOrNull { it.parse(raw) }
}

internal fun String.toAmountOrNull(): BigDecimal? = trim().toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
