package com.tiendatech.mobile.feature.auth.data

import com.tiendatech.mobile.core.security.SessionStore
import com.tiendatech.mobile.feature.auth.domain.AuthResult
import com.tiendatech.mobile.feature.auth.domain.AuthUser
import com.tiendatech.mobile.feature.auth.domain.RegistrationData
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore
) {
    suspend fun login(username: String, password: String): AuthResult<AuthUser> = request {
        val response = api.login(LoginRequest(username.trim(), password))
        if (!response.isSuccessful) return@request failure(response.code())
        val envelope = response.body() ?: return@request AuthResult.Failure("La respuesta del servidor está vacía")
        val body = envelope.data
        val userDto = body.user ?: body.usuario
            ?: return@request AuthResult.Failure(body.message ?: "La respuesta no contiene el usuario")
        val user = userDto.toDomain()
        if (!user.isCustomer()) return@request AuthResult.Failure("Esta aplicación es exclusiva para clientes")
        val token = body.token ?: body.access ?: body.accessToken
        if (token.isNullOrBlank()) return@request AuthResult.Failure("La respuesta no contiene una sesión válida")
        sessionStore.saveToken(token)
        AuthResult.Success(user)
    }

    suspend fun restoreSession(): AuthResult<AuthUser?> {
        if (sessionStore.getToken().isNullOrBlank()) return AuthResult.Success(null)
        return request {
            val response = api.profile()
            if (response.code() == 401 || response.code() == 403) {
                sessionStore.clear()
                return@request AuthResult.Success(null)
            }
            if (!response.isSuccessful) return@request failure(response.code())
            val user = response.body()?.data?.data?.toDomain()
                ?: return@request AuthResult.Failure("No se pudo recuperar el perfil")
            if (!user.isCustomer()) {
                sessionStore.clear()
                return@request AuthResult.Success(null)
            }
            AuthResult.Success(user)
        }
    }

    suspend fun sendOtp(email: String, transactionId: String?): AuthResult<String> = request {
        val response = api.sendOtp(OtpRequest("enviar", email.trim(), txId = transactionId))
        if (!response.isSuccessful) return@request failure(response.code())
        val txId = response.body()?.data?.txId
            ?: return@request AuthResult.Failure("El servidor no devolvió el identificador de verificación")
        AuthResult.Success(txId)
    }

    suspend fun verifyAndRegister(
        data: RegistrationData,
        code: String,
        transactionId: String
    ): AuthResult<Unit> = request {
        val verify = api.verifyOtp(OtpRequest("validar", data.email, code, transactionId))
        if (!verify.isSuccessful) return@request failure(verify.code())
        val register = api.register(
            RegisterRequest(data.name, data.username, data.email, data.password, data.document, data.phone)
        )
        if (!register.isSuccessful) return@request failure(register.code())
        AuthResult.Success(Unit)
    }

    suspend fun recoverPassword(email: String): AuthResult<Unit> = request {
        val response = api.recoverPassword(RecoveryRequest(email.trim()))
        if (!response.isSuccessful) return@request failure(response.code())
        AuthResult.Success(Unit)
    }

    fun logout() = sessionStore.clear()

    private suspend fun <T> request(block: suspend () -> AuthResult<T>): AuthResult<T> = try {
        block()
    } catch (_: SocketTimeoutException) {
        AuthResult.Failure("El servidor tardó demasiado en responder")
    } catch (_: IOException) {
        AuthResult.Failure("No se pudo conectar con el servidor")
    } catch (_: Exception) {
        AuthResult.Failure("Ocurrió un error inesperado")
    }

    private fun failure(status: Int): AuthResult.Failure = AuthResult.Failure(
        when (status) {
            400 -> "Revisa los datos ingresados"
            401 -> "Usuario o contraseña incorrectos"
            403 -> "No tienes permiso para acceder"
            409 -> "El usuario, correo o documento ya está registrado"
            429 -> "Demasiados intentos. Espera antes de volver a intentar"
            in 500..599 -> "El servidor no está disponible temporalmente"
            else -> "No fue posible completar la solicitud"
        }
    )

    private fun AuthUserDto.toDomain() = AuthUser(
        id = usuarioId ?: legacyUserId ?: id ?: 0,
        username = usuario,
        name = nombre,
        email = correo,
        roleId = roleId ?: idRol ?: when ((role ?: rol).orEmpty().uppercase()) {
            "CLIENTE" -> 2
            "ADMIN" -> 1
            "TRABAJADOR" -> 3
            else -> 0
        }
    )

    private fun AuthUser.isCustomer() = roleId == 2
}
