package com.vueltax.app.capture.parser

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * QR de la factura electrónica de venta de Colombia.
 *
 * Formato: numeral 11.1.7 «Código Bidimensional QR» del Anexo Técnico de Factura
 * Electrónica de Venta, versión 1.9 (Resolución DIAN 000165 del 1 de noviembre de 2023).
 * El QR lleva una etiqueta por dato, en este orden:
 *
 * ```
 * NumFac: [número]      FecFac: [AAAA-MM-DD]   HorFac: [hora con GMT]
 * NitFac: [NIT emisor]  DocAdq: [id comprador]
 * ValFac: [valor sin impuestos]  ValIva: [IVA]  ValOtroIm: [otros impuestos]
 * ValTolFac: [total]    CUFE: [CUFE o CUDE]    [URL de consulta de la DIAN]
 * ```
 *
 * Los valores van con punto decimal, dos decimales y sin separador de miles. En la práctica
 * los emisores separan los datos con saltos de línea o espacios y a veces usan `=`, y
 * algunos QR solo traen la URL de consulta, de la que se obtiene el CUFE.
 */
object ColombiaQrParser : InvoiceQrParser {
    private val label = Regex(
        """(NumFac|FecFac|HorFac|NitFac|DocAdq|ValFac|ValIva|ValOtroIm|ValTolFac|CUFE|CUDE|QRCode)\s*[:=]\s*""",
        RegexOption.IGNORE_CASE,
    )
    private val documentKeyInUrl = Regex("""documentkey=([0-9a-fA-F]+)""", RegexOption.IGNORE_CASE)

    override fun parse(raw: String): ParsedInvoice? {
        val fields = labelledFields(raw)
        val documentKey = fields["cufe"] ?: fields["cude"]
            ?: documentKeyInUrl.find(raw)?.groupValues?.get(1)
        val invoiceNumber = fields["numfac"]
        if (invoiceNumber == null && documentKey == null) return null

        return ParsedInvoice(
            country = "CO",
            source = CaptureSource.QR,
            invoiceNumber = invoiceNumber,
            issueDate = fields["fecfac"]?.toDateOrNull(),
            issuerTaxId = fields["nitfac"],
            total = fields["valtolfac"]?.toAmountOrNull(),
            tax = fields["valiva"]?.toAmountOrNull(),
            documentKey = documentKey,
        )
    }

    /** Valor de cada etiqueta: el primer texto sin espacios que la sigue. */
    private fun labelledFields(raw: String): Map<String, String> {
        val matches = label.findAll(raw).toList()
        return matches.mapIndexedNotNull { index, match ->
            val end = matches.getOrNull(index + 1)?.range?.first ?: raw.length
            val value = raw.substring(match.range.last + 1, end).trim().split(Regex("""\s+""")).first()
            if (value.isEmpty()) null else match.groupValues[1].lowercase() to value
        }.toMap()
    }

    private fun String.toDateOrNull(): LocalDate? = try {
        LocalDate.parse(this)
    } catch (_: DateTimeParseException) {
        null
    }
}
