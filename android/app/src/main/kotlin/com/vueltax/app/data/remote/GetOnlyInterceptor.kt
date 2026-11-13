package com.vueltax.app.data.remote

import java.io.IOException
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Regla de privacidad en tiempo de ejecución: la app solo descarga datos públicos, así que
 * cualquier petición que no sea GET se bloquea antes de salir del dispositivo.
 */
class GetOnlyInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") {
            throw IOException("Petición ${request.method} bloqueada: la app solo hace GET")
        }
        return chain.proceed(request)
    }
}
