package com.tiendatech.mobile.feature.cart.data

import com.tiendatech.mobile.feature.cart.domain.CartLine
import com.tiendatech.mobile.feature.cart.domain.CartResult
import com.tiendatech.mobile.feature.cart.domain.ShoppingCart
import com.tiendatech.mobile.feature.catalog.data.CatalogRepository
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CartRepository @Inject constructor(
    private val api: CartApi,
    private val catalog: CatalogRepository
) {
    suspend fun load(userId: Long): CartResult<ShoppingCart> = request {
        val cartResponse = api.current(userId)
        if (!cartResponse.isSuccessful) return@request failure(cartResponse.code())
        val cart = cartResponse.body()?.data ?: return@request CartResult.Failure("El servidor devolvió un carrito vacío")
        val linesResponse = api.lines(cart.carritoId)
        if (!linesResponse.isSuccessful) return@request failure(linesResponse.code())
        val lines = linesResponse.body()?.data?.content.orEmpty().map { line ->
            CartLine(line.productoId, line.cantidad, line.precioUnitario, catalog.cachedProduct(line.productoId))
        }
        CartResult.Success(ShoppingCart(cart.carritoId, lines))
    }

    suspend fun add(userId: Long, productId: Long, quantity: Int): CartResult<Unit> = request {
        if (quantity <= 0) return@request CartResult.Failure("La cantidad debe ser mayor que cero")
        val cartResponse = api.current(userId)
        if (!cartResponse.isSuccessful) return@request failure(cartResponse.code())
        val cartId = cartResponse.body()?.data?.carritoId ?: return@request CartResult.Failure("No se pudo obtener el carrito")
        val response = api.add(cartId, CartQuantityRequest(productId, quantity))
        if (!response.isSuccessful) return@request failure(response.code())
        CartResult.Success(Unit)
    }

    suspend fun update(cartId: Long, productId: Long, quantity: Int): CartResult<Unit> = request {
        val response = api.update(cartId, productId, QuantityRequest(quantity))
        if (!response.isSuccessful) return@request failure(response.code())
        CartResult.Success(Unit)
    }

    suspend fun remove(cartId: Long, productId: Long): CartResult<Unit> = request {
        val response = api.remove(cartId, productId)
        if (!response.isSuccessful) return@request failure(response.code())
        CartResult.Success(Unit)
    }

    private suspend fun <T> request(block: suspend () -> CartResult<T>): CartResult<T> = try {
        block()
    } catch (_: SocketTimeoutException) {
        CartResult.Failure("El servidor tardó demasiado en responder")
    } catch (_: IOException) {
        CartResult.Failure("No se pudo conectar con el servidor")
    } catch (_: Exception) {
        CartResult.Failure("No fue posible actualizar el carrito")
    }

    private fun failure(status: Int): CartResult<Nothing> = when (status) {
        401, 403 -> CartResult.Unauthorized
        404 -> CartResult.Failure("No se encontró el carrito o producto")
        409 -> CartResult.Failure("No fue posible modificar el carrito")
        in 500..599 -> CartResult.Failure("El servicio de carrito no está disponible")
        else -> CartResult.Failure("No fue posible completar la operación")
    }
}
