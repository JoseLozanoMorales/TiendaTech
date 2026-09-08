package com.tiendatech.mobile.core.network

import kotlinx.serialization.Serializable

/** Respuesta JSON transversal expuesta por los microservicios. */
@Serializable
data class ApiEnvelope<T>(
    val status: Int,
    val data: T,
    val message: String? = null,
    val timestamp: String? = null
)
