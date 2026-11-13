package app.vueltax.backend.http

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.security.MessageDigest

val apiJson = Json { encodeDefaults = true }

/**
 * Responde datos públicos con `Cache-Control` y un `ETag` derivado del contenido; si el
 * cliente ya tiene esa versión (`If-None-Match`), responde 304 sin cuerpo.
 */
suspend fun ApplicationCall.respondCacheable(json: String, maxAgeSeconds: Int) {
    val etag = "\"${sha256Hex(json).take(32)}\""
    response.header(HttpHeaders.ETag, etag)
    response.header(HttpHeaders.CacheControl, "public, max-age=$maxAgeSeconds")

    val known = request.headers[HttpHeaders.IfNoneMatch]
        ?.split(',')
        ?.map { it.trim().removePrefix("W/") }
        .orEmpty()
    if (etag in known || "*" in known) {
        respond(HttpStatusCode.NotModified)
    } else {
        respondText(json, ContentType.Application.Json)
    }
}

suspend fun ApplicationCall.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK) {
    response.header(HttpHeaders.CacheControl, "no-store")
    respondText(json, ContentType.Application.Json, status)
}

suspend fun ApplicationCall.respondError(status: HttpStatusCode, message: String, details: List<String> = emptyList()) {
    val body = buildJsonObject {
        put("error", JsonPrimitive(message))
        if (details.isNotEmpty()) put("details", JsonArray(details.map(::JsonPrimitive)))
    }
    respondJson(body.toString(), status)
}

private fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
