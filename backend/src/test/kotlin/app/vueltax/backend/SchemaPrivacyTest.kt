package app.vueltax.backend

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guarda de la regla de privacidad: el esquema solo tiene datos públicos de referencia. */
class SchemaPrivacyTest {
    private fun strings(sql: String): List<String> = transaction(TestDatabase.database) {
        exec(sql) { rows -> buildList { while (rows.next()) add(rows.getString(1)) } }.orEmpty()
    }

    @Test
    fun `solo existen las tablas de datos publicos`() {
        val tables = strings(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY 1",
        )
        assertEquals(
            listOf("country", "exchange_rate", "flyway_schema_history", "refund_point", "rule_set"),
            tables,
        )
    }

    @Test
    fun `ninguna columna guarda datos personales`() {
        val forbidden = listOf("passport", "pasaporte", "user", "traveler", "viajero", "flight", "vuelo", "invoice", "factura", "email")
        val columns = strings(
            "SELECT table_name || '.' || column_name FROM information_schema.columns " +
                "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
        )
        val offending = columns.filter { column -> forbidden.any { it in column } }
        assertTrue(offending.isEmpty(), "Columnas sospechosas: $offending")
    }
}
