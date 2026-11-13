package com.vueltax.domain.rules

import com.vueltax.domain.model.ExitMode
import com.vueltax.domain.model.Traveler
import com.vueltax.domain.model.Trip

sealed interface TravelerEligibility {
    data object Eligible : TravelerEligibility

    data class NotEligible(val reasons: List<TravelerIssue>) : TravelerEligibility
}

/** Motivo por el que el viajero no puede pedir la devolución; [message] es texto para mostrar. */
sealed interface TravelerIssue {
    val message: String

    data class ResidentInCountry(val country: String) : TravelerIssue {
        override val message = "La devolución es solo para no residentes en $country."
    }

    data class NationalOfCountry(val country: String) : TravelerIssue {
        override val message = "La devolución es solo para turistas extranjeros."
    }

    data class StayTooShort(val stayDays: Int, val minDays: Int) : TravelerIssue {
        override val message =
            "Tu estadía es de $stayDays días y el mínimo es de $minDays."
    }

    data class StayTooLong(val stayDays: Int, val maxDays: Int) : TravelerIssue {
        override val message =
            "Tu estadía es de $stayDays días y el máximo es de $maxDays."
    }

    data class ExitModeNotAllowed(val exitMode: ExitMode, val allowed: List<ExitMode>) :
        TravelerIssue {
        override val message =
            "La devolución no aplica si sales del país por vía ${exitMode.label()}; " +
                "vías permitidas: ${allowed.joinToString { it.label() }}."
    }
}

private fun ExitMode.label(): String = when (this) {
    ExitMode.AIR -> "aérea"
    ExitMode.SEA -> "marítima"
    ExitMode.LAND -> "terrestre"
}

/**
 * ¿Puede este viajero pedir la devolución en este viaje? Comprueba residencia, nacionalidad,
 * días de estadía y vía de salida, y devuelve todos los motivos que lo impiden.
 */
fun evaluateTraveler(traveler: Traveler, trip: Trip, ruleSet: RuleSet): TravelerEligibility {
    require(trip.countryCode == ruleSet.country) {
        "Las reglas son de ${ruleSet.country} y el viaje es a ${trip.countryCode}"
    }

    val issues = buildList {
        if (traveler.residenceCountry == ruleSet.country) {
            add(TravelerIssue.ResidentInCountry(ruleSet.country))
        }
        if (traveler.nationality == ruleSet.country) {
            add(TravelerIssue.NationalOfCountry(ruleSet.country))
        }
        val minDays = ruleSet.stayDays.min
        if (minDays != null && trip.stayDays < minDays) {
            add(TravelerIssue.StayTooShort(trip.stayDays, minDays))
        }
        val maxDays = ruleSet.stayDays.max
        if (maxDays != null && trip.stayDays > maxDays) {
            add(TravelerIssue.StayTooLong(trip.stayDays, maxDays))
        }
        if (trip.exitMode !in ruleSet.exitModes) {
            add(TravelerIssue.ExitModeNotAllowed(trip.exitMode, ruleSet.exitModes))
        }
    }
    return if (issues.isEmpty()) TravelerEligibility.Eligible else TravelerEligibility.NotEligible(issues)
}
