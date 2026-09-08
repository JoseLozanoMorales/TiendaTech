package com.tiendatech.mobile.feature.account.data

import com.tiendatech.mobile.feature.account.domain.AccountData
import com.tiendatech.mobile.feature.account.domain.AccountResult
import com.tiendatech.mobile.feature.account.domain.Address
import com.tiendatech.mobile.feature.account.domain.City
import com.tiendatech.mobile.feature.account.domain.OrderConfirmation
import com.tiendatech.mobile.feature.account.domain.PaymentMethod
import com.tiendatech.mobile.feature.account.domain.PaymentType
import com.tiendatech.mobile.feature.account.domain.Profile
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(private val api: AccountApi) {
    suspend fun load(userId: Long): AccountResult<AccountData> = request {
        coroutineScope {
            val profile = async { api.profile() }; val addresses = async { api.addresses(userId) }
            val provinces = async { api.provinces() }; val cities = async { api.cities() }
            val methods = async { api.paymentMethods(userId) }; val types = async { api.paymentTypes() }
            val profileResponse = profile.await(); val addressResponse = addresses.await()
            val provinceResponse = provinces.await(); val cityResponse = cities.await()
            val methodResponse = methods.await(); val typeResponse = types.await()
            val failedCode = listOf(profileResponse, addressResponse, provinceResponse, cityResponse, methodResponse, typeResponse)
                .firstOrNull { !it.isSuccessful }?.code()
            if (failedCode != null) return@coroutineScope failure(failedCode)
            val provinceNames = provinceResponse.body()?.data.orEmpty().associate { it.provinciaId to it.nombre }
            val cityList = cityResponse.body()?.data.orEmpty()
            AccountResult.Success(
                AccountData(
                    profile = profileResponse.body()!!.data.data.toDomain(),
                    addresses = addressResponse.body()?.data.orEmpty().map { it.toDomain() },
                    cities = cityList.map { City(it.ciudadId, it.nombre, provinceNames[it.provinciaId].orEmpty()) },
                    paymentMethods = methodResponse.body()?.data?.content.orEmpty().map { it.toDomain() },
                    paymentTypes = typeResponse.body()?.data.orEmpty().map { PaymentType(it.tipoId, it.nombre) }
                )
            )
        }
    }

    suspend fun saveAddress(userId: Long, id: Long?, street: String, reference: String?, cityId: Long): AccountResult<Unit> = request {
        if (street.isBlank()) return@request AccountResult.Failure("Ingresa la dirección")
        val response = if (id == null) api.createAddress(userId, AddressRequest(street.trim(), reference?.trim(), cityId))
        else api.updateAddress(userId, id, AddressRequest(street.trim(), reference?.trim(), cityId))
        if (!response.isSuccessful) return@request failure(response.code())
        AccountResult.Success(Unit)
    }

    suspend fun deleteAddress(userId: Long, id: Long): AccountResult<Unit> = request {
        val response = api.deleteAddress(userId, id)
        if (!response.isSuccessful) return@request failure(response.code())
        AccountResult.Success(Unit)
    }

    suspend fun createPayment(card: String, expiration: String, typeId: Long): AccountResult<Unit> = request {
        AccountValidator.payment(card, expiration)?.let { return@request AccountResult.Failure(it) }
        val response = api.createPayment(PaymentRequest(card.filter(Char::isDigit), PaymentExpiration.toApiDate(expiration)!!, typeId))
        if (!response.isSuccessful) return@request failure(response.code())
        AccountResult.Success(Unit)
    }

    suspend fun updatePayment(id: Long, card: String, expiration: String, typeId: Long, enabled: Boolean): AccountResult<Unit> = request {
        AccountValidator.payment(card, expiration)?.let { return@request AccountResult.Failure(it) }
        val response = api.updatePayment(id, PaymentUpdateRequest(card.filter(Char::isDigit), PaymentExpiration.toApiDate(expiration)!!, typeId, enabled))
        if (!response.isSuccessful) return@request failure(response.code())
        AccountResult.Success(Unit)
    }

    suspend fun setPaymentEnabled(id: Long, enabled: Boolean): AccountResult<Unit> = request {
        val response = if (enabled) api.enablePayment(id) else api.disablePayment(id)
        if (!response.isSuccessful) return@request failure(response.code())
        AccountResult.Success(Unit)
    }

    suspend fun changePassword(current: String, new: String, repeated: String): AccountResult<Unit> = request {
        AccountValidator.password(current, new, repeated)?.let { return@request AccountResult.Failure(it) }
        val response = api.changePassword(PasswordRequest(current, new))
        if (!response.isSuccessful) return@request failure(response.code())
        AccountResult.Success(Unit)
    }

    suspend fun checkout(userId: Long, addressId: Long, paymentId: Long, key: String): AccountResult<OrderConfirmation> {
        val baselineResponse = try { api.orders(userId) } catch (_: Exception) { return AccountResult.Failure("No se pudo preparar la verificación del pedido") }
        if (!baselineResponse.isSuccessful) return failure(baselineResponse.code())
        val baseline = baselineResponse.body()?.data?.content.orEmpty().mapTo(mutableSetOf(), OrderDto::ordenId)
        return try {
            val response = api.checkout(key, CheckoutRequest(addressId, paymentId))
            if (response.isSuccessful && response.body() != null) AccountResult.Success(response.body()!!.data.confirmation())
            else if (response.code() in 500..599) verifyAfterAmbiguous(userId, baseline)
            else failure(response.code())
        } catch (_: IOException) {
            verifyAfterAmbiguous(userId, baseline)
        } catch (_: Exception) {
            AccountResult.Failure("No fue posible registrar la orden")
        }
    }

    private suspend fun verifyAfterAmbiguous(userId: Long, baseline: Set<Long>): AccountResult<OrderConfirmation> = try {
        val response = api.orders(userId)
        val created = response.body()?.data?.content.orEmpty().firstOrNull { it.ordenId !in baseline }
        if (response.isSuccessful && created != null) AccountResult.Success(created.confirmation())
        else AccountResult.Ambiguous("No se pudo confirmar el resultado. Conserva esta pantalla y vuelve a verificar antes de reintentar")
    } catch (_: Exception) {
        AccountResult.Ambiguous("La respuesta se perdió y no fue posible verificar el historial. No repitas todavía la compra")
    }

    private suspend fun <T> request(block: suspend () -> AccountResult<T>): AccountResult<T> = try { block() }
    catch (_: SocketTimeoutException) { AccountResult.Failure("El servidor tardó demasiado en responder") }
    catch (_: IOException) { AccountResult.Failure("No se pudo conectar con el servidor") }
    catch (_: Exception) { AccountResult.Failure("No fue posible completar la operación") }

    private fun failure(code: Int): AccountResult<Nothing> = when (code) {
        401, 403 -> AccountResult.Unauthorized
        400, 422 -> AccountResult.Failure("Revisa los datos ingresados")
        404 -> AccountResult.Failure("No se encontró el recurso solicitado")
        409 -> AccountResult.Failure("La operación entra en conflicto con los datos actuales")
        in 500..599 -> AccountResult.Failure("El servicio no está disponible temporalmente")
        else -> AccountResult.Failure("No fue posible completar la operación")
    }

    private fun ProfileDto.toDomain() = Profile(usuarioId, usuario, nombre, cedula, correo, telefono)
    private fun AddressDto.toDomain() = Address(direccionId, calle, referencia, ciudadId, ciudadNombre, provinciaNombre, habilitado != false)
    private fun PaymentMethodDto.toDomain() = PaymentMethod(metodopagoId, numeroMascara, fechaExpiracion, habilitado, tipoId, tipoNombre)
    private fun OrderDto.confirmation() = OrderConfirmation(ordenId, total, fecha)
}

object AccountValidator {
    fun payment(card: String, expiration: String): String? = when {
        card.filter(Char::isDigit).length !in 13..19 -> "El número de tarjeta debe tener entre 13 y 19 dígitos"
        PaymentExpiration.toApiDate(expiration) == null -> "La fecha debe usar el formato MM/AA"
        !PaymentExpiration.isCurrentOrFuture(expiration) -> "La tarjeta está vencida"
        else -> null
    }
    fun password(current: String, new: String, repeated: String): String? = when {
        current.isBlank() -> "Ingresa la contraseña actual"
        new.length < 8 -> "La nueva contraseña debe tener al menos 8 caracteres"
        new != repeated -> "Las contraseñas nuevas no coinciden"
        else -> null
    }
}

object PaymentExpiration {
    private val monthYear = Regex("^(0[1-9]|1[0-2])/(\\d{2})$")

    fun toApiDate(value: String): String? {
        val match = monthYear.matchEntire(value.trim()) ?: return null
        val month = match.groupValues[1].toInt()
        val year = 2000 + match.groupValues[2].toInt()
        return YearMonth.of(year, month).atEndOfMonth().toString()
    }

    fun isCurrentOrFuture(value: String, current: YearMonth = YearMonth.now()): Boolean {
        val apiDate = toApiDate(value) ?: return false
        return !YearMonth.from(LocalDate.parse(apiDate)).isBefore(current)
    }

    fun display(value: String): String = runCatching {
        val date = LocalDate.parse(value)
        "%02d/%02d".format(date.monthValue, date.year % 100)
    }.getOrDefault(value)
}
