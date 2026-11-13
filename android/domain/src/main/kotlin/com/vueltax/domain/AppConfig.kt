package com.vueltax.domain

/**
 * Configuración global de la app. El nombre solo se define aquí y en el recurso `app_name`.
 */
object AppConfig {
    const val APP_NAME = "Vueltax"

    /** Países del MVP, en ISO 3166-1 alpha-2. */
    val SUPPORTED_COUNTRIES = listOf("CO", "PE", "MX")

    /** Moneda base con la que se piden las tasas de cambio a la API pública. */
    const val RATES_BASE_CURRENCY = "USD"
}
