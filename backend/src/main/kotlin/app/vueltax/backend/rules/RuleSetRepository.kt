package app.vueltax.backend.rules

import app.vueltax.backend.db.Countries
import app.vueltax.backend.db.RuleSets
import app.vueltax.backend.db.query
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll

@Serializable
data class CountryView(
    val code: String,
    val name: String,
    val currency: String,
    val taxName: String,
    /** Última versión publicada de las reglas; null si aún no hay ninguna. */
    val rulesVersion: Int?,
)

data class StoredRuleSet(val country: String, val version: Int, val content: String)

enum class SaveResult { CREATED, UNCHANGED, VERSION_CONFLICT, UNKNOWN_COUNTRY }

class RuleSetRepository(private val database: Database) {

    suspend fun countries(): List<CountryView> = database.query {
        val latest = RuleSets.version.max()
        val versions = RuleSets.select(RuleSets.countryCode, latest)
            .groupBy(RuleSets.countryCode)
            .associate { it[RuleSets.countryCode] to it[latest] }
        Countries.selectAll().orderBy(Countries.code).map {
            CountryView(
                code = it[Countries.code],
                name = it[Countries.name],
                currency = it[Countries.currency],
                taxName = it[Countries.taxName],
                rulesVersion = versions[it[Countries.code]],
            )
        }
    }

    suspend fun latest(country: String): StoredRuleSet? = database.query {
        RuleSets.selectAll()
            .where { RuleSets.countryCode eq country }
            .orderBy(RuleSets.version, SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?.let { StoredRuleSet(it[RuleSets.countryCode], it[RuleSets.version], it[RuleSets.content]) }
    }

    /** Guarda una versión nueva. Repetir una versión ya guardada solo se acepta si es idéntica. */
    suspend fun save(ruleSet: ParsedRuleSet): SaveResult = database.query {
        val knownCountry = Countries.selectAll().where { Countries.code eq ruleSet.country }.any()
        if (!knownCountry) return@query SaveResult.UNKNOWN_COUNTRY

        val existing = RuleSets.select(RuleSets.content)
            .where { (RuleSets.countryCode eq ruleSet.country) and (RuleSets.version eq ruleSet.version) }
            .singleOrNull()
        if (existing != null) {
            val same = Json.parseToJsonElement(existing[RuleSets.content]) == ruleSet.content
            return@query if (same) SaveResult.UNCHANGED else SaveResult.VERSION_CONFLICT
        }

        RuleSets.insert {
            it[countryCode] = ruleSet.country
            it[version] = ruleSet.version
            it[content] = ruleSet.content.toString()
            it[validFrom] = ruleSet.validFrom
            it[needsVerification] = ruleSet.needsVerification
        }
        SaveResult.CREATED
    }
}
