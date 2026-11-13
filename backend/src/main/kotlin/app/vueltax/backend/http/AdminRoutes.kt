package app.vueltax.backend.http

import app.vueltax.backend.Services
import app.vueltax.backend.refund.RefundPointInput
import app.vueltax.backend.refund.RefundPointResult
import app.vueltax.backend.rules.RuleSetValidation
import app.vueltax.backend.rules.SaveResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString

/**
 * Administración de datos públicos, protegida por token Bearer. Aquí solo se cargan
 * reglas, puntos de devolución y tasas: nunca datos de usuarios.
 */
fun Route.adminRoutes(services: Services) {
    route("/v1/admin") {
        // Publica una versión de reglas; el cuerpo es un archivo rules/<PAÍS>.json.
        post("/rules") {
            when (val validation = services.ruleValidator.validate(call.receiveText())) {
                RuleSetValidation.Malformed ->
                    call.respondError(HttpStatusCode.BadRequest, "El cuerpo debe ser un objeto JSON")

                is RuleSetValidation.Invalid ->
                    call.respondError(
                        HttpStatusCode.UnprocessableEntity,
                        "El rule set no cumple el schema",
                        validation.errors,
                    )

                is RuleSetValidation.Valid -> {
                    val ruleSet = validation.ruleSet
                    val body = apiJson.encodeToString(RulesVersionView(ruleSet.country, ruleSet.version))
                    when (services.rules.save(ruleSet)) {
                        SaveResult.CREATED -> call.respondJson(body, HttpStatusCode.Created)
                        SaveResult.UNCHANGED -> call.respondJson(body, HttpStatusCode.OK)
                        SaveResult.VERSION_CONFLICT -> call.respondError(
                            HttpStatusCode.Conflict,
                            "La versión ${ruleSet.version} de ${ruleSet.country} ya existe con otro contenido; " +
                                "incrementa version",
                        )

                        SaveResult.UNKNOWN_COUNTRY -> call.respondError(
                            HttpStatusCode.UnprocessableEntity,
                            "País no soportado: ${ruleSet.country}",
                        )
                    }
                }
            }
        }

        post("/refund-points") {
            val input = call.receiveRefundPoint() ?: return@post
            call.respondRefundPoint(services.refundPoints.create(input), HttpStatusCode.Created)
        }

        put("/refund-points/{id}") {
            val id = call.refundPointId() ?: return@put
            val input = call.receiveRefundPoint() ?: return@put
            call.respondRefundPoint(services.refundPoints.update(id, input), HttpStatusCode.OK)
        }

        delete("/refund-points/{id}") {
            val id = call.refundPointId() ?: return@delete
            if (services.refundPoints.delete(id)) {
                call.respondJson("{}", HttpStatusCode.OK)
            } else {
                call.respondError(HttpStatusCode.NotFound, "No existe el punto de devolución $id")
            }
        }

        // Ejecuta ahora la descarga de tasas, sin esperar al job diario.
        post("/rates/refresh") {
            call.respondJson(apiJson.encodeToString(services.ratesUpdater.runOnce()))
        }
    }
}

private suspend fun ApplicationCall.refundPointId(): Long? {
    val id = parameters["id"]?.toLongOrNull()
    if (id == null) respondError(HttpStatusCode.BadRequest, "id debe ser un número")
    return id
}

private suspend fun ApplicationCall.receiveRefundPoint(): RefundPointInput? {
    val input = try {
        apiJson.decodeFromString<RefundPointInput>(receiveText())
    } catch (_: SerializationException) {
        respondError(HttpStatusCode.BadRequest, "Cuerpo JSON inválido para un punto de devolución")
        return null
    } catch (_: IllegalArgumentException) {
        respondError(HttpStatusCode.BadRequest, "Cuerpo JSON inválido para un punto de devolución")
        return null
    }
    val errors = input.errors()
    if (errors.isNotEmpty()) {
        respondError(HttpStatusCode.UnprocessableEntity, "Punto de devolución inválido", errors)
        return null
    }
    return input
}

private suspend fun ApplicationCall.respondRefundPoint(result: RefundPointResult, success: HttpStatusCode) {
    when (result) {
        is RefundPointResult.Saved -> respondJson(apiJson.encodeToString(result.point), success)
        RefundPointResult.NotFound -> respondError(HttpStatusCode.NotFound, "No existe ese punto de devolución")
        RefundPointResult.UnknownCountry -> respondError(HttpStatusCode.UnprocessableEntity, "País no soportado")
        RefundPointResult.Duplicate -> respondError(
            HttpStatusCode.Conflict,
            "Ya existe un punto de devolución con ese país, aeropuerto y terminal",
        )
    }
}
