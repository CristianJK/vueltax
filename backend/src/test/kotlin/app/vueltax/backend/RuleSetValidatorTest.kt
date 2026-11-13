package app.vueltax.backend

import app.vueltax.backend.rules.RuleSetValidation
import app.vueltax.backend.rules.RuleSetValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleSetValidatorTest {
    private val validator = RuleSetValidator()

    @Test
    fun `los archivos de rules cumplen el schema`() {
        assertEquals(listOf("CO", "MX", "PE"), RuleFiles.countries)
        for (country in RuleFiles.countries) {
            val result = validator.validate(RuleFiles.read(country))
            val valid = assertIs<RuleSetValidation.Valid>(result, "$country: $result")
            assertEquals(country, valid.ruleSet.country)
            assertTrue(valid.ruleSet.version >= 1)
        }
    }

    @Test
    fun `rechaza propiedades fuera del schema como datos personales`() {
        val result = validator.validate(RuleFiles.with("CO", "passportNumber", "\"X123\""))
        val invalid = assertIs<RuleSetValidation.Invalid>(result)
        assertTrue(invalid.errors.any { "passportNumber" in it }, invalid.errors.toString())
    }

    @Test
    fun `rechaza valores invalidos`() {
        val cases = mapOf(
            "country" to "\"COL\"",
            "currency" to "\"pesos\"",
            "version" to "0",
            "exitModes" to "[\"rail\"]",
            "validFrom" to "\"01/01/2026\"",
            "standardRate" to "\"19\"",
        )
        for ((field, value) in cases) {
            assertIs<RuleSetValidation.Invalid>(validator.validate(RuleFiles.with("CO", field, value)), field)
        }
    }

    @Test
    fun `rechaza un rango de estadia incoherente`() {
        val result = validator.validate(RuleFiles.with("CO", "stayDays", """{"min": 10, "max": 5}"""))
        assertIs<RuleSetValidation.Invalid>(result)
    }

    @Test
    fun `distingue JSON mal formado`() {
        assertEquals(RuleSetValidation.Malformed, validator.validate("{ no es json"))
        assertEquals(RuleSetValidation.Malformed, validator.validate("[1, 2]"))
    }
}
