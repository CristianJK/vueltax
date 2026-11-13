package com.vueltax.app.capture.parser

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Los tiquetes de estas pruebas son inventados; ninguno corresponde a un comercio real. */
class ReceiptTextParserTest {
    private fun assertAmount(expected: String, actual: BigDecimal?) {
        val matches = actual != null && BigDecimal(expected).compareTo(actual) == 0
        assertEquals("Se esperaba $expected y fue $actual", true, matches)
    }

    @Test
    fun `CO - tiquete con puntos de miles`() {
        val text = """
            TIENDA DE PRUEBA S.A.S.
            NIT: 900.123.456-7
            FECHA: 10/11/2026
            SUBTOTAL        1.500.000
            IVA 19%           285.000
            TOTAL           1.785.000
        """.trimIndent()

        val invoice = ReceiptTextParser.parse(text, "CO")

        assertEquals(CaptureSource.OCR, invoice.source)
        assertAmount("1785000", invoice.total)
        assertAmount("285000", invoice.tax)
        assertEquals(LocalDate.of(2026, 11, 10), invoice.issueDate)
        assertEquals("900.123.456-7", invoice.issuerTaxId)
    }

    @Test
    fun `PE - boleta con decimales y fecha ISO`() {
        val text = """
            BOLETA DE VENTA ELECTRONICA
            RUC 20123456789
            Fecha de emision: 2026-11-10
            OP. GRAVADA  S/ 100.00
            I.G.V. 18%  S/ 18.00
            IMPORTE TOTAL  S/ 118.00
        """.trimIndent()

        val invoice = ReceiptTextParser.parse(text, "PE")

        assertAmount("118.00", invoice.total)
        assertAmount("18.00", invoice.tax)
        assertEquals(LocalDate.of(2026, 11, 10), invoice.issueDate)
        assertEquals("20123456789", invoice.issuerTaxId)
    }

    @Test
    fun `MX - tiquete con comas de miles y punto decimal`() {
        val text = """
            TIENDA DE PRUEBA SA DE CV
            RFC: TPR010101AB1
            12-11-2026 14:35
            SUBTOTAL  $2,000.00
            IVA 16%   $320.00
            TOTAL     $2,320.00
        """.trimIndent()

        val invoice = ReceiptTextParser.parse(text, "MX")

        assertAmount("2320.00", invoice.total)
        assertAmount("320.00", invoice.tax)
        assertEquals(LocalDate.of(2026, 11, 12), invoice.issueDate)
        assertEquals("TPR010101AB1", invoice.issuerTaxId)
    }

    @Test
    fun `si hay varios totales se queda con el mayor`() {
        val text = "TOTAL ARTICULOS 3\nTOTAL A PAGAR 45.900\nSUBTOTAL 99.999"

        assertAmount("45900", ReceiptTextParser.parse(text, "CO").total)
    }

    @Test
    fun `sin linea de total ni de impuesto no adivina`() {
        val invoice = ReceiptTextParser.parse("GRACIAS POR SU COMPRA\n900123456\n15.000", "CO")

        assertNull(invoice.total)
        assertNull(invoice.tax)
        assertNull(invoice.issueDate)
        assertNull(invoice.issuerTaxId)
    }

    @Test
    fun `una fecha imposible se ignora`() {
        assertNull(ReceiptTextParser.parse("FECHA 31/02/2026", "CO").issueDate)
    }

    @Test
    fun `no confunde el porcentaje con el impuesto`() {
        assertAmount("19000", ReceiptTextParser.parse("IVA 19 % 19.000", "CO").tax)
        assertNull(ReceiptTextParser.parse("IVA 19%", "CO").tax)
    }

    @Test
    fun `interpreta montos en cualquier convencion`() {
        assertAmount("1785000.00", ReceiptTextParser.parseAmount("1.785.000,00"))
        assertAmount("1785000.00", ReceiptTextParser.parseAmount("1,785,000.00"))
        assertAmount("1785000", ReceiptTextParser.parseAmount("1.785.000"))
        assertAmount("15000", ReceiptTextParser.parseAmount("15.000"))
        assertAmount("118.00", ReceiptTextParser.parseAmount("118.00"))
        assertAmount("118.5", ReceiptTextParser.parseAmount("118,5"))
        assertAmount("2026", ReceiptTextParser.parseAmount("2026"))
        assertNull(ReceiptTextParser.parseAmount(""))
    }
}
