package com.vueltax.domain.model

/** Solo los datos mínimos para evaluar la elegibilidad; nunca salen del dispositivo. */
data class Traveler(
    val id: Long = 0,
    val nationality: String,
    val residenceCountry: String,
)
