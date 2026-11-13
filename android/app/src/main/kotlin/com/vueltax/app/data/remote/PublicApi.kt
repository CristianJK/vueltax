package com.vueltax.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API pública del backend. Solo GET y solo datos públicos: aquí no se añaden métodos que
 * envíen datos del usuario (lo comprueba PublicApiPrivacyTest).
 */
interface PublicApi {
    @GET("v1/countries")
    suspend fun countries(): List<CountryDto>

    /** Reglas del país como JSON sin interpretar (formato de `rules/schema.json`). */
    @GET("v1/rules/{country}")
    suspend fun rules(@Path("country") country: String): ResponseBody

    @GET("v1/rules/{country}/version")
    suspend fun rulesVersion(@Path("country") country: String): RulesVersionDto

    @GET("v1/refund-points")
    suspend fun refundPoints(@Query("country") country: String? = null): List<RefundPointDto>

    @GET("v1/rates")
    suspend fun rates(@Query("base") base: String, @Query("date") date: String? = null): RatesDto
}
