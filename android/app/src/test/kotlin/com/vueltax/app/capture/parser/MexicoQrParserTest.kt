package com.vueltax.app.capture.parser

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class MexicoQrParserTest {
    /** Ejemplo literal del apartado D del Anexo 20 de la RMF 2022 (no es un CFDI real). */
    private val annexExample = "https://verificacfdi.facturaelectronica.sat.gob.mx/default.aspx" +
        "?id=5803EB8D-81CD-4557-8719-26632D2FA434&re=VISM990101474&rr=XAXX010101000" +
        "&tt=0000014300.000000&fe=rH8/bw=="

    @Test
    fun `lee el ejemplo del Anexo 20`() {
        val invoice = MexicoQrParser.parse(annexExample)!!

        assertEquals("MX", invoice.country)
        assertEquals(CaptureSource.QR, invoice.source)
        assertEquals("5803EB8D-81CD-4557-8719-26632D2FA434", invoice.documentKey)
        assertEquals("VISM990101474", invoice.issuerTaxId)
        assertEquals(0, BigDecimal("14300").compareTo(invoice.total))
    }

    @Test
    fun `el QR no trae IVA, fecha ni numero de factura`() {
        val invoice = MexicoQrParser.parse(annexExample)!!

        assertNull(invoice.tax)
        assertNull(invoice.issueDate)
        assertNull(invoice.invoiceNumber)
    }

    @Test
    fun `no conserva el RFC del receptor`() {
        val invoice = MexicoQrParser.parse(annexExample.replace("XAXX010101000", "PEPJ800101AB2"))

        assertFalse(invoice.toString().contains("PEPJ800101AB2"))
    }

    @Test
    fun `acepta el total sin ceros a la izquierda y el UUID en minusculas`() {
        val url = "https://verificacfdi.facturaelectronica.sat.gob.mx/default.aspx" +
            "?id=5803eb8d-81cd-4557-8719-26632d2fa434&re=vism990101474&rr=XAXX010101000&tt=2320.5&fe=AbCd1234"

        val invoice = MexicoQrParser.parse(url)!!

        assertEquals("5803EB8D-81CD-4557-8719-26632D2FA434", invoice.documentKey)
        assertEquals("VISM990101474", invoice.issuerTaxId)
        assertEquals(BigDecimal("2320.5"), invoice.total)
    }

    @Test
    fun `acepta la cadena antigua sin URL`() {
        val legacy = "?re=VISM990101474&rr=XAXX010101000&tt=0000014300.000000" +
            "&id=5803EB8D-81CD-4557-8719-26632D2FA434"

        val invoice = MexicoQrParser.parse(legacy)!!

        assertEquals("5803EB8D-81CD-4557-8719-26632D2FA434", invoice.documentKey)
        assertEquals(0, BigDecimal("14300").compareTo(invoice.total))
    }

    @Test
    fun `un RFC o un total mal formados quedan vacios`() {
        val invoice = MexicoQrParser.parse(
            annexExample.replace("VISM990101474", "123").replace("0000014300.000000", "mucho"),
        )!!

        assertNull(invoice.issuerTaxId)
        assertNull(invoice.total)
    }

    @Test
    fun `rechaza textos que no son un QR de CFDI`() {
        assertNull(MexicoQrParser.parse(""))
        assertNull(MexicoQrParser.parse("https://example.org/?id=123"))
        assertNull(MexicoQrParser.parse("https://example.org/?id=5803EB8D-81CD-4557-8719-26632D2FA434"))
        assertNull(MexicoQrParser.parse(annexExample.replace("5803EB8D-81CD", "5803EB8D")))
        assertNull(MexicoQrParser.parse("20123456789|01|F001|1234|18.00|118.00|2026-11-10|"))
    }

    @Test
    fun `el registro de parsers reconoce el pais de cada formato`() {
        val colombia = "NumFac: A1\nValTolFac: 119000.00"
        val peru = "20123456789|01|F001|1234|18.00|118.00|2026-11-10|"

        assertEquals("MX", InvoiceQrParsers.parse(annexExample)?.country)
        assertEquals("CO", InvoiceQrParsers.parse(colombia)?.country)
        assertEquals("PE", InvoiceQrParsers.parse(peru)?.country)
        assertNull(InvoiceQrParsers.parse("https://example.org/promo"))
    }
}
