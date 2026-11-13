package com.vueltax.app.capture.parser

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/**
 * QR del comprobante de pago electrónico de Perú (factura o boleta de venta).
 *
 * Formato: «Código de barras QR, información a consignar» del Anexo C de la Resolución de
 * Superintendencia 113-2018/SUNAT (mismo orden que el numeral 6.5.3 del anexo de aspectos
 * técnicos anterior). Los campos van separados por `|`, en este orden, y un campo sin dato
 * se deja vacío para conservar la cantidad:
 *
 * ```
 * RUC | TIPO DE DOCUMENTO | SERIE | NUMERO | MTO TOTAL IGV | MTO TOTAL DEL COMPROBANTE |
 * FECHA DE EMISION | TIPO DE DOCUMENTO ADQUIRENTE | NUMERO DE DOCUMENTO ADQUIRENTE |
 * VALOR RESUMEN (opcional)
 * ```
 *
 * La norma dice que cada dato va «con el mismo formato empleado en el comprobante», así que
 * la fecha debería ser AAAA-MM-DD; se acepta también DD/MM/AAAA y DD-MM-AAAA.
 */
object PeruQrParser : InvoiceQrParser {
    private const val RUC = 0
    private const val SERIES = 2
    private const val NUMBER = 3
    private const val TAX = 4
    private const val TOTAL = 5
    private const val DATE = 6
    private const val MIN_FIELDS = 7

    private val ruc = Regex("""\d{11}""")
    // STRICT para que una fecha imposible (31/02) no se "corrija" al último día del mes.
    private val dateFormats = listOf(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
    )

    override fun parse(raw: String): ParsedInvoice? {
        val fields = raw.split('|').map { it.trim() }
        if (fields.size < MIN_FIELDS || !ruc.matches(fields[RUC])) return null

        val number = listOf(fields[SERIES], fields[NUMBER]).filter { it.isNotEmpty() }
        return ParsedInvoice(
            country = "PE",
            source = CaptureSource.QR,
            invoiceNumber = number.joinToString("-").ifEmpty { null },
            issueDate = fields[DATE].toDateOrNull(),
            issuerTaxId = fields[RUC],
            total = fields[TOTAL].toAmountOrNull(),
            tax = fields[TAX].toAmountOrNull(),
        )
    }

    private fun String.toDateOrNull(): LocalDate? = dateFormats.firstNotNullOfOrNull { format ->
        try {
            LocalDate.parse(this, format)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
