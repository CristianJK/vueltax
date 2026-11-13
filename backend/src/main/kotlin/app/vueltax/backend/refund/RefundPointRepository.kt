package app.vueltax.backend.refund

import app.vueltax.backend.COUNTRY_CODE
import app.vueltax.backend.IATA_CODE
import app.vueltax.backend.db.Countries
import app.vueltax.backend.db.RefundPoints
import app.vueltax.backend.db.query
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime

@Serializable
data class RefundPointView(
    val id: Long,
    val country: String,
    val airportIata: String,
    val terminal: String?,
    val description: String,
    val lat: Double,
    val lon: Double,
    val openingHours: String,
)

@Serializable
data class RefundPointInput(
    val country: String,
    val airportIata: String,
    val terminal: String? = null,
    val description: String,
    val lat: Double,
    val lon: Double,
    val openingHours: String,
) {
    fun errors(): List<String> = buildList {
        if (!COUNTRY_CODE.matches(country)) add("country debe ser un código ISO 3166-1 alpha-2")
        if (!IATA_CODE.matches(airportIata)) add("airportIata debe ser un código IATA de 3 letras")
        if (terminal != null && terminal.isBlank()) add("terminal no puede estar vacío; omítelo si no aplica")
        if (description.isBlank()) add("description es obligatorio")
        if (openingHours.isBlank()) add("openingHours es obligatorio")
        if (lat.isNaN() || lat !in -90.0..90.0) add("lat debe estar entre -90 y 90")
        if (lon.isNaN() || lon !in -180.0..180.0) add("lon debe estar entre -180 y 180")
    }
}

sealed interface RefundPointResult {
    data class Saved(val point: RefundPointView) : RefundPointResult
    data object NotFound : RefundPointResult
    data object UnknownCountry : RefundPointResult
    /** Ya existe otro punto con el mismo país, aeropuerto y terminal. */
    data object Duplicate : RefundPointResult
}

class RefundPointRepository(private val database: Database) {

    suspend fun list(country: String?): List<RefundPointView> = database.query {
        val query = RefundPoints.selectAll()
        if (country != null) query.where { RefundPoints.countryCode eq country }
        query.orderBy(RefundPoints.countryCode)
            .orderBy(RefundPoints.airportIata)
            .orderBy(RefundPoints.terminal)
            .map { it.toView() }
    }

    suspend fun create(input: RefundPointInput): RefundPointResult = database.query {
        conflict(input, exceptId = null)?.let { return@query it }
        val id = RefundPoints.insertAndGetId {
            it[countryCode] = input.country
            it[airportIata] = input.airportIata
            it[terminal] = input.terminal
            it[description] = input.description
            it[lat] = input.lat
            it[lon] = input.lon
            it[openingHours] = input.openingHours
            it[updatedAt] = OffsetDateTime.now()
        }
        RefundPointResult.Saved(input.toView(id.value))
    }

    suspend fun update(id: Long, input: RefundPointInput): RefundPointResult = database.query {
        if (RefundPoints.selectAll().where { RefundPoints.id eq id }.empty()) {
            return@query RefundPointResult.NotFound
        }
        conflict(input, exceptId = id)?.let { return@query it }
        RefundPoints.update({ RefundPoints.id eq id }) {
            it[countryCode] = input.country
            it[airportIata] = input.airportIata
            it[terminal] = input.terminal
            it[description] = input.description
            it[lat] = input.lat
            it[lon] = input.lon
            it[openingHours] = input.openingHours
            it[updatedAt] = OffsetDateTime.now()
        }
        RefundPointResult.Saved(input.toView(id))
    }

    suspend fun delete(id: Long): Boolean = database.query {
        RefundPoints.deleteWhere { RefundPoints.id eq id } > 0
    }

    private fun JdbcTransaction.conflict(input: RefundPointInput, exceptId: Long?): RefundPointResult? {
        if (Countries.selectAll().where { Countries.code eq input.country }.empty()) {
            return RefundPointResult.UnknownCountry
        }
        val duplicate = RefundPoints.selectAll()
            .where { (RefundPoints.countryCode eq input.country) and (RefundPoints.airportIata eq input.airportIata) }
            .any { it[RefundPoints.terminal] == input.terminal && it[RefundPoints.id].value != exceptId }
        return if (duplicate) RefundPointResult.Duplicate else null
    }

    private fun RefundPointInput.toView(id: Long) =
        RefundPointView(id, country, airportIata, terminal, description, lat, lon, openingHours)

    private fun ResultRow.toView() = RefundPointView(
        id = this[RefundPoints.id].value,
        country = this[RefundPoints.countryCode],
        airportIata = this[RefundPoints.airportIata],
        terminal = this[RefundPoints.terminal],
        description = this[RefundPoints.description],
        lat = this[RefundPoints.lat],
        lon = this[RefundPoints.lon],
        openingHours = this[RefundPoints.openingHours],
    )
}
