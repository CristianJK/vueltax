package com.vueltax.app.capture.parser

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Los comprobantes de estas pruebas son inventados (RUC 20123456789 no corresponde a un
 * emisor real); solo siguen el orden de campos del Anexo C de la RS 113-2018/SUNAT.
 */
class PeruQrParserTest {
    private val invoice = "20123456789|01|F001|00001234|18.00|118.00|2026-11-10|7|X1234567|"

    @Test
    fun `lee los campos en el orden de la especificacion`() {
        val parsed = PeruQrParser.parse(invoice)!!

        assertEquals("PE", parsed.country)
        assertEquals(CaptureSource.QR, parsed.source)
        assertEquals("20123456789", parsed.issuerTaxId)
        assertEquals("F001-00001234", parsed.invoiceNumber)
        assertEquals(BigDecimal("18.00"), parsed.tax)
        assertEquals(BigDecimal("118.00"), parsed.total)
        assertEquals(LocalDate.of(2026, 11, 10), parsed.issueDate)
        assertNull(parsed.documentKey)
    }

    @Test
    fun `no conserva el documento del comprador`() {
        assertFalse(PeruQrParser.parse(invoice).toString().contains("X1234567"))
    }

    @Test
    fun `acepta el valor resumen al final y espacios alrededor de los separadores`() {
        val withHash = "20123456789 | 03 | B001 | 77 | 1.53 | 10.00 | 2026-11-10 | 1 | 00000000 | aGFzaA== |"

        val parsed = PeruQrParser.parse(withHash)!!

        assertEquals("B001-77", parsed.invoiceNumber)
        assertEquals(BigDecimal("1.53"), parsed.tax)
        assertEquals(BigDecimal("10.00"), parsed.total)
    }

    @Test
    fun `acepta la fecha con el dia primero`() {
        val slashes = PeruQrParser.parse(invoice.replace("2026-11-10", "10/11/2026"))!!
        val dashes = PeruQrParser.parse(invoice.replace("2026-11-10", "10-11-2026"))!!

        assertEquals(LocalDate.of(2026, 11, 10), slashes.issueDate)
        assertEquals(LocalDate.of(2026, 11, 10), dashes.issueDate)
    }

    @Test
    fun `los campos vacios quedan sin dato`() {
        val parsed = PeruQrParser.parse("20123456789|03|B001|77||10.00||||")!!

        assertNull(parsed.tax)
        assertNull(parsed.issueDate)
        assertEquals(BigDecimal("10.00"), parsed.total)
    }

    @Test
    fun `sin serie ni numero no hay numero de comprobante`() {
        val parsed = PeruQrParser.parse("20123456789|03|||1.53|10.00|2026-11-10||")!!

        assertNull(parsed.invoiceNumber)
    }

    @Test
    fun `una fecha o un monto invalidos quedan vacios`() {
        val parsed = PeruQrParser.parse("20123456789|01|F001|1|abc|118,00|31/02/2026|7|X|")!!

        assertNull(parsed.tax)
        assertNull(parsed.total)
        assertNull(parsed.issueDate)
    }

    @Test
    fun `rechaza textos con menos campos o sin un RUC de 11 digitos`() {
        assertNull(PeruQrParser.parse(""))
        assertNull(PeruQrParser.parse("20123456789|01|F001|1234|18.00|118.00"))
        assertNull(PeruQrParser.parse("2012345678|01|F001|1234|18.00|118.00|2026-11-10|"))
        assertNull(PeruQrParser.parse("RUC|01|F001|1234|18.00|118.00|2026-11-10|"))
        assertNull(PeruQrParser.parse("https://example.org/a|b|c|d|e|f|g"))
    }
}
