package com.vueltax.domain.rules

import com.vueltax.domain.model.ExitMode
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSetParsingTest {
    /** Las pruebas corren con `android/domain` como directorio de trabajo. */
    private fun repoRuleSet(country: String): RuleSet {
        val file = File("../../rules/$country.json")
        assertTrue("No se encuentra ${file.absolutePath}", file.exists())
        return RuleSet.parse(file.readText())
    }

    @Test
    fun `lee los archivos de reglas del repositorio`() {
        val expected = mapOf("CO" to "COP", "PE" to "PEN", "MX" to "MXN")
        for ((country, currency) in expected) {
            val ruleSet = repoRuleSet(country)
            assertEquals(country, ruleSet.country)
            assertEquals(currency, ruleSet.currency)
            assertTrue(ruleSet.exitModes.isNotEmpty())
            assertTrue(ruleSet.version >= 1)
        }
    }

    @Test
    fun `las reglas de Colombia usan una unidad tributaria con valor`() {
        val unit = repoRuleSet("CO").amountUnit

        assertEquals(AmountUnitType.TAX_UNIT, unit.type)
        val value = unit.value
        assertTrue(value != null && value.signum() > 0)
    }

    @Test
    fun `interpreta todos los campos del schema`() {
        val ruleSet = RuleSet.parse(SAMPLE)

        assertEquals("CO", ruleSet.country)
        assertEquals(AuthorityType.TAX_AUTHORITY, ruleSet.refundAuthority.type)
        assertEquals("UVT", ruleSet.amountUnit.code)
        assertEquals(2026, ruleSet.amountUnit.year)
        assertEquals(MinPurchaseScope.PER_STORE, ruleSet.minPurchase?.scope)
        assertEquals(true, ruleSet.minPurchase?.electronicPaymentOnly)
        assertEquals(MaxRefundScope.PER_REQUEST, ruleSet.maxRefund?.scope)
        assertNull(ruleSet.operatorFeePct)
        assertEquals(StayDays(min = 2, max = null), ruleSet.stayDays)
        assertEquals(listOf(ExitMode.AIR, ExitMode.SEA, ExitMode.LAND), ruleSet.exitModes)
        assertEquals(RefundPointType.AIRPORT, ruleSet.refundPoints.single().type)
        assertEquals("BOG", ruleSet.refundPoints.single().iata)
        assertNull(ruleSet.sources.single().retrievedAt)
        assertNull(ruleSet.lastVerified)
        assertEquals(LocalDate.of(2026, 1, 1), ruleSet.validFrom)
        assertEquals(emptyList<String>(), ruleSet.verificationNotes)
    }

    @Test
    fun `lee los numeros sin perder precision`() {
        val ruleSet = RuleSet.parse(SAMPLE)

        assertEquals(BigDecimal("19.5"), ruleSet.standardRate)
        assertEquals(BigDecimal("52374.123456789012345"), ruleSet.amountUnit.value)
        assertMoney("157122.37", ruleSet.toLocalCurrency(BigDecimal("3")))
    }

    private companion object {
        val SAMPLE = """
            {
              "${'$'}schema": "./schema.json",
              "country": "CO",
              "currency": "COP",
              "taxName": "IVA",
              "standardRate": 19.5,
              "refundAuthority": { "type": "tax_authority", "name": "Autoridad de prueba" },
              "amountUnit": {
                "type": "tax_unit",
                "code": "UVT",
                "value": 52374.123456789012345,
                "year": 2026
              },
              "minPurchase": { "amount": 3, "scope": "per_store", "electronicPaymentOnly": true },
              "maxRefund": { "amount": 200, "scope": "per_request" },
              "operatorFeePct": null,
              "stayDays": { "min": 2, "max": null },
              "exitModes": ["air", "sea", "land"],
              "requiresAuthorizedStore": false,
              "requiresIntentBeforeInvoice": false,
              "eligibleGoods": ["taxed_goods"],
              "excludedGoods": [],
              "excludedServices": ["all_services"],
              "requiredDocuments": ["passport"],
              "refundPoints": [{ "type": "airport", "iata": "BOG", "name": "Aeropuerto de prueba" }],
              "sources": [{ "title": "Fuente", "url": "https://example.org/", "retrievedAt": null }],
              "lastVerified": null,
              "version": 1,
              "validFrom": "2026-01-01",
              "needsVerification": true
            }
        """.trimIndent()
    }
}
