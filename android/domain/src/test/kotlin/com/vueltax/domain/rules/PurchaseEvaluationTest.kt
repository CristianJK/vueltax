package com.vueltax.domain.rules

import com.vueltax.domain.model.PurchaseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseEvaluationTest {
    // Colombia: mínimo de 3 UVT por factura = 3 x 52 374 = 157 122 COP.

    @Test
    fun `CO - factura justo en el minimo califica`() {
        val result = evaluatePurchase(purchase("157122.00", "COP"), colombia)

        assertEquals(PurchaseEvaluation.Qualified, result)
    }

    @Test
    fun `CO - factura un centavo por debajo del minimo no califica`() {
        val issue = issuesOf(evaluatePurchase(purchase("157121.99", "COP"), colombia)).single()

        issue as PurchaseIssue.BelowMinimum
        assertMoney("0.01", issue.missing)
        assertMoney("157122", issue.minimum)
        assertEquals(MinPurchaseScope.PER_INVOICE, issue.scope)
    }

    @Test
    fun `CO - dice cuanto falta para el minimo`() {
        val issue = issuesOf(evaluatePurchase(purchase("100000", "COP"), colombia)).single()

        assertEquals(
            "Te faltan 57122.00 COP para el mínimo de 157122.00 COP por factura.",
            issue.message,
        )
    }

    @Test
    fun `CO - el minimo es por factura y no suma otras compras de la tienda`() {
        val first = purchase("100000", "COP")
        val second = purchase("100000.50", "COP")

        val result = evaluatePurchase(first, colombia, listOf(first, second))

        assertTrue(result is PurchaseEvaluation.NotQualified)
    }

    @Test
    fun `CO - los servicios no califican`() {
        val service = purchase("500000", "COP").copy(kind = PurchaseKind.SERVICE, category = null)

        assertEquals(
            listOf(PurchaseIssue.ServiceNotRefundable),
            issuesOf(evaluatePurchase(service, colombia)),
        )
    }

    @Test
    fun `CO - un bien exento esta excluido`() {
        val exempt = purchase("500000", "COP").copy(category = "exempt_goods")

        assertEquals(
            listOf(PurchaseIssue.CategoryExcluded("exempt_goods")),
            issuesOf(evaluatePurchase(exempt, colombia)),
        )
    }

    @Test
    fun `CO - una categoria que no esta en la lista no califica`() {
        val other = purchase("500000", "COP").copy(category = "otra_categoria")

        assertEquals(
            listOf(PurchaseIssue.CategoryNotEligible("otra_categoria")),
            issuesOf(evaluatePurchase(other, colombia)),
        )
    }

    @Test
    fun `CO - sin tipo ni categoria pide la informacion que falta`() {
        val noKind = purchase("500000", "COP").copy(kind = null)
        val noCategory = purchase("500000", "COP").copy(category = null)

        assertEquals(
            PurchaseEvaluation.NeedsInfo(listOf(PurchaseField.KIND)),
            evaluatePurchase(noKind, colombia),
        )
        assertEquals(
            PurchaseEvaluation.NeedsInfo(listOf(PurchaseField.CATEGORY)),
            evaluatePurchase(noCategory, colombia),
        )
    }

    @Test
    fun `CO - no exige tienda autorizada ni intencion previa`() {
        val unknown = purchase("500000", "COP").copy(
            authorizedStore = null,
            intentDeclaredBeforeInvoice = null,
            paidElectronically = null,
        )

        assertEquals(PurchaseEvaluation.Qualified, evaluatePurchase(unknown, colombia))
    }

    @Test
    fun `CO - factura en otra moneda no califica`() {
        val result = evaluatePurchase(purchase("500", "USD"), colombia)

        assertEquals(listOf(PurchaseIssue.WrongCurrency("USD", "COP")), issuesOf(result))
    }

    // Perú: mínimo de 100 PEN por factura, tienda inscrita e intención previa.

    @Test
    fun `PE - factura justo en el minimo califica`() {
        assertEquals(PurchaseEvaluation.Qualified, evaluatePurchase(purchase("100.00", "PEN"), peru))
    }

    @Test
    fun `PE - factura por debajo del minimo dice cuanto falta`() {
        val issue = issuesOf(evaluatePurchase(purchase("99.99", "PEN"), peru)).single()

        assertEquals("Te faltan 0.01 PEN para el mínimo de 100.00 PEN por factura.", issue.message)
    }

    @Test
    fun `PE - sin saber de la tienda ni de la intencion pide ambos datos`() {
        val unknown = purchase("200", "PEN").copy(
            authorizedStore = null,
            intentDeclaredBeforeInvoice = null,
        )

        assertEquals(
            PurchaseEvaluation.NeedsInfo(
                listOf(PurchaseField.AUTHORIZED_STORE, PurchaseField.INTENT_BEFORE_INVOICE),
            ),
            evaluatePurchase(unknown, peru),
        )
    }

    @Test
    fun `PE - tienda no autorizada no califica aunque falten otros datos`() {
        val notAuthorized = purchase("200", "PEN").copy(
            authorizedStore = false,
            intentDeclaredBeforeInvoice = null,
        )

        assertEquals(
            listOf(PurchaseIssue.StoreNotAuthorized),
            issuesOf(evaluatePurchase(notAuthorized, peru)),
        )
    }

    @Test
    fun `PE - sin intencion previa no califica`() {
        val noIntent = purchase("200", "PEN").copy(intentDeclaredBeforeInvoice = false)

        assertEquals(
            listOf(PurchaseIssue.IntentNotDeclared),
            issuesOf(evaluatePurchase(noIntent, peru)),
        )
    }

    @Test
    fun `PE - reune todos los incumplimientos`() {
        val bad = purchase("50", "PEN").copy(
            authorizedStore = false,
            intentDeclaredBeforeInvoice = false,
        )

        val issues = issuesOf(evaluatePurchase(bad, peru))

        assertEquals(3, issues.size)
        assertTrue(issues[0] is PurchaseIssue.BelowMinimum)
        assertEquals(PurchaseIssue.StoreNotAuthorized, issues[1])
        assertEquals(PurchaseIssue.IntentNotDeclared, issues[2])
    }

    // México: mínimo de 1 200 MXN por establecimiento, solo con pago electrónico.

    @Test
    fun `MX - una sola factura por debajo del minimo dice cuanto falta`() {
        val only = purchase("700", "MXN")

        val issue = issuesOf(evaluatePurchase(only, mexico)).single()

        assertEquals(
            "Te faltan 500.00 MXN para el mínimo de 1200.00 MXN por establecimiento.",
            issue.message,
        )
    }

    @Test
    fun `MX - las compras de la misma tienda se suman hasta el minimo`() {
        val first = purchase("700", "MXN", storeName = "Tienda Uno")
        val second = purchase("500", "MXN", storeName = "  tienda uno ")
        val all = listOf(first, second)

        assertEquals(PurchaseEvaluation.Qualified, evaluatePurchase(first, mexico, all))
        assertEquals(PurchaseEvaluation.Qualified, evaluatePurchase(second, mexico, all))
    }

    @Test
    fun `MX - las compras de otra tienda no se suman`() {
        val first = purchase("700", "MXN", storeName = "Tienda Uno")
        val second = purchase("500", "MXN", storeName = "Tienda Dos")

        val result = evaluatePurchase(first, mexico, listOf(first, second))

        assertTrue(result is PurchaseEvaluation.NotQualified)
    }

    @Test
    fun `MX - una compra en efectivo no califica ni suma para el minimo`() {
        val card = purchase("700", "MXN")
        val cash = purchase("500", "MXN").copy(paidElectronically = false)
        val all = listOf(card, cash)

        assertEquals(
            listOf(PurchaseIssue.NotPaidElectronically),
            issuesOf(evaluatePurchase(cash, mexico, all)),
        )
        val cardIssue = issuesOf(evaluatePurchase(card, mexico, all)).single()
        assertMoney("500", (cardIssue as PurchaseIssue.BelowMinimum).missing)
    }

    @Test
    fun `MX - sin saber el medio de pago pide el dato`() {
        val unknown = purchase("2000", "MXN").copy(paidElectronically = null)

        assertEquals(
            PurchaseEvaluation.NeedsInfo(listOf(PurchaseField.PAYMENT_METHOD)),
            evaluatePurchase(unknown, mexico),
        )
    }

    @Test
    fun `MX - no exige intencion previa`() {
        val noIntent = purchase("1200", "MXN").copy(intentDeclaredBeforeInvoice = null)

        assertEquals(PurchaseEvaluation.Qualified, evaluatePurchase(noIntent, mexico))
    }

    @Test
    fun `sin reglas de minimo ni de bienes cualquier bien califica`() {
        val open = mexico.copy(
            minPurchase = null,
            requiresAuthorizedStore = false,
            eligibleGoods = emptyList(),
        )
        val anything = purchase("1", "MXN").copy(category = null, paidElectronically = null)

        assertEquals(PurchaseEvaluation.Qualified, evaluatePurchase(anything, open))
    }
}
