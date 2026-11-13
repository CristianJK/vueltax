package app.vueltax.backend.rules

import com.networknt.schema.ExecutionContext
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.util.function.Consumer

/** Campos de un rule set ya validado que el backend necesita para guardarlo. */
data class ParsedRuleSet(
    val country: String,
    val version: Int,
    val validFrom: LocalDate,
    val needsVerification: Boolean,
    val content: JsonObject,
)

sealed interface RuleSetValidation {
    data class Valid(val ruleSet: ParsedRuleSet) : RuleSetValidation
    data class Invalid(val errors: List<String>) : RuleSetValidation
    data object Malformed : RuleSetValidation
}

/**
 * Valida rule sets contra rules/schema.json, que el build empaqueta como recurso
 * (ver processResources en build.gradle.kts).
 */
class RuleSetValidator {
    private val schema: Schema = checkNotNull(javaClass.getResourceAsStream(SCHEMA_RESOURCE)) {
        "No se encontró $SCHEMA_RESOURCE en el classpath"
    }.use { SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12).getSchema(it) }

    private val assertFormats = Consumer<ExecutionContext> { context ->
        context.executionConfig { it.formatAssertionsEnabled(true) }
    }

    fun validate(json: String): RuleSetValidation {
        val content = try {
            Json.parseToJsonElement(json) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return RuleSetValidation.Malformed

        val errors = schema.validate(json, InputFormat.JSON, assertFormats)
            .map { error -> "${error.instanceLocation}: ${error.message}" }
        if (errors.isNotEmpty()) return RuleSetValidation.Invalid(errors)

        // Coherencia que el schema no puede expresar (misma comprobación que rules/test).
        val stayDays = content.getValue("stayDays").jsonObject
        val min = stayDays.getValue("min").takeUnless { it is JsonNull }?.jsonPrimitive?.int
        val max = stayDays.getValue("max").takeUnless { it is JsonNull }?.jsonPrimitive?.int
        if (min != null && max != null && min > max) {
            return RuleSetValidation.Invalid(listOf("/stayDays: min ($min) es mayor que max ($max)"))
        }

        return RuleSetValidation.Valid(
            ParsedRuleSet(
                country = content.getValue("country").jsonPrimitive.content,
                version = content.getValue("version").jsonPrimitive.int,
                validFrom = LocalDate.parse(content.getValue("validFrom").jsonPrimitive.content),
                needsVerification = content.getValue("needsVerification").jsonPrimitive.boolean,
                content = content,
            ),
        )
    }

    private companion object {
        const val SCHEMA_RESOURCE = "/rules/schema.json"
    }
}
