package app.vueltax.backend

import app.vueltax.backend.rates.FrankfurterSource
import app.vueltax.backend.rates.RateQuote
import app.vueltax.backend.rates.RateRepository
import app.vueltax.backend.rates.RateSource
import app.vueltax.backend.rates.RatesUpdater
import app.vueltax.backend.rates.TrmSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class RatesTest {
    @BeforeTest
    fun reset() = TestDatabase.reset()

    private val repository = RateRepository(TestDatabase.database)

    private fun client(requests: MutableList<HttpRequestData> = mutableListOf(), body: (HttpRequestData) -> String) =
        HttpClient(MockEngine { request ->
            requests += request
            respond(body(request))
        }) { expectSuccess = true }

    @Test
    fun `frankfurter pide base y quotes y descarta lo que no se pidio`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val http = client(requests) { request ->
            val base = request.url.parameters["base"]
            """
            [{"date":"2026-11-13","base":"$base","quote":"COP","rate":3293.28},
             {"date":"2026-11-12","base":"$base","quote":"PEN","rate":3.4131},
             {"date":"2026-11-12","base":"$base","quote":"AAVE","rate":0.008773}]
            """
        }
        val source = FrankfurterSource(http, "https://rates.test/", listOf("USD", "EUR"), listOf("COP", "PEN", "USD"))

        val quotes = source.fetch()

        assertEquals(listOf("/v2/rates", "/v2/rates"), requests.map { it.url.encodedPath })
        assertEquals(listOf("COP,PEN", "COP,PEN,USD"), requests.map { it.url.parameters["quotes"] })
        assertEquals(4, quotes.size)
        assertEquals(
            RateQuote(LocalDate.parse("2026-11-13"), "USD", "COP", BigDecimal("3293.28"), "frankfurter"),
            quotes.first(),
        )
        assertTrue(quotes.none { it.quote == "AAVE" })
    }

    @Test
    fun `frankfurter falla con una respuesta de error`() {
        val http = HttpClient(MockEngine { respond("ups", HttpStatusCode.BadGateway) }) { expectSuccess = true }
        val source = FrankfurterSource(http, "https://rates.test", listOf("USD"), listOf("COP"))
        assertFailsWith<ServerResponseException> { runBlocking { source.fetch() } }
    }

    @Test
    fun `la TRM se guarda para cada dia de su vigencia`() = runBlocking {
        val requests = mutableListOf<HttpRequestData>()
        val http = client(requests) {
            """
            [{"valor":"3301.91","unidad":"COP","vigenciadesde":"2026-11-14T00:00:00.000","vigenciahasta":"2026-11-16T00:00:00.000"},
             {"valor":"3297.07","unidad":"COP","vigenciadesde":"2026-11-13T00:00:00.000","vigenciahasta":"2026-11-13T00:00:00.000"},
             {"valor":"3000.00","unidad":"COP","vigenciadesde":"2026-10-01T00:00:00.000","vigenciahasta":"2026-10-01T00:00:00.000"}]
            """
        }
        val source = TrmSource(http, "https://trm.test/resource.json", today = { LocalDate.parse("2026-11-14") })

        val quotes = source.fetch()

        assertEquals("vigenciadesde DESC", requests.single().url.parameters["\$order"])
        assertEquals(
            listOf("2026-11-14=3301.91", "2026-11-15=3301.91", "2026-11-16=3301.91", "2026-11-13=3297.07"),
            quotes.map { "${it.date}=${it.rate}" },
        )
        assertTrue(quotes.all { it.base == "USD" && it.quote == "COP" && it.source == "trm-datos.gov.co" })
    }

    @Test
    fun `la TRM falla si el dataset llega vacio`() {
        val source = TrmSource(client { "[]" }, "https://trm.test/resource.json")
        assertFailsWith<IllegalStateException> { runBlocking { source.fetch() } }
    }

    private class FlakySource(override val name: String, private val failures: Int) : RateSource {
        var calls = 0
        override suspend fun fetch(): List<RateQuote> {
            calls++
            if (calls <= failures) throw IOException("fuente caída")
            return listOf(RateQuote(LocalDate.parse("2026-11-12"), "USD", "COP", BigDecimal("3300"), name))
        }
    }

    @Test
    fun `reintenta una fuente que falla y registra la fuente de cada tasa`() = runBlocking {
        val flaky = FlakySource("inestable", failures = 2)
        val down = FlakySource("caida", failures = Int.MAX_VALUE)
        val steady = FlakySource("estable", failures = 0)
        val updater = RatesUpdater(listOf(flaky, down, steady), repository, maxAttempts = 3, initialBackoff = 1.milliseconds)

        val results = updater.runOnce().associateBy { it.source }

        assertEquals(3, results.getValue("inestable").attempts)
        assertTrue(results.getValue("inestable").ok)
        assertEquals(false, results.getValue("caida").ok)
        assertEquals(3, down.calls)
        assertEquals(1, results.getValue("estable").attempts)

        // Una fuente caída no impide guardar las demás.
        val stored = repository.latestOnOrBefore("USD", LocalDate.parse("2026-11-12"))
        assertEquals(listOf("estable", "inestable"), stored.map { it.source })
    }

    @Test
    fun `volver a guardar la misma tasa la actualiza sin duplicarla`() = runBlocking {
        val date = LocalDate.parse("2026-11-12")
        repository.save(listOf(RateQuote(date, "USD", "COP", BigDecimal("3300"), "frankfurter")))
        repository.save(listOf(RateQuote(date, "USD", "COP", BigDecimal("3310.5"), "frankfurter")))

        val stored = repository.latestOnOrBefore("USD", date).single()
        assertEquals(0, BigDecimal("3310.5").compareTo(stored.rate))
    }

    @Test
    fun `calcula la espera hasta la siguiente ejecucion diaria`() {
        val time = LocalTime.parse("23:30")
        val before = OffsetDateTime.parse("2026-11-12T20:00:00Z")
        val after = OffsetDateTime.parse("2026-11-12T23:45:00Z")
        assertEquals(210, RatesUpdater.untilNext(time, before).toMinutes())
        assertEquals(23 * 60 + 45, RatesUpdater.untilNext(time, after).toMinutes())
    }
}
