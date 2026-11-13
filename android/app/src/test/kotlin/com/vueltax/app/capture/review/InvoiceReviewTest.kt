package com.vueltax.app.capture.review

import com.vueltax.app.capture.parser.CaptureSource
import com.vueltax.app.capture.parser.ParsedInvoice
import com.vueltax.domain.model.PurchaseKind
import com.vueltax.domain.rules.AmountUnit
import com.vueltax.domain.rules.AmountUnitType
import com.vueltax.domain.rules.MinPurchase
import com.vueltax.domain.rules.MinPurchaseScope
import com.vueltax.domain.rules.PurchaseEvaluation
import com.vueltax.domain.rules.PurchaseField
import com.vueltax.domain.rules.PurchaseIssue
import com.vueltax.domain.rules.RuleSet
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InvoiceReviewTest {
    private val today = LocalDate.of(2026, 11, 12)

    /**
     * Parte de los archivos de reglas del repositorio (las pruebas corren en `android/app`) y
     * fija los valores de los que dependen estas pruebas, para que no cambien al verificarlos.
     */
    private fun rules(country: String): RuleSet = RuleSet.parse(File("../../rules/$country.json").readText()).copy(
        amountUnit = AmountUnit(AmountUnitType.LOCAL_CURRENCY),
        maxRefund = null,
        operatorFeePct = null,
        eligibleGoods = listOf("taxed_goods"),
        excludedGoods = emptyList(),
        excludedServices = listOf(RuleSet.ALL_SERVICES),
    )

    private val peru = rules("PE").copy(
        minPurchase = MinPurchase(BigDecimal("100"), MinPurchaseScope.PER_INVOICE),
        requiresAuthorizedStore = true,
        requiresIntentBeforeInvoice = true,
    )

    private val mexico = rules("MX").copy(
        minPurchase = MinPurchase(BigDecimal("1200"), MinPurchaseScope.PER_STORE, electronicPaymentOnly = true),
        requiresAuthorizedStore = true,
        requiresIntentBeforeInvoice = false,
    )

    private fun draft(country: String, total: String, tax: String = "") = InvoiceDraft(
        country = country,
        storeName = "Tienda Uno",
        invoiceNumber = "F001-1",
        issueDate = "2026-11-10",
        total = total,
        tax = tax,
        kind = PurchaseKind.GOODS,
        category = "taxed_goods",
        paidElectronically = true,
        authorizedStore = true,
        intentDeclaredBeforeInvoice = true,
    )

    private fun review(draft: InvoiceDraft, ruleSet: RuleSet?, others: List<InvoiceDraft> = emptyList()) =
        InvoiceReviewer.review(
            draft = draft,
            ruleSet = ruleSet,
            otherPurchases = others.map { it.toPurchase(tripId = 1, currency = ruleSet!!.currency)!! },
            tripId = 1,
            today = today,
        )

    // Borrador

    @Test
    fun `los datos de un QR sustituyen a lo escrito`() {
        val typed = InvoiceDraft(country = "CO", invoiceNumber = "viejo", total = "1", storeName = "Mi tienda")
        val qr = ParsedInvoice(
            country = "PE",
            source = CaptureSource.QR,
            invoiceNumber = "F001-1234",
            issueDate = LocalDate.of(2026, 11, 10),
            issuerTaxId = "20123456789",
            total = BigDecimal("118.00"),
            tax = BigDecimal("18.00"),
        )

        val merged = typed.mergedWith(qr)

        assertEquals("PE", merged.country)
        assertEquals("F001-1234", merged.invoiceNumber)
        assertEquals("2026-11-10", merged.issueDate)
        assertEquals("118.00", merged.total)
        assertEquals("18.00", merged.tax)
        assertEquals("20123456789", merged.issuerTaxId)
        assertEquals("Mi tienda", merged.storeName)
    }

    @Test
    fun `los datos del OCR solo rellenan los campos vacios`() {
        val typed = InvoiceDraft(country = "MX", total = "2320.00", documentKey = "UUID-DEL-QR")
        val ocr = ParsedInvoice(
            country = "MX",
            source = CaptureSource.OCR,
            issueDate = LocalDate.of(2026, 11, 12),
            total = BigDecimal("9999"),
            tax = BigDecimal("320.00"),
        )

        val merged = typed.mergedWith(ocr)

        assertEquals("2320.00", merged.total)
        assertEquals("320.00", merged.tax)
        assertEquals("2026-11-12", merged.issueDate)
        assertEquals("UUID-DEL-QR", merged.documentKey)
    }

    @Test
    fun `sin total o fecha validos no hay factura que evaluar`() {
        assertNull(draft("PE", total = "").toPurchase(1, "PEN"))
        assertNull(draft("PE", total = "118").copy(issueDate = "10/11/2026").toPurchase(1, "PEN"))
    }

    @Test
    fun `acepta el total escrito con coma decimal y deja el impuesto vacio en null`() {
        val purchase = draft("PE", total = "118,50").toPurchase(1, "PEN")!!

        assertEquals(0, BigDecimal("118.50").compareTo(purchase.total))
        assertNull(purchase.tax)
        assertEquals("PEN", purchase.currency)
    }

    // Resultado en vivo

    @Test
    fun `sin reglas descargadas lo dice`() {
        assertEquals(ReviewResult.NoRules, review(draft("PE", "118"), ruleSet = null))
    }

    @Test
    fun `sin total esta incompleta`() {
        assertEquals(ReviewResult.Incomplete, review(draft("PE", total = ""), peru))
    }

    @Test
    fun `PE - una factura completa califica y muestra el impuesto recuperable`() {
        val result = review(draft("PE", total = "118.00", tax = "18.00"), peru) as ReviewResult.Evaluated

        assertEquals(PurchaseEvaluation.Qualified, result.evaluation)
        assertEquals(0, BigDecimal("18").compareTo(result.recoverableTax))
        assertEquals("PEN", result.currency)
    }

    @Test
    fun `PE - el resultado cambia al corregir un dato`() {
        val base = draft("PE", total = "118.00", tax = "18.00")

        val unknown = review(base.copy(authorizedStore = null), peru) as ReviewResult.Evaluated
        val notAuthorized = review(base.copy(authorizedStore = false), peru) as ReviewResult.Evaluated

        assertEquals(
            PurchaseEvaluation.NeedsInfo(listOf(PurchaseField.AUTHORIZED_STORE)),
            unknown.evaluation,
        )
        assertEquals(
            PurchaseEvaluation.NotQualified(listOf(PurchaseIssue.StoreNotAuthorized)),
            notAuthorized.evaluation,
        )
        assertEquals(0, BigDecimal.ZERO.compareTo(notAuthorized.recoverableTax))
    }

    @Test
    fun `MX - cuenta las facturas ya guardadas de la misma tienda para el minimo`() {
        val newInvoice = draft("MX", total = "500").copy(invoiceNumber = "B-2")
        val saved = draft("MX", total = "700").copy(invoiceNumber = "B-1")

        val alone = review(newInvoice, mexico) as ReviewResult.Evaluated
        val withSaved = review(newInvoice, mexico, others = listOf(saved)) as ReviewResult.Evaluated

        val issue = (alone.evaluation as PurchaseEvaluation.NotQualified).reasons.single()
        assertTrue(issue is PurchaseIssue.BelowMinimum)
        assertEquals(PurchaseEvaluation.Qualified, withSaved.evaluation)
    }
}
