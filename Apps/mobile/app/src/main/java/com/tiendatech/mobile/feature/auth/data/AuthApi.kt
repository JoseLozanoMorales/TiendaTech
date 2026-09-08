package com.tiendatech.mobile.feature.auth.data

import com.tiendatech.mobile.core.network.ApiEnvelope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface AuthApi {
    @POST("api/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiEnvelope<LoginResponse>>

    @POST("api/otp")
    suspend fun sendOtp(@Body request: OtpRequest): Response<ApiEnvelope<OtpResponse>>

    @POST("api/otp")
    suspend fun verifyOtp(@Body request: OtpRequest): Response<Unit>

    @POST("api/usuarios/crear")
    suspend fun register(@Body request: RegisterRequest): Response<Unit>

    @POST("api/usuarios/recuperar-password")
    suspend fun recoverPassword(@Body request: RecoveryRequest): Response<Unit>

    @GET("api/usuarios/me")
    suspend fun profile(): Response<ApiEnvelope<ProfileResponse>>
}

@Serializable
data class LoginRequest(
    val usuario: String,
    val contrasena: String
)

@Serializable
data class LoginResponse(
    val success: Boolean = false,
    val user: AuthUserDto? = null,
    val usuario: AuthUserDto? = null,
    val token: String? = null,
    val access: String? = null,
    val accessToken: String? = null,
    val message: String? = null
)

@Serializable
data class AuthUserDto(
    val usuarioId: Int? = null,
    @SerialName("id_usuario") val legacyUserId: Int? = null,
    val id: Int? = null,
    val usuario: String = "",
    val nombre: String = "",
    val cedula: String = "",
    val correo: String = "",
    val telefono: String = "",
    @SerialName("id_rol") val roleId: Int? = null,
    val idRol: Int? = null,
    val role: String? = null,
    val rol: String? = null
)

@Serializable
data class OtpRequest(
    val accion: String,
    val correo: String,
    val codigo: String? = null,
    val txId: String? = null
)

@Serializable
data class OtpResponse(
    val txId: String,
    val devCode: String? = null
)

@Serializable
data class RegisterRequest(
    val nombre: String,
    val usuario: String,
    val correo: String,
    val contrasena: String,
    val cedula: String,
    val telefono: String
)

@Serializable
data class RecoveryRequest(val correo: String)

@Serializable
data class ProfileResponse(val data: AuthUserDto)
