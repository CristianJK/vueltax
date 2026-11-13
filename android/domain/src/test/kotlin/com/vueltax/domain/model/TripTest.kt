package com.vueltax.domain.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TripTest {
    private val arrival = LocalDate.of(2026, 11, 10)

    private fun trip(departure: LocalDate) = Trip(
        countryCode = "CO",
        arrivalDate = arrival,
        departureDate = departure,
        exitMode = ExitMode.AIR,
    )

    @Test
    fun `la estadia cuenta el dia de llegada y el de salida`() {
        assertEquals(1, trip(arrival).stayDays)
        assertEquals(6, trip(arrival.plusDays(5)).stayDays)
    }

    @Test
    fun `rechaza una salida anterior a la llegada`() {
        assertThrows(IllegalArgumentException::class.java) {
            trip(arrival.minusDays(1))
        }
    }
}
