package com.tiendatech.mobile.core.network

import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface DiagnosticService {
    @GET("api/productos")
    suspend fun testConnection(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 1
    ): Response<ApiEnvelope<List<JsonObject>>>
}
