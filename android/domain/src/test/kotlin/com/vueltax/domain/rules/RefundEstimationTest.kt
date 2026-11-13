package com.vueltax.domain.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundEstimationTest {
    private val noRates = RateTable(emptyList())

    // Colombia: IVA 19 %, tope de 200 UVT = 200 x 52 374 = 10 474 800 COP, sin comisión.

    @Test
    fun `CO - deduce el IVA del total cuando la factura no lo trae`() {
        // 238 000 x 19 / 119 = 38 000
        val estimate = estimateRefund(listOf(purchase("238000", "COP")), colombia, noRates, "COP", NOV_12)

        assertMoney("38000", estimate.purchases.single().recoverableTax)
        assertMoney("38000", estimate.gross)
        assertMoney("0", estimate.operatorFee)
        assertMoney("38000", estimate.net)
        assertFalse(estimate.capApplied)
    }

    @Test
    fun `CO - usa el IVA de la factura cuando lo trae`() {
        val purchases = listOf(purchase("238000", "COP", tax = "30000.50"))

        val estimate = estimateRefund(purchases, colombia, noRates, "COP", NOV_12)

        assertMoney("30000.50", estimate.net)
    }

    @Test
    fun `CO - aplica el tope de 200 UVT por solicitud`() {
        val purchases = listOf("Uno", "Dos", "Tres").map {
            purchase("23800000", "COP", tax = "3800000", storeName = it)
        }

        val estimate = estimateRefund(purchases, colombia, noRates, "COP", NOV_12)

        assertMoney("11400000", estimate.recoverableTax)
        assertMoney("10474800", estimate.cap)
        assertMoney("10474800", estimate.gross)
        assertMoney("10474800", estimate.net)
        assertTrue(estimate.capApplied)
    }

    @Test
    fun `CO - justo en el tope no se recorta`() {
        val purchases = listOf("Uno", "Dos").map {
            purchase("40000000", "COP", tax = "5237400", storeName = it)
        }

        val estimate = estimateRefund(purchases, colombia, noRates, "COP", NOV_12)

        assertMoney("10474800", estimate.gross)
        assertFalse(estimate.capApplied)
    }

    @Test
    fun `CO - convierte con la tasa del dia de compra y con la de hoy`() {
        val rates = RateTable(
            listOf(usdRate("COP", "4000", NOV_10), usdRate("COP", "4200", NOV_12)),
        )
        val purchases = listOf(purchase("238000", "COP", date = NOV_10))

        // Hoy no hay tasa publicada: se usa la más reciente anterior (4 200).
        val estimate = estimateRefund(purchases, colombia, rates, "USD", NOV_12.plusDays(3))

        assertMoney("9.50", estimate.netAtPurchaseDateRates) // 38 000 / 4 000
        assertMoney("9.05", estimate.netAtTodayRate) // 38 000 / 4 200 = 9.0476...
    }

    @Test
    fun `CO - cruza por la moneda base para otras monedas del viajero`() {
        val rates = RateTable(
            listOf(
                usdRate("COP", "4000", NOV_10),
                usdRate("COP", "4200", NOV_12),
                usdRate("EUR", "0.9", NOV_10),
            ),
        )
        val purchases = listOf(purchase("238000", "COP", date = NOV_10))

        val estimate = estimateRefund(purchases, colombia, rates, "EUR", NOV_12)

        assertMoney("8.55", estimate.netAtPurchaseDateRates) // 38 000 x 0.9 / 4 000
        assertMoney("8.14", estimate.netAtTodayRate) // 38 000 x 0.9 / 4 200 = 8.1428...
    }

    @Test
    fun `CO - el tope tambien recorta el equivalente del dia de compra`() {
        val rates = RateTable(listOf(usdRate("COP", "4000", NOV_10)))
        val purchases = listOf("Uno", "Dos", "Tres").map {
            purchase("23800000", "COP", tax = "3800000", storeName = it)
        }

        val estimate = estimateRefund(purchases, colombia, rates, "USD", NOV_10)

        // 10 474 800 / 4 000 = 2 618.70 por ambos caminos.
        assertMoney("2618.70", estimate.netAtPurchaseDateRates)
        assertMoney("2618.70", estimate.netAtTodayRate)
    }

    @Test
    fun `CO - sin tasa de cambio los equivalentes quedan vacios`() {
        val rates = RateTable(listOf(usdRate("COP", "4200", NOV_12)))
        val purchases = listOf(purchase("238000", "COP", date = NOV_10))

        val inEuros = estimateRefund(purchases, colombia, rates, "EUR", NOV_12)
        val inDollars = estimateRefund(purchases, colombia, rates, "USD", NOV_12)

        assertNull(inEuros.netAtPurchaseDateRates)
        assertNull(inEuros.netAtTodayRate)
        // La compra es anterior a la primera tasa disponible, pero hoy sí hay tasa.
        assertNull(inDollars.netAtPurchaseDateRates)
        assertMoney("9.05", inDollars.netAtTodayRate)
    }

    @Test
    fun `CO - si el viajero usa la moneda local el equivalente es el neto`() {
        val estimate = estimateRefund(listOf(purchase("238000", "COP")), colombia, noRates, "COP", NOV_12)

        assertMoney("38000", estimate.netAtPurchaseDateRates)
        assertMoney("38000", estimate.netAtTodayRate)
    }

    // Perú: IGV 18 %, sin tope ni comisión.

    @Test
    fun `PE - suma solo las facturas que califican`() {
        val withTax = purchase("118.00", "PEN", tax = "18.00", storeName = "Uno")
        val withoutTax = purchase("236.00", "PEN", storeName = "Dos") // 236 x 18 / 118 = 36
        val notAuthorized = purchase("500.00", "PEN", storeName = "Tres").copy(authorizedStore = false)

        val estimate = estimateRefund(
            listOf(withTax, withoutTax, notAuthorized),
            peru,
            noRates,
            "PEN",
            NOV_12,
        )

        assertMoney("54", estimate.recoverableTax)
        assertNull(estimate.cap)
        assertMoney("54", estimate.gross)
        assertMoney("0", estimate.operatorFee)
        assertMoney("54", estimate.net)
        assertMoney("0", estimate.purchases[2].recoverableTax)
        assertTrue(estimate.purchases[2].evaluation is PurchaseEvaluation.NotQualified)
    }

    @Test
    fun `PE - redondea el IGV deducido a centimos`() {
        // 100 x 18 / 118 = 15.2542...
        val estimate = estimateRefund(listOf(purchase("100.00", "PEN")), peru, noRates, "PEN", NOV_12)

        assertMoney("15.25", estimate.net)
    }

    @Test
    fun `PE - una factura a la que le faltan datos no suma todavia`() {
        val pending = purchase("236.00", "PEN").copy(intentDeclaredBeforeInvoice = null)

        val estimate = estimateRefund(listOf(pending), peru, noRates, "PEN", NOV_12)

        assertMoney("0", estimate.net)
        assertTrue(estimate.purchases.single().evaluation is PurchaseEvaluation.NeedsInfo)
    }

    // México: IVA 16 %, comisión del operador del 35 %, mínimo por tienda.

    @Test
    fun `MX - descuenta la comision del operador`() {
        val purchases = listOf(purchase("2320", "MXN", tax = "320"))

        val estimate = estimateRefund(purchases, mexico, noRates, "MXN", NOV_12)

        assertMoney("320", estimate.gross)
        assertMoney("112", estimate.operatorFee) // 320 x 35 %
        assertMoney("208", estimate.net)
    }

    @Test
    fun `MX - redondea la comision hacia arriba en el medio centavo`() {
        val purchases = listOf(purchase("1500", "MXN", tax = "100.10"))

        val estimate = estimateRefund(purchases, mexico, noRates, "MXN", NOV_12)

        assertMoney("35.04", estimate.operatorFee) // 35.035
        assertMoney("65.06", estimate.net)
    }

    @Test
    fun `MX - la comision tambien reduce los equivalentes`() {
        val rates = RateTable(
            listOf(usdRate("MXN", "18.5", NOV_10), usdRate("MXN", "20", NOV_12)),
        )
        val purchases = listOf(purchase("2320", "MXN", tax = "320", date = NOV_10))

        val estimate = estimateRefund(purchases, mexico, rates, "USD", NOV_12)

        assertMoney("11.24", estimate.netAtPurchaseDateRates) // 208 / 18.5 = 11.2432...
        assertMoney("10.40", estimate.netAtTodayRate) // 208 / 20
    }

    @Test
    fun `MX - solo cuentan las tiendas que llegan al minimo`() {
        val purchases = listOf(
            purchase("700", "MXN", storeName = "Uno"), // 700 x 16 / 116 = 96.55
            purchase("500", "MXN", storeName = "Uno"), // 500 x 16 / 116 = 68.97
            purchase("300", "MXN", storeName = "Dos"),
        )

        val estimate = estimateRefund(purchases, mexico, noRates, "MXN", NOV_12)

        assertMoney("165.52", estimate.gross)
        assertMoney("57.93", estimate.operatorFee) // 57.932
        assertMoney("107.59", estimate.net)
        assertMoney("0", estimate.purchases[2].recoverableTax)
    }

    @Test
    fun `MX - una compra bajo el minimo no devuelve nada`() {
        val estimate = estimateRefund(listOf(purchase("700", "MXN")), mexico, noRates, "MXN", NOV_12)

        assertMoney("0", estimate.gross)
        assertMoney("0", estimate.net)
        assertMoney("0", estimate.netAtPurchaseDateRates)
    }

    @Test
    fun `sin facturas todo es cero`() {
        val estimate = estimateRefund(emptyList(), colombia, noRates, "COP", NOV_12)

        assertEquals(emptyList<PurchaseEstimate>(), estimate.purchases)
        assertMoney("0", estimate.recoverableTax)
        assertMoney("0", estimate.net)
        assertMoney("0", estimate.netAtTodayRate)
    }
}
