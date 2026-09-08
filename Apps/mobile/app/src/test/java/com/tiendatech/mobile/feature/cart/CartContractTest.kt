package com.tiendatech.mobile.feature.cart

import com.tiendatech.mobile.feature.cart.data.CartApi
import com.tiendatech.mobile.feature.cart.data.CartQuantityRequest
import com.tiendatech.mobile.feature.cart.domain.CartLine
import com.tiendatech.mobile.feature.cart.domain.ShoppingCart
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

class CartContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CartApi

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build().create(CartApi::class.java)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `current cart uses authenticated user path`() = runTest {
        server.enqueue(json("""{"status":200,"data":{"carritoId":12,"usuarioId":7,"total":0},"message":"OK"}"""))
        assertEquals(12L, api.current(7).body()?.data?.carritoId)
        assertEquals("/api/carrito/7", server.takeRequest().path)
    }

    @Test fun `cart page decodes server lines`() = runTest {
        server.enqueue(json("""{"status":200,"data":{"content":[{"carritoId":12,"productoId":9,"cantidad":2,"precioUnitario":1500.5}],"page":0,"size":20,"totalElements":1,"totalPages":1},"message":"OK"}"""))
        val page = api.lines(12).body()?.data
        assertEquals(9L, page?.content?.single()?.productoId)
        assertEquals(2, page?.content?.single()?.cantidad)
    }

    @Test fun `add sends only product and quantity`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(api.add(12, CartQuantityRequest(9, 3)).isSuccessful)
        val request = server.takeRequest()
        assertEquals("/api/carrito/12/agregar", request.path)
        assertEquals("{\"productoId\":9,\"cantidad\":3}", request.body.readUtf8())
    }

    @Test fun `cart totals are calculated from trusted unit prices`() {
        val cart = ShoppingCart(1, listOf(CartLine(8, 2, 100.0, null), CartLine(9, 3, 50.0, null)))
        assertEquals(5, cart.units)
        assertEquals(350.0, cart.total, 0.0)
    }

    private fun json(body: String) = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)
}
