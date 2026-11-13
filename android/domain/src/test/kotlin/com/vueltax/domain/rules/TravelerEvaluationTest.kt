package com.vueltax.domain.rules

import com.vueltax.domain.model.ExitMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TravelerEvaluationTest {
    // Colombia

    @Test
    fun `CO - turista extranjero no residente es elegible por cualquier via`() {
        for (exitMode in ExitMode.entries) {
            val result = evaluateTraveler(traveler(), trip("CO", exitMode = exitMode), colombia)
            assertEquals(TravelerEligibility.Eligible, result)
        }
    }

    @Test
    fun `CO - residente en el pais no es elegible`() {
        val result = evaluateTraveler(traveler(residenceCountry = "CO"), trip("CO"), colombia)

        assertEquals(listOf(TravelerIssue.ResidentInCountry("CO")), issuesOf(result))
    }

    @Test
    fun `CO - nacional que vive fuera no es elegible`() {
        val result = evaluateTraveler(traveler(nationality = "CO"), trip("CO"), colombia)

        assertEquals(listOf(TravelerIssue.NationalOfCountry("CO")), issuesOf(result))
    }

    @Test
    fun `CO - sin limites de estadia cualquier duracion vale`() {
        val longTrip = trip("CO", departure = NOV_10.plusDays(400))

        assertEquals(TravelerEligibility.Eligible, evaluateTraveler(traveler(), longTrip, colombia))
    }

    // Perú

    @Test
    fun `PE - estadia de un dia es demasiado corta`() {
        val result = evaluateTraveler(traveler(), trip("PE", departure = NOV_10), peru)

        assertEquals(listOf(TravelerIssue.StayTooShort(stayDays = 1, minDays = 2)), issuesOf(result))
    }

    @Test
    fun `PE - estadia en los limites de 2 y 60 dias es elegible`() {
        val twoDays = trip("PE", departure = NOV_10.plusDays(1))
        val sixtyDays = trip("PE", departure = NOV_10.plusDays(59))

        assertEquals(TravelerEligibility.Eligible, evaluateTraveler(traveler(), twoDays, peru))
        assertEquals(TravelerEligibility.Eligible, evaluateTraveler(traveler(), sixtyDays, peru))
    }

    @Test
    fun `PE - estadia de 61 dias es demasiado larga`() {
        val result = evaluateTraveler(traveler(), trip("PE", departure = NOV_10.plusDays(60)), peru)

        assertEquals(listOf(TravelerIssue.StayTooLong(stayDays = 61, maxDays = 60)), issuesOf(result))
    }

    @Test
    fun `PE - salida terrestre no esta permitida`() {
        val result = evaluateTraveler(traveler(), trip("PE", exitMode = ExitMode.LAND), peru)

        val expected = TravelerIssue.ExitModeNotAllowed(
            exitMode = ExitMode.LAND,
            allowed = listOf(ExitMode.AIR, ExitMode.SEA),
        )
        assertEquals(listOf(expected), issuesOf(result))
    }

    @Test
    fun `PE - devuelve todos los motivos a la vez`() {
        val resident = traveler(nationality = "PE", residenceCountry = "PE")
        val shortLandTrip = trip("PE", departure = NOV_10, exitMode = ExitMode.LAND)

        val issues = issuesOf(evaluateTraveler(resident, shortLandTrip, peru))

        assertEquals(4, issues.size)
        assertTrue(issues.all { it.message.isNotBlank() })
    }

    // México

    @Test
    fun `MX - salida aerea y maritima son elegibles`() {
        for (exitMode in listOf(ExitMode.AIR, ExitMode.SEA)) {
            val result = evaluateTraveler(traveler(), trip("MX", exitMode = exitMode), mexico)
            assertEquals(TravelerEligibility.Eligible, result)
        }
    }

    @Test
    fun `MX - salida terrestre explica las vias permitidas`() {
        val result = evaluateTraveler(traveler(), trip("MX", exitMode = ExitMode.LAND), mexico)

        val message = issuesOf(result).single().message
        assertTrue(message, message.contains("terrestre"))
        assertTrue(message, message.contains("aérea, marítima"))
    }

    @Test
    fun `rechaza reglas de un pais distinto al del viaje`() {
        assertThrows(IllegalArgumentException::class.java) {
            evaluateTraveler(traveler(), trip("PE"), colombia)
        }
    }
}
