package com.tiendatech.mobile.feature.orders

import com.tiendatech.mobile.feature.orders.data.OrdersApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class OrdersContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: OrdersApi
    @Before fun setUp() { server = MockWebServer(); server.start(); api = Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(Json.asConverterFactory("application/json".toMediaType())).build().create(OrdersApi::class.java) }
    @After fun tearDown() = server.shutdown()

    @Test fun `orders use own user paged endpoint`() = runTest {
        server.enqueue(json("""{"status":200,"data":{"content":[],"page":1,"size":20,"totalElements":21,"totalPages":2},"message":"OK"}"""))
        val page = api.orders(7, 1).body()?.data
        assertEquals(2, page?.totalPages); assertEquals("/api/ordenes/usuario/7?page=1&size=20", server.takeRequest().path)
    }

    @Test fun `order detail decodes monetary fields`() = runTest {
        server.enqueue(json("""{"status":200,"data":{"content":[{"ordenId":5,"productoId":9,"cantidad":2,"precioUnitario":100,"subtotal":200,"iva":30,"total":230}],"page":0,"size":100,"totalElements":1,"totalPages":1},"message":"OK"}"""))
        val line = api.orderLines(5).body()?.data?.content?.single()
        assertEquals(30.0, line?.iva ?: 0.0, 0.0); assertEquals(230.0, line?.total ?: 0.0, 0.0)
    }

    @Test fun `invoice list is filtered by authenticated user id`() = runTest {
        server.enqueue(json("""{"status":200,"data":[],"message":"OK"}""")); assertTrue(api.invoices(7).isSuccessful)
        assertEquals("/api/facturas?usuarioId=7", server.takeRequest().path)
    }

    @Test fun `invoice response links to order`() = runTest {
        server.enqueue(json("""{"status":200,"data":[{"facturaId":8,"ordenId":5,"usuarioId":7,"fechaEmision":"2026-08-21","fechaOrden":"2026-08-21","cedula":"1","nombre":"Cliente","correo":"c@x.co","telefono":"1","direccionEntrega":"Calle 1","subtotal":200,"total":230,"numero":"FAC-8"}],"message":"OK"}"""))
        val invoice = api.invoices(7).body()?.data?.single(); assertEquals(5L, invoice?.ordenId); assertEquals("FAC-8", invoice?.numero)
    }

    @Test fun `invoice response accepts nullable customer fields`() = runTest {
        server.enqueue(json("""{"status":200,"data":[{"facturaId":1,"ordenId":1000001,"usuarioId":4,"fechaEmision":"2026-08-25","fechaOrden":"2026-08-25","cedula":null,"nombre":"José Morales","correo":"jose@example.com","telefono":null,"direccionEntrega":null,"subtotal":473.01,"total":543.96,"numero":"FAC-E3-1000001"}],"message":"OK"}"""))
        val invoice = api.invoices(4).body()?.data?.single()
        assertEquals(1000001L, invoice?.ordenId)
        assertEquals(null, invoice?.direccionEntrega)
    }

    @Test fun `invoice detail has product name`() = runTest {
        server.enqueue(json("""{"status":200,"data":[{"productoId":9,"nombreProducto":"Procesador","cantidad":1,"precio":100,"subtotal":100,"iva":15,"total":115}],"message":"OK"}"""))
        assertEquals("Procesador", api.invoiceLines(8).body()?.data?.single()?.nombreProducto)
    }

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
}
