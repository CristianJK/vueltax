package app.vueltax.backend

import app.vueltax.backend.rates.RateQuote
import app.vueltax.backend.rates.RateRepository
import app.vueltax.backend.refund.RefundPointInput
import app.vueltax.backend.refund.RefundPointRepository
import app.vueltax.backend.rules.RuleSetRepository
import app.vueltax.backend.rules.RuleSetValidation
import app.vueltax.backend.rules.RuleSetValidator
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublicApiTest {
    private val validator = RuleSetValidator()

    @BeforeTest
    fun reset() = TestDatabase.reset()

    private fun loadRules(json: String) = runBlocking {
        val ruleSet = (validator.validate(json) as RuleSetValidation.Valid).ruleSet
        RuleSetRepository(TestDatabase.database).save(ruleSet)
    }

    private fun loadRates(vararg quotes: RateQuote) = runBlocking {
        RateRepository(TestDatabase.database).save(quotes.toList())
    }

    private fun rate(date: String, quote: String, rate: String, source: String = "frankfurter") =
        RateQuote(LocalDate.parse(date), "USD", quote, BigDecimal(rate), source)

    @Test
    fun `health responde ok con la base de datos arriba`() = apiTest {
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.json().jsonObject["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `countries lista los paises del MVP con su version de reglas`() = apiTest {
        loadRules(RuleFiles.read("CO"))

        val countries = client.get("/v1/countries").json().jsonArray.map { it.jsonObject }
        assertEquals(listOf("CO", "MX", "PE"), countries.map { it["code"]!!.jsonPrimitive.content })
        val colombia = countries.first()
        assertEquals("COP", colombia["currency"]!!.jsonPrimitive.content)
        assertEquals(1, colombia["rulesVersion"]!!.jsonPrimitive.int)
        assertEquals("null", countries.last()["rulesVersion"].toString())
    }

    @Test
    fun `rules devuelve la ultima version y version solo el numero`() = apiTest {
        loadRules(RuleFiles.read("CO"))
        loadRules(RuleFiles.with("CO", "version", "3"))
        loadRules(RuleFiles.with("CO", "version", "2"))

        val rules = client.get("/v1/rules/CO")
        assertEquals(HttpStatusCode.OK, rules.status)
        val body = rules.json().jsonObject
        assertEquals(3, body["version"]!!.jsonPrimitive.int)
        assertEquals("CO", body["country"]!!.jsonPrimitive.content)
        assertEquals("IVA", body["taxName"]!!.jsonPrimitive.content)

        assertEquals("""{"country":"CO","version":3}""", client.get("/v1/rules/co/version").bodyAsText())
    }

    @Test
    fun `rules responde 404 sin reglas y 400 con un pais mal formado`() = apiTest {
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/rules/PE").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/rules/PE/version").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/rules/COL").status)
    }

    @Test
    fun `las respuestas publicas llevan cache-control y etag y admiten 304`() = apiTest {
        loadRules(RuleFiles.read("CO"))

        for (path in listOf("/v1/countries", "/v1/rules/CO", "/v1/rules/CO/version", "/v1/refund-points")) {
            val first = client.get(path)
            assertEquals(HttpStatusCode.OK, first.status, path)
            val etag = assertNotNull(first.headers[HttpHeaders.ETag], path)
            assertTrue(first.headers[HttpHeaders.CacheControl]!!.startsWith("public, max-age="), path)

            val cached = client.get(path) { header(HttpHeaders.IfNoneMatch, etag) }
            assertEquals(HttpStatusCode.NotModified, cached.status, path)
            assertEquals("", cached.bodyAsText(), path)
            assertEquals(etag, cached.headers[HttpHeaders.ETag], path)
        }

        // Al publicarse una versión nueva el ETag cambia y el cliente recibe el contenido.
        val oldEtag = client.get("/v1/rules/CO").headers[HttpHeaders.ETag]!!
        loadRules(RuleFiles.with("CO", "version", "2"))
        val updated = client.get("/v1/rules/CO") { header(HttpHeaders.IfNoneMatch, oldEtag) }
        assertEquals(HttpStatusCode.OK, updated.status)
    }

    @Test
    fun `refund-points filtra por pais`() = apiTest {
        val repository = RefundPointRepository(TestDatabase.database)
        repository.create(RefundPointInput("CO", "BOG", "T1", "Oficina DIAN", 4.70, -74.14, "24 horas"))
        repository.create(RefundPointInput("PE", "LIM", null, "Módulo de devolución", -12.02, -77.11, "06:00-22:00"))

        val all = client.get("/v1/refund-points").json().jsonArray
        assertEquals(2, all.size)

        val colombia = client.get("/v1/refund-points?country=co").json().jsonArray.single().jsonObject
        assertEquals("BOG", colombia["airportIata"]!!.jsonPrimitive.content)
        assertEquals("T1", colombia["terminal"]!!.jsonPrimitive.content)
        assertEquals("24 horas", colombia["openingHours"]!!.jsonPrimitive.content)

        assertEquals(0, client.get("/v1/refund-points?country=MX").json().jsonArray.size)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/refund-points?country=Colombia").status)
    }

    @Test
    fun `rates devuelve la tasa de la fecha o la anterior mas reciente por fuente`() = apiTest {
        loadRates(
            rate("2026-11-05", "COP", "3200.5"),
            rate("2026-11-06", "COP", "3238.09"),
            rate("2026-11-06", "COP", "3240.12", source = "trm-datos.gov.co"),
            rate("2026-11-06", "PEN", "3.4432"),
            rate("2026-11-09", "COP", "3300"),
            rate("2026-10-01", "MXN", "18.5"),
        )

        // Domingo 8: no hay tasa de ese día, se devuelve la del viernes 6.
        val response = client.get("/v1/rates?base=USD&date=2026-11-08")
        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.json().jsonObject
        assertEquals("USD", body["base"]!!.jsonPrimitive.content)
        assertEquals("2026-11-08", body["date"]!!.jsonPrimitive.content)
        val rates = body["rates"]!!.jsonArray.map { it.jsonObject }
        assertEquals(
            listOf("COP/frankfurter/2026-11-06/3238.09", "COP/trm-datos.gov.co/2026-11-06/3240.12", "PEN/frankfurter/2026-11-06/3.4432"),
            rates.map { r -> listOf("quote", "source", "date", "rate").joinToString("/") { r[it]!!.jsonPrimitive.content } },
        )
    }

    @Test
    fun `rates valida parametros y responde 404 sin datos`() = apiTest {
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/rates").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/rates?base=DOLAR").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/v1/rates?base=USD&date=08-11-2026").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/rates?base=USD&date=2026-11-08").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/v1/rates?base=USD").status)
    }

    @Test
    fun `los endpoints publicos solo aceptan GET`() = apiTest {
        // Ktor responde 405 en rutas fijas y 404 en rutas con parámetros; ninguna escribe.
        val rejected = setOf(HttpStatusCode.MethodNotAllowed, HttpStatusCode.NotFound)
        for (path in listOf("/v1/countries", "/v1/rules/CO", "/v1/rules/CO/version", "/v1/refund-points", "/v1/rates")) {
            for (write in listOf(client.post(path), client.put(path), client.delete(path))) {
                assertTrue(write.status in rejected, "$path -> ${write.status}")
            }
        }
    }

    @Test
    fun `CORS esta cerrado por defecto`() = apiTest {
        val response = client.get("/v1/countries") { header(HttpHeaders.Origin, "https://example.com") }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `CORS solo admite los origenes configurados`() =
        apiTest(testSettings(corsAllowedOrigins = listOf("https://admin.example.com"))) {
            val allowed = client.get("/v1/countries") { header(HttpHeaders.Origin, "https://admin.example.com") }
            assertEquals("https://admin.example.com", allowed.headers[HttpHeaders.AccessControlAllowOrigin])

            val other = client.get("/v1/countries") { header(HttpHeaders.Origin, "https://example.com") }
            assertNull(other.headers[HttpHeaders.AccessControlAllowOrigin])
        }

    @Test
    fun `el rate limit corta con 429 y no afecta a health`() = apiTest(testSettings(publicRateLimit = 3)) {
        repeat(3) { assertEquals(HttpStatusCode.OK, client.get("/v1/countries").status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/countries").status)
        assertEquals(HttpStatusCode.TooManyRequests, client.get("/v1/refund-points").status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }
}
