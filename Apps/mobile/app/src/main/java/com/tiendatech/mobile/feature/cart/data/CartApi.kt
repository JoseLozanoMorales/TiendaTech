package com.tiendatech.mobile.feature.cart.data

import com.tiendatech.mobile.core.network.ApiEnvelope
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface CartApi {
    @GET("api/carrito/{userId}")
    suspend fun current(@Path("userId") userId: Long): Response<ApiEnvelope<CartDto>>

    @GET("api/carrito/{cartId}/detalle")
    suspend fun lines(
        @Path("cartId") cartId: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 200
    ): Response<ApiEnvelope<CartPageDto>>

    @POST("api/carrito/{cartId}/agregar")
    suspend fun add(@Path("cartId") cartId: Long, @Body request: CartQuantityRequest): Response<Unit>

    @PUT("api/carrito/{cartId}/actualizar/{productId}")
    suspend fun update(
        @Path("cartId") cartId: Long,
        @Path("productId") productId: Long,
        @Body request: QuantityRequest
    ): Response<Unit>

    @DELETE("api/carrito/{cartId}/quitar/{productId}")
    suspend fun remove(@Path("cartId") cartId: Long, @Path("productId") productId: Long): Response<Unit>
}

@Serializable data class CartDto(val carritoId: Long, val usuarioId: Long? = null, val total: Double? = null)

@Serializable
data class CartPageDto(
    val content: List<CartLineDto> = emptyList(),
    val page: Int = 0,
    val size: Int = 0,
    val totalElements: Long = 0,
    val totalPages: Int = 0
)

@Serializable data class CartLineDto(val carritoId: Long, val productoId: Long, val cantidad: Int, val precioUnitario: Double)
@Serializable data class CartQuantityRequest(val productoId: Long, val cantidad: Int)
@Serializable data class QuantityRequest(val cantidad: Int)
