package com.tiendatech.mobile.feature.account.data

import com.tiendatech.mobile.core.network.ApiEnvelope
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface AccountApi {
    @GET("api/usuarios/me") suspend fun profile(): Response<ApiEnvelope<ProfileEnvelope>>
    @GET("api/usuarios/{userId}/direcciones") suspend fun addresses(@Path("userId") userId: Long, @Query("view") view: String = "full"): Response<ApiEnvelope<List<AddressDto>>>
    @POST("api/usuarios/{userId}/direcciones") suspend fun createAddress(@Path("userId") userId: Long, @Body request: AddressRequest): Response<ApiEnvelope<AddressDto>>
    @PUT("api/usuarios/{userId}/direcciones/{addressId}") suspend fun updateAddress(@Path("userId") userId: Long, @Path("addressId") addressId: Long, @Body request: AddressRequest): Response<ApiEnvelope<AddressDto>>
    @DELETE("api/usuarios/{userId}/direcciones/{addressId}") suspend fun deleteAddress(@Path("userId") userId: Long, @Path("addressId") addressId: Long): Response<Unit>
    @GET("api/provincias") suspend fun provinces(): Response<ApiEnvelope<List<ProvinceDto>>>
    @GET("api/ciudades") suspend fun cities(): Response<ApiEnvelope<List<CityDto>>>
    @GET("api/metodopago/usuario/{userId}") suspend fun paymentMethods(@Path("userId") userId: Long, @Query("size") size: Int = 100): Response<ApiEnvelope<PaymentPageDto>>
    @GET("api/metodopago/tipos") suspend fun paymentTypes(): Response<ApiEnvelope<List<PaymentTypeDto>>>
    @POST("api/metodopago") suspend fun createPayment(@Body request: PaymentRequest): Response<Unit>
    @PUT("api/metodopago/{id}") suspend fun updatePayment(@Path("id") id: Long, @Body request: PaymentUpdateRequest): Response<Unit>
    @DELETE("api/metodopago/{id}") suspend fun disablePayment(@Path("id") id: Long): Response<Unit>
    @POST("api/metodopago/{id}/reactivar") suspend fun enablePayment(@Path("id") id: Long): Response<Unit>
    @POST("api/seguridad/cambiar-password") suspend fun changePassword(@Body request: PasswordRequest): Response<Unit>
    @GET("api/ordenes/usuario/{userId}") suspend fun orders(@Path("userId") userId: Long, @Query("page") page: Int = 0, @Query("size") size: Int = 100): Response<ApiEnvelope<OrderPageDto>>
    @POST("api/ordenes/checkout") suspend fun checkout(@Header("Idempotency-Key") key: String, @Body request: CheckoutRequest): Response<ApiEnvelope<OrderDto>>
}

@Serializable data class ProfileEnvelope(val data: ProfileDto)
@Serializable data class ProfileDto(val usuarioId: Long, val usuario: String = "", val nombre: String = "", val cedula: String = "", val correo: String = "", val telefono: String = "", val id_rol: Int? = null)
@Serializable data class AddressDto(val direccionId: Long, val usuarioId: Long? = null, val calle: String = "", val referencia: String? = null, val ciudadId: Long, val ciudadNombre: String? = null, val provinciaNombre: String? = null, val habilitado: Boolean? = true)
@Serializable data class AddressRequest(val calle: String, val referencia: String?, val ciudadId: Long)
@Serializable data class ProvinceDto(val provinciaId: Long, val nombre: String)
@Serializable data class CityDto(val ciudadId: Long, val nombre: String, val provinciaId: Long)
@Serializable data class PaymentMethodDto(val metodopagoId: Long, val numeroMascara: String, val fechaExpiracion: String, val habilitado: Boolean, val tipoId: Long, val tipoNombre: String)
@Serializable data class PaymentPageDto(val content: List<PaymentMethodDto> = emptyList(), val page: Int = 0, val size: Int = 0, val totalElements: Long = 0, val totalPages: Int = 0)
@Serializable data class PaymentTypeDto(val tipoId: Long, val nombre: String)
@Serializable data class PaymentRequest(val numeroTarjeta: String, val fechaExpiracion: String, val tipoId: Long)
@Serializable data class PaymentUpdateRequest(val numeroTarjeta: String, val fechaExpiracion: String, val tipoId: Long, val habilitado: Boolean)
@Serializable data class PasswordRequest(val actual: String, val nueva: String)
@Serializable data class CheckoutRequest(val direccionId: Long, val metodopagoId: Long)
@Serializable data class OrderDto(val ordenId: Long, val usuarioId: Long, val direccionId: Long, val metodopagoId: Long, val subtotal: Double, val total: Double, val fecha: String)
@Serializable data class OrderPageDto(val content: List<OrderDto> = emptyList(), val page: Int = 0, val size: Int = 0, val totalElements: Long = 0, val totalPages: Int = 0)
