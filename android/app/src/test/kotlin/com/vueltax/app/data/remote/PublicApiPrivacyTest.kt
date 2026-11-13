package com.vueltax.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.GET
import retrofit2.http.Part

/** Regla de privacidad: la app solo descarga datos públicos, nunca envía datos del usuario. */
class PublicApiPrivacyTest {
    private val methods = PublicApi::class.java.declaredMethods.filterNot { it.isSynthetic }

    @Test
    fun `todos los metodos de la API son GET`() {
        assertTrue(methods.isNotEmpty())
        for (method in methods) {
            assertTrue(
                "${method.name} debe ser GET",
                method.isAnnotationPresent(GET::class.java),
            )
        }
    }

    @Test
    fun `ningun metodo envia cuerpo ni formularios`() {
        val bodyAnnotations = setOf(Body::class.java, Field::class.java, Part::class.java)
        for (method in methods) {
            val sendsBody = method.parameterAnnotations
                .flatMap { it.toList() }
                .any { it.annotationClass.java in bodyAnnotations }
            assertFalse("${method.name} no puede enviar datos", sendsBody)
        }
    }
}
