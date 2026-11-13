package com.vueltax.app.capture.parser

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Heurísticas sobre el texto reconocido (OCR) de la foto de un tiquete, para cuando no hay
 * código QR. Son aproximaciones: el usuario siempre confirma o corrige el resultado.
 */
object ReceiptTextParser {
    private val amount = Regex("""\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?""")
    private val percentage = Regex("""\d+(?:[.,]\d+)?\s*%""")
    private val totalLabel = Regex("""\bTOTAL\b""", RegexOption.IGNORE_CASE)
    private val subtotalLabel = Regex("""SUB\s*-?\s*TOTAL""", RegexOption.IGNORE_CASE)
    private val taxLabel = Regex("""\b(I\.?V\.?A\.?|I\.?G\.?V\.?|IMPUESTO)(?![A-Za-z])""", RegexOption.IGNORE_CASE)

    private val isoDate = Regex("""\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})\b""")
    private val dayFirstDate = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})\b""")

    private val taxIdByCountry = mapOf(
        // NIT: 9 dígitos (con o sin puntos) y dígito de verificación opcional.
        "CO" to Regex("""NIT\D{0,5}(\d{1,3}(?:\.?\d{3}){2}(?:\s*-\s*\d)?)""", RegexOption.IGNORE_CASE),
        // RUC: 11 dígitos que empiezan por 10, 15, 17 o 20.
        "PE" to Regex("""\b((?:10|15|17|20)\d{9})\b"""),
        // RFC: 3 o 4 letras, fecha AAMMDD y homoclave de 3 caracteres.
        "MX" to Regex("""\b([A-ZÑ&]{3,4}\d{6}[A-Z0-9]{3})\b"""),
    )

    fun parse(text: String, country: String): ParsedInvoice {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return ParsedInvoice(
            country = country,
            source = CaptureSource.OCR,
            issueDate = findDate(text),
            issuerTaxId = taxIdByCountry[country]?.find(text)?.groupValues?.get(1)?.replace(" ", ""),
            total = findTotal(lines),
            tax = findTax(lines),
        )
    }

    /** El mayor monto de las líneas que dicen TOTAL (no SUBTOTAL); null si no hay ninguna. */
    private fun findTotal(lines: List<String>): BigDecimal? = lines
        .filter { totalLabel.containsMatchIn(it) && !subtotalLabel.containsMatchIn(it) }
        .flatMap(::amountsIn)
        .maxOrNull()

    /** El monto de la primera línea que nombra el impuesto, sin contar el porcentaje. */
    private fun findTax(lines: List<String>): BigDecimal? = lines
        .filter { taxLabel.containsMatchIn(it) }
        .firstNotNullOfOrNull { amountsIn(it).lastOrNull() }

    private fun amountsIn(line: String): List<BigDecimal> = amount
        .findAll(line.replace(percentage, " "))
        .mapNotNull { parseAmount(it.value) }
        .toList()

    private fun findDate(text: String): LocalDate? {
        val iso = isoDate.find(text)?.destructured?.let { (year, month, day) -> date(year, month, day) }
        return iso ?: dayFirstDate.find(text)?.destructured?.let { (day, month, year) -> date(year, month, day) }
    }

    private fun date(year: String, month: String, day: String): LocalDate? = try {
        val iso = "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
        LocalDate.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        null
    }

    /**
     * Interpreta un monto escrito con punto o coma, en cualquier convención:
     * `1.785.000,00`, `1,785,000.00`, `1.785.000`, `118.00`, `118,5`.
     *
     * El último separador es decimal solo si le siguen uno o dos dígitos; si le siguen tres,
     * es de miles (en los tres países los decimales nunca se escriben con tres cifras).
     */
    fun parseAmount(text: String): BigDecimal? {
        val compact = text.filterNot { it.isWhitespace() }
        val lastSeparator = compact.indexOfLast { it == '.' || it == ',' }
        val decimals = if (lastSeparator < 0) 0 else compact.length - lastSeparator - 1
        val hasDecimals = lastSeparator >= 0 && decimals in 1..2

        val digits = compact.filter { it.isDigit() }
        val normalized = if (hasDecimals) {
            digits.dropLast(decimals) + "." + digits.takeLast(decimals)
        } else {
            digits
        }
        return normalized.toBigDecimalOrNull()
    }
}
