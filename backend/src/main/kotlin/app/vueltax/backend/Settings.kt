package app.vueltax.backend

import java.time.LocalTime
import java.time.format.DateTimeParseException

/** Configuración del backend; todo llega por variables de entorno. */
data class Settings(
    val port: Int,
    val database: DatabaseSettings,
    val adminToken: String,
    val trustForwardedHeaders: Boolean,
    val corsAllowedOrigins: List<String>,
    val publicRateLimitPerMinute: Int,
    val adminRateLimitPerMinute: Int,
    val rates: RatesSettings,
) {
    companion object {
        const val MIN_ADMIN_TOKEN_LENGTH = 32

        /** Monedas locales del MVP más las de origen más habituales de los turistas. */
        val DEFAULT_QUOTES = listOf(
            "COP", "PEN", "MXN", "USD", "EUR", "GBP", "CAD", "BRL", "CLP", "ARS", "CHF", "JPY", "CNY", "AUD",
        )

        fun fromEnv(env: Map<String, String> = System.getenv()): Settings {
            fun value(name: String): String? = env[name]?.trim()?.takeIf { it.isNotEmpty() }
            fun required(name: String): String =
                value(name) ?: throw IllegalStateException("Falta la variable de entorno $name")
            fun int(name: String, default: Int): Int {
                val raw = value(name) ?: return default
                return raw.toIntOrNull()?.takeIf { it > 0 }
                    ?: throw IllegalStateException("$name debe ser un entero positivo")
            }
            fun bool(name: String, default: Boolean): Boolean = value(name)?.toBooleanStrictOrNull()
                ?: if (value(name) == null) default else throw IllegalStateException("$name debe ser true o false")
            fun list(name: String, default: List<String> = emptyList()): List<String> =
                value(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: default
            fun currencies(name: String, default: List<String> = emptyList()): List<String> =
                list(name, default).map { it.uppercase() }.onEach {
                    check(CURRENCY_CODE.matches(it)) { "$name contiene una moneda inválida" }
                }

            val adminToken = required("ADMIN_TOKEN")
            check(adminToken.length >= MIN_ADMIN_TOKEN_LENGTH) {
                "ADMIN_TOKEN debe tener al menos $MIN_ADMIN_TOKEN_LENGTH caracteres"
            }

            val jobTime = value("RATES_JOB_TIME_UTC") ?: "23:30"
            return Settings(
                port = int("PORT", 8080),
                database = DatabaseSettings(
                    url = required("DATABASE_URL"),
                    user = required("DATABASE_USER"),
                    password = required("DATABASE_PASSWORD"),
                ),
                adminToken = adminToken,
                trustForwardedHeaders = bool("TRUST_FORWARDED_HEADERS", false),
                corsAllowedOrigins = list("CORS_ALLOWED_ORIGINS"),
                publicRateLimitPerMinute = int("RATE_LIMIT_PER_MINUTE", 120),
                adminRateLimitPerMinute = int("ADMIN_RATE_LIMIT_PER_MINUTE", 30),
                rates = RatesSettings(
                    jobEnabled = bool("RATES_JOB_ENABLED", true),
                    jobTimeUtc = try {
                        LocalTime.parse(jobTime)
                    } catch (_: DateTimeParseException) {
                        throw IllegalStateException("RATES_JOB_TIME_UTC debe tener formato HH:mm")
                    },
                    bases = currencies("RATES_BASES", listOf("USD", "EUR")),
                    quotes = currencies("RATES_QUOTES", DEFAULT_QUOTES),
                    frankfurterUrl = value("FRANKFURTER_URL") ?: "https://api.frankfurter.dev",
                    trmUrl = value("TRM_URL") ?: "https://www.datos.gov.co/resource/32sa-8pi3.json",
                ),
            )
        }
    }
}

data class DatabaseSettings(val url: String, val user: String, val password: String)

data class RatesSettings(
    val jobEnabled: Boolean,
    val jobTimeUtc: LocalTime,
    /** Monedas base que se piden a Frankfurter. */
    val bases: List<String>,
    /** Monedas cotizadas que se guardan (Frankfurter publica cientos, incluidas criptomonedas). */
    val quotes: List<String>,
    val frankfurterUrl: String,
    val trmUrl: String,
)

val COUNTRY_CODE = Regex("^[A-Z]{2}$")
val CURRENCY_CODE = Regex("^[A-Z]{3}$")
val IATA_CODE = Regex("^[A-Z]{3}$")
