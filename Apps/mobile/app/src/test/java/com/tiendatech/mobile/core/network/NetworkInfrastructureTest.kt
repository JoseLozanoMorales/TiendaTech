package com.tiendatech.mobile.core.network

import com.tiendatech.mobile.core.security.SessionTokenProvider
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class NetworkInfrastructureTest {

    private lateinit var server: MockWebServer
    private lateinit var json: Json
    private var testToken: String? = null

    private val tokenProvider = object : SessionTokenProvider {
        override fun getToken(): String? = testToken
    }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        json = Json { ignoreUnknownKeys = true }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun diagnosticRequest_usesExpectedPathAndQuery() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":200,"data":[],"message":"OK"}"""))

        service().testConnection()

        assertEquals("/api/productos?page=0&size=1", server.takeRequest().path)
    }

    @Test
    fun successfulJsonResponse_returnsSuccess() = runBlocking {
        server.enqueue(jsonResponse(200, """{"status":200,"data":[{"producto_id":1,"unknown":true}],"message":"OK"}"""))

        val result = safeApiCall(json) { service().testConnection() }

        assertTrue(result is NetworkResult.Success)
        assertEquals(1, (result as NetworkResult.Success).data.data.size)
    }

    @Test
    fun messageJson_isDecodedForHttpErrors() = runBlocking {
        server.enqueue(jsonResponse(422, "{\"message\":\"Datos inválidos\"}"))

        val result = safeApiCall(json) { service().testConnection() }

        assertEquals(NetworkResult.HttpError(422, "Datos inválidos"), result)
    }

    @Test
    fun errorJson_isDecodedForHttpErrors() = runBlocking {
        server.enqueue(jsonResponse(500, "{\"error\":\"Error interno\"}"))

        val result = safeApiCall(json) { service().testConnection() }

        assertEquals(NetworkResult.HttpError(500, "Error interno"), result)
    }

    @Test
    fun plainText_isPreservedForHttpErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setBody("Espera un momento"))

        val result = safeApiCall(json) { service().testConnection() }

        assertEquals(NetworkResult.HttpError(429, "Espera un momento"), result)
    }

    @Test
    fun timeout_isClassifiedWithoutExposingTechnicalMessage() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val result = safeApiCall(json) {
            service(readTimeoutMillis = 100).testConnection()
        }

        assertTrue(result is NetworkResult.Timeout)
        assertEquals("Tiempo de espera agotado", (result as NetworkResult.Timeout).message)
    }

    @Test
    fun authInterceptor_addsBearerAndAcceptWhenTokenExists() = runBlocking {
        testToken = "fake-test-token"
        server.enqueue(jsonResponse(200, """{"status":200,"data":[],"message":"OK"}"""))

        service().testConnection()

        val request = server.takeRequest()
        assertEquals("Bearer fake-test-token", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
    }

    @Test
    fun authInterceptor_omitsAuthorizationAndIdentityHeadersWithoutToken() = runBlocking {
        testToken = null
        server.enqueue(jsonResponse(200, """{"status":200,"data":[],"message":"OK"}"""))

        service().testConnection()

        val request = server.takeRequest()
        assertNull(request.getHeader("Authorization"))
        assertNull(request.getHeader("X-User-Id"))
        assertNull(request.getHeader("X-Usuario"))
        assertNull(request.getHeader("X-User-Role"))
    }

    private fun service(readTimeoutMillis: Long = 2_000): DiagnosticService {
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(tokenProvider))
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DiagnosticService::class.java)
    }

    private fun jsonResponse(code: Int, body: String): MockResponse = MockResponse()
        .setResponseCode(code)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
