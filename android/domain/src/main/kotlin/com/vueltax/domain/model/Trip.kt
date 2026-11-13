package com.vueltax.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class ExitMode { AIR, SEA, LAND }

data class Trip(
    val id: Long = 0,
    val countryCode: String,
    val arrivalDate: LocalDate,
    val departureDate: LocalDate,
    val exitMode: ExitMode,
) {
    init {
        require(!departureDate.isBefore(arrivalDate)) {
            "La fecha de salida no puede ser anterior a la de llegada"
        }
    }

    /** Días de estadía contando el de llegada y el de salida. */
    val stayDays: Int
        get() = ChronoUnit.DAYS.between(arrivalDate, departureDate).toInt() + 1
}
