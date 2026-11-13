package app.vueltax.backend.db

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.json.jsonb

// Espejo del esquema creado por Flyway (db/migration). Las columnas con valor por
// defecto que el código no lee (created_at, fetched_at) no se declaran aquí.

object Countries : Table("country") {
    val code = varchar("code", 2)
    val name = text("name")
    val currency = varchar("currency", 3)
    val taxName = text("tax_name")
    override val primaryKey = PrimaryKey(code)
}

object RuleSets : LongIdTable("rule_set") {
    val countryCode = varchar("country_code", 2)
    val version = integer("version")
    val content = jsonb<String>("content", { it }, { it })
    val validFrom = date("valid_from")
    val needsVerification = bool("needs_verification")
}

object RefundPoints : LongIdTable("refund_point") {
    val countryCode = varchar("country_code", 2)
    val airportIata = varchar("airport_iata", 3)
    val terminal = text("terminal").nullable()
    val description = text("description")
    val lat = double("lat")
    val lon = double("lon")
    val openingHours = text("opening_hours")
    val updatedAt = timestampWithTimeZone("updated_at")
}

object ExchangeRates : LongIdTable("exchange_rate") {
    val rateDate = date("rate_date")
    val base = varchar("base", 3)
    val quote = varchar("quote", 3)
    val rate = decimal("rate", 20, 8)
    val sourceName = text("source")
    val fetchedAt = timestampWithTimeZone("fetched_at")
}
