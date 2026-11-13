package app.vueltax.backend

import app.vueltax.backend.rates.RateQuote
import app.vueltax.backend.rates.RateSource
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminApiTest {
    @BeforeTest
    fun reset() = TestDatabase.reset()

    private val refundPoint = """
        {"country":"CO","airportIata":"BOG","terminal":"T1","description":"Oficina DIAN, salidas internacionales",
         "lat":4.7016,"lon":-74.1469,"openingHours":"24 horas"}
    """.trimIndent()

    @Test
    fun `sin token o con token incorrecto responde 401 y no guarda nada`() = apiTest {
        val attempts = listOf(
            client.post("/v1/admin/rules") { setBody(RuleFiles.read("CO")) },
            client.post("/v1/admin/rules") { bearerAuth("otro-token"); setBody(RuleFiles.read("CO")) },
            client.post("/v1/admin/refund-points") { setBody(refundPoint) },
            client.put("/v1/admin/refund-points/1") { setBody(refundPoint) },
            client.delete("/v1/admin/refund-points/1"),
            client.post("/v1/admin/rates/refresh"),
        )
        attempts.forEach { assertEquals(HttpStatusCode.Unauthorized, it.status) }

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/rules/CO").status)
        assertEquals(0, client.get("/v1/refund-points").json().jsonArray.size)
    }

    @Test
    fun `carga los rule sets de rules y los publica`() = apiTest {
        for (country in RuleFiles.countries) {
            val created = client.post("/v1/admin/rules") { asAdmin(); setBody(RuleFiles.read(country)) }
            assertEquals(HttpStatusCode.Created, created.status, country)
            assertEquals(country, client.get("/v1/rules/$country").json().jsonObject["country"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `repetir una version es idempotente y cambiarla sin subir version es conflicto`() = apiTest {
        val original = RuleFiles.read("PE")
        assertEquals(HttpStatusCode.Created, client.post("/v1/admin/rules") { asAdmin(); setBody(original) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/v1/admin/rules") { asAdmin(); setBody(original) }.status)

        val changed = RuleFiles.with("PE", "standardRate", "17")
        assertEquals(HttpStatusCode.Conflict, client.post("/v1/admin/rules") { asAdmin(); setBody(changed) }.status)
        assertEquals(18, client.get("/v1/rules/PE").json().jsonObject["standardRate"]!!.jsonPrimitive.int)
    }

    @Test
    fun `rechaza rule sets invalidos`() = apiTest {
        val personal = client.post("/v1/admin/rules") {
            asAdmin()
            setBody(RuleFiles.with("CO", "passportNumber", "\"X123\""))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, personal.status)
        assertTrue(personal.json().jsonObject["details"]!!.jsonArray.isNotEmpty())

        val unknownCountry = client.post("/v1/admin/rules") {
            asAdmin()
            setBody(RuleFiles.with("CO", "country", "\"AR\""))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, unknownCountry.status)

        val malformed = client.post("/v1/admin/rules") { asAdmin(); setBody("{") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)

        assertEquals(HttpStatusCode.NotFound, client.get("/v1/rules/CO").status)
    }

    @Test
    fun `gestiona puntos de devolucion`() = apiTest {
        val created = client.post("/v1/admin/refund-points") { asAdmin(); setBody(refundPoint) }
        assertEquals(HttpStatusCode.Created, created.status)
        val id = created.json().jsonObject["id"]!!.jsonPrimitive.long

        val duplicate = client.post("/v1/admin/refund-points") { asAdmin(); setBody(refundPoint) }
        assertEquals(HttpStatusCode.Conflict, duplicate.status)

        val updated = client.put("/v1/admin/refund-points/$id") {
            asAdmin()
            setBody(refundPoint.replace("24 horas", "05:00-23:00"))
        }
        assertEquals(HttpStatusCode.OK, updated.status)
        val listed = client.get("/v1/refund-points?country=CO").json().jsonArray.single().jsonObject
        assertEquals("05:00-23:00", listed["openingHours"]!!.jsonPrimitive.content)
        assertEquals(id, listed["id"]!!.jsonPrimitive.long)

        val missing = client.put("/v1/admin/refund-points/9999") { asAdmin(); setBody(refundPoint) }
        assertEquals(HttpStatusCode.NotFound, missing.status)

        assertEquals(HttpStatusCode.OK, client.delete("/v1/admin/refund-points/$id") { asAdmin() }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("/v1/admin/refund-points/$id") { asAdmin() }.status)
        assertEquals(0, client.get("/v1/refund-points").json().jsonArray.size)
    }

    @Test
    fun `rechaza puntos de devolucion invalidos`() = apiTest {
        val invalid = listOf(
            refundPoint.replace("\"BOG\"", "\"bogota\""),
            refundPoint.replace("4.7016", "94.7"),
            refundPoint.replace("\"CO\"", "\"AR\""),
            refundPoint.replace("24 horas", " "),
        )
        for (body in invalid) {
            val response = client.post("/v1/admin/refund-points") { asAdmin(); setBody(body) }
            assertEquals(HttpStatusCode.UnprocessableEntity, response.status, body)
        }
        // Campos desconocidos (p. ej. datos personales) no se aceptan.
        val extra = refundPoint.replace("{\"country\"", "{\"passport\":\"X\",\"country\"")
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/v1/admin/refund-points") { asAdmin(); setBody(extra) }.status,
        )
        assertEquals(HttpStatusCode.BadRequest, client.post("/v1/admin/refund-points") { asAdmin(); setBody("x") }.status)
    }

    @Test
    fun `refresh descarga las tasas y quedan publicadas con su fuente`() {
        val source = object : RateSource {
            override val name = "fuente-de-prueba"
            override suspend fun fetch() =
                listOf(RateQuote(LocalDate.parse("2026-11-12"), "USD", "COP", BigDecimal("3301.91"), name))
        }
        apiTest(sources = listOf(source)) {
            val refresh = client.post("/v1/admin/rates/refresh") { asAdmin() }
            assertEquals(HttpStatusCode.OK, refresh.status)
            val result = refresh.json().jsonArray.single().jsonObject
            assertEquals("true", result["ok"]!!.jsonPrimitive.content)
            assertEquals(1, result["saved"]!!.jsonPrimitive.int)

            val rate = client.get("/v1/rates?base=USD&date=2026-11-12").json()
                .jsonObject["rates"]!!.jsonArray.single().jsonObject
            assertEquals("fuente-de-prueba", rate["source"]!!.jsonPrimitive.content)
            assertEquals("3301.91", rate["rate"]!!.jsonPrimitive.content)
        }
    }
}
