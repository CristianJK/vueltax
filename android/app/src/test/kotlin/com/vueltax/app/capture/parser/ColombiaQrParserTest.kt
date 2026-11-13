package com.vueltax.app.capture.parser

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ColombiaQrParserTest {
    /**
     * Ejemplo literal del numeral 11.1.7 del Anexo Técnico v1.9 de la DIAN (no es una factura
     * real). La fecha del ejemplo, `2019-16-01`, es una errata del propio anexo.
     */
    private val annexExample = """
        NumFac: 323200000129
        FecFac: 2019-16-01
        HorFac: 10:53:10-05:00
        NitFac: 700085371
        DocAdq: 800199436
        ValFac: 1500000.00
        ValIva: 285000.00
        ValOtroIm: 0.00
        ValTolFac: 1785000.00
        CUFE: $ANNEX_CUFE
        https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=$ANNEX_CUFE
    """.trimIndent()

    @Test
    fun `lee el ejemplo del anexo tecnico`() {
        val invoice = ColombiaQrParser.parse(annexExample)!!

        assertEquals("CO", invoice.country)
        assertEquals(CaptureSource.QR, invoice.source)
        assertEquals("323200000129", invoice.invoiceNumber)
        assertEquals("700085371", invoice.issuerTaxId)
        assertEquals(BigDecimal("1785000.00"), invoice.total)
        assertEquals(BigDecimal("285000.00"), invoice.tax)
        assertEquals(ANNEX_CUFE, invoice.documentKey)
    }

    @Test
    fun `una fecha imposible queda vacia en vez de fallar`() {
        assertNull(ColombiaQrParser.parse(annexExample)!!.issueDate)
    }

    @Test
    fun `lee la fecha en formato AAAA-MM-DD`() {
        val invoice = ColombiaQrParser.parse(annexExample.replace("2019-16-01", "2026-11-10"))!!

        assertEquals(LocalDate.of(2026, 11, 10), invoice.issueDate)
    }

    @Test
    fun `no conserva el documento del comprador`() {
        val invoice = ColombiaQrParser.parse(annexExample)!!

        assertFalse(invoice.toString().contains("800199436"))
    }

    @Test
    fun `acepta los datos en una sola linea separados por espacios`() {
        val oneLine = annexExample.lines().joinToString(" ")

        val invoice = ColombiaQrParser.parse(oneLine)!!

        assertEquals("323200000129", invoice.invoiceNumber)
        assertEquals(BigDecimal("1785000.00"), invoice.total)
        assertEquals(ANNEX_CUFE, invoice.documentKey)
    }

    @Test
    fun `acepta el signo igual, etiquetas sin espacio y otras mayusculas`() {
        val variant = "numfac=SETP990000123\nFECFAC:2026-11-10\nValIva=19000.00\nValTolFac:119000.00"

        val invoice = ColombiaQrParser.parse(variant)!!

        assertEquals("SETP990000123", invoice.invoiceNumber)
        assertEquals(LocalDate.of(2026, 11, 10), invoice.issueDate)
        assertEquals(BigDecimal("19000.00"), invoice.tax)
        assertEquals(BigDecimal("119000.00"), invoice.total)
        assertNull(invoice.documentKey)
    }

    @Test
    fun `lee el CUDE de un documento equivalente`() {
        val invoice = ColombiaQrParser.parse("NumFac: POS123\nCUDE: abc123def456")!!

        assertEquals("abc123def456", invoice.documentKey)
    }

    @Test
    fun `de un QR que solo trae la URL de consulta obtiene el CUFE`() {
        val url = "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey=$ANNEX_CUFE"

        val invoice = ColombiaQrParser.parse(url)!!

        assertEquals(ANNEX_CUFE, invoice.documentKey)
        assertNull(invoice.invoiceNumber)
        assertNull(invoice.total)
        assertNull(invoice.tax)
    }

    @Test
    fun `un valor que no es un numero queda vacio`() {
        val invoice = ColombiaQrParser.parse("NumFac: A1\nValIva: abc\nValTolFac: -5.00")!!

        assertNull(invoice.tax)
        assertNull(invoice.total)
    }

    @Test
    fun `rechaza textos que no son un QR de factura colombiana`() {
        assertNull(ColombiaQrParser.parse(""))
        assertNull(ColombiaQrParser.parse("https://example.org/promo"))
        assertNull(ColombiaQrParser.parse("20123456789|01|F001|1234|18.00|118.00|2026-11-10|"))
    }

    private companion object {
        const val ANNEX_CUFE = "e5bac48e354bc907bccff0ea7d45fbf784f0a8e7243b58337361e1fbd430489d"
    }
}
