package com.tiendatech.mobile.feature.orders.data

import com.tiendatech.mobile.feature.catalog.data.CatalogRepository
import com.tiendatech.mobile.feature.orders.domain.Invoice
import com.tiendatech.mobile.feature.orders.domain.OrderDetail
import com.tiendatech.mobile.feature.orders.domain.OrderLine
import com.tiendatech.mobile.feature.orders.domain.OrderSummary
import com.tiendatech.mobile.feature.orders.domain.OrdersPage
import com.tiendatech.mobile.feature.orders.domain.OrdersResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrdersRepository @Inject constructor(private val api: OrdersApi, private val catalog: CatalogRepository) {
    suspend fun page(userId: Long, page: Int): OrdersResult<OrdersPage> = request {
        coroutineScope {
            val ordersCall = async { api.orders(userId, page) }
            val invoicesCall = async { runCatching { api.invoices(userId) }.getOrNull() }
            val orders = ordersCall.await(); val invoices = invoicesCall.await()
            if (!orders.isSuccessful) return@coroutineScope failure(orders.code())
            val invoiceByOrder = invoices?.takeIf { it.isSuccessful }?.body()?.data.orEmpty()
                .filter { it.usuarioId == userId }.associateBy(InvoiceDto::ordenId)
            val body = orders.body()?.data ?: return@coroutineScope OrdersResult.Failure("La respuesta de pedidos está vacía")
            OrdersResult.Success(OrdersPage(body.content.map { it.summary(invoiceByOrder[it.ordenId]) }, body.page, body.totalPages))
        }
    }

    suspend fun detail(userId: Long, orderId: Long): OrdersResult<OrderDetail> = request {
        coroutineScope {
            val orderCall = async { api.order(orderId) }
            val linesCall = async { api.orderLines(orderId) }
            val invoicesCall = async { runCatching { api.invoices(userId) }.getOrNull() }
            val orderResponse = orderCall.await(); val linesResponse = linesCall.await(); val invoicesResponse = invoicesCall.await()
            if (!orderResponse.isSuccessful) return@coroutineScope failure(orderResponse.code())
            if (!linesResponse.isSuccessful) return@coroutineScope failure(linesResponse.code())
            val order = orderResponse.body()?.data ?: return@coroutineScope OrdersResult.Failure("Pedido no encontrado")
            if (order.usuarioId != userId) return@coroutineScope OrdersResult.Failure("Pedido no encontrado")
            val ownedInvoice = invoicesResponse?.takeIf { it.isSuccessful }?.body()?.data.orEmpty()
                .firstOrNull { it.usuarioId == userId && it.ordenId == orderId }
            val invoiceLines = if (ownedInvoice != null) {
                val response = runCatching { api.invoiceLines(ownedInvoice.facturaId) }.getOrNull()
                if (response?.isSuccessful == true) response.body()?.data.orEmpty() else emptyList()
            } else emptyList()
            val names = invoiceLines.associate { it.productoId to it.nombreProducto }
            val lines = linesResponse.body()?.data?.content.orEmpty().map { line ->
                val cachedName = catalog.cachedProduct(line.productoId)?.name
                OrderLine(line.productoId, names[line.productoId] ?: cachedName, line.cantidad, line.precioUnitario, line.subtotal, line.iva, line.total)
            }
            val invoice = ownedInvoice?.let { value ->
                Invoice(value.facturaId, value.numero, value.fechaEmision, value.nombre ?: "Cliente", value.direccionEntrega ?: "Dirección no disponible", value.subtotal, value.total,
                    invoiceLines.map { OrderLine(it.productoId, it.nombreProducto, it.cantidad, it.precio, it.subtotal, it.iva, it.total) })
            }
            OrdersResult.Success(OrderDetail(order.summary(ownedInvoice), order.direccionId, order.metodopagoId, lines, invoice))
        }
    }

    private suspend fun <T> request(block: suspend () -> OrdersResult<T>): OrdersResult<T> = try { block() }
    catch (_: SocketTimeoutException) { OrdersResult.Failure("El servidor tardó demasiado en responder") }
    catch (_: IOException) { OrdersResult.Failure("No se pudo conectar con el servidor") }
    catch (_: Exception) { OrdersResult.Failure("No fue posible cargar los pedidos") }

    private fun failure(code: Int): OrdersResult<Nothing> = when (code) {
        401, 403 -> OrdersResult.Unauthorized
        404 -> OrdersResult.Failure("Pedido no encontrado")
        in 500..599 -> OrdersResult.Failure("El servicio de pedidos no está disponible")
        else -> OrdersResult.Failure("No fue posible cargar los pedidos")
    }

    private fun OrderDto.summary(invoice: InvoiceDto?) = OrderSummary(ordenId, fecha, subtotal, total, invoice?.facturaId, invoice?.numero)
}
