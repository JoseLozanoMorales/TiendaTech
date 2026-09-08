package com.tiendatech.mobile.feature.orders.data

import com.tiendatech.mobile.core.network.ApiEnvelope
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OrdersApi {
    @GET("api/ordenes/usuario/{userId}") suspend fun orders(@Path("userId") userId: Long, @Query("page") page: Int, @Query("size") size: Int = 20): Response<ApiEnvelope<OrderPageDto>>
    @GET("api/ordenes/{orderId}") suspend fun order(@Path("orderId") orderId: Long): Response<ApiEnvelope<OrderDto>>
    @GET("api/ordenes/{orderId}/detalle") suspend fun orderLines(@Path("orderId") orderId: Long, @Query("page") page: Int = 0, @Query("size") size: Int = 100): Response<ApiEnvelope<OrderLinePageDto>>
    @GET("api/facturas") suspend fun invoices(@Query("usuarioId") userId: Long): Response<ApiEnvelope<List<InvoiceDto>>>
    @GET("api/facturas/{invoiceId}/detalle") suspend fun invoiceLines(@Path("invoiceId") invoiceId: Long): Response<ApiEnvelope<List<InvoiceLineDto>>>
}

@Serializable data class OrderDto(val ordenId: Long, val usuarioId: Long, val direccionId: Long, val metodopagoId: Long, val subtotal: Double, val total: Double, val fecha: String)
@Serializable data class OrderPageDto(val content: List<OrderDto> = emptyList(), val page: Int = 0, val size: Int = 20, val totalElements: Long = 0, val totalPages: Int = 0)
@Serializable data class OrderLineDto(val ordenId: Long, val productoId: Long, val cantidad: Int, val precioUnitario: Double, val subtotal: Double, val iva: Double, val total: Double)
@Serializable data class OrderLinePageDto(val content: List<OrderLineDto> = emptyList(), val page: Int = 0, val size: Int = 0, val totalElements: Long = 0, val totalPages: Int = 0)
@Serializable data class InvoiceDto(
    val facturaId: Long,
    val ordenId: Long,
    val usuarioId: Long,
    val fechaEmision: String,
    val fechaOrden: String,
    val cedula: String? = null,
    val nombre: String? = null,
    val correo: String? = null,
    val telefono: String? = null,
    val direccionEntrega: String? = null,
    val subtotal: Double,
    val total: Double,
    val numero: String
)
@Serializable data class InvoiceLineDto(val productoId: Long, val nombreProducto: String, val cantidad: Int, val precio: Double, val subtotal: Double, val iva: Double, val total: Double)
