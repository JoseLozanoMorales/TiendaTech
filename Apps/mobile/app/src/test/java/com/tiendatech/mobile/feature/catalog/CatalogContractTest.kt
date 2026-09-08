package com.tiendatech.mobile.feature.catalog

import com.tiendatech.mobile.feature.catalog.data.CatalogApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class CatalogContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: CatalogApi

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(CatalogApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `products decode the production response envelope`() = runTest {
        server.enqueue(json("""{"status":200,"data":[{"producto_id":1,"nombre":"GPU","preciounitario":399.99,"stock":10,"habilitado":true,"galeria_id":1}],"message":"OK","timestamp":"2026-09-08T03:52:16Z"}"""))

        val response = api.products(page = 0, size = 5)

        assertEquals(1L, response.body()?.data?.single()?.productIdSnake)
        assertEquals("GPU", response.body()?.data?.single()?.nombre)
        assertEquals("/api/productos?page=0&size=5", server.takeRequest().path)
    }

    @Test
    fun `categories decode the production response envelope`() = runTest {
        server.enqueue(json("""{"status":200,"data":[{"id":6,"id_categoria":6,"nombre":"Tarjeta grafica","slug":"tarjeta-grafica"}],"message":"OK","timestamp":"2026-09-08T03:52:16Z"}"""))

        val category = api.categories().body()?.data?.single()

        assertEquals(6L, category?.id)
        assertEquals("Tarjeta grafica", category?.nombre)
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
