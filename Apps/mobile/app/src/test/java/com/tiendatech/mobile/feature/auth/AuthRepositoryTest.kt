package com.tiendatech.mobile.feature.auth

import com.tiendatech.mobile.core.security.SessionStore
import com.tiendatech.mobile.feature.auth.data.AuthApi
import com.tiendatech.mobile.feature.auth.data.AuthRepository
import com.tiendatech.mobile.feature.auth.domain.AuthResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class AuthRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var store: FakeSessionStore
    private lateinit var repository: AuthRepository

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        store = FakeSessionStore()
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AuthApi::class.java)
        repository = AuthRepository(api, store)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `customer login stores access token`() = runTest {
        server.enqueue(jsonResponse("""{"status":200,"data":{"success":true,"user":{"usuarioId":7,"usuario":"cliente","nombre":"Cliente","correo":"c@t.co","id_rol":2},"access":"jwt-cliente"},"message":"OK","timestamp":"2026-09-08T04:00:00Z"}"""))

        val result = repository.login("cliente", "secreta")

        assertTrue(result is AuthResult.Success)
        assertEquals("jwt-cliente", store.getToken())
        assertEquals("/api/login", server.takeRequest().path)
    }

    @Test fun `administrative login is rejected without storing token`() = runTest {
        server.enqueue(jsonResponse("""{"status":200,"data":{"success":true,"user":{"usuarioId":1,"usuario":"admin","nombre":"Admin","correo":"a@t.co","id_rol":1},"token":"jwt-admin"},"message":"OK"}"""))

        val result = repository.login("admin", "secreta")

        assertTrue(result is AuthResult.Failure)
        assertNull(store.getToken())
    }

    @Test fun `unauthorized profile clears persisted session`() = runTest {
        store.saveToken("expired")
        server.enqueue(MockResponse().setResponseCode(401))

        val result = repository.restoreSession()

        assertTrue(result is AuthResult.Success && result.value == null)
        assertNull(store.getToken())
    }

    @Test fun `session restoration decodes double data envelope from profile`() = runTest {
        store.saveToken("jwt-cliente")
        server.enqueue(jsonResponse("""{"status":200,"data":{"data":{"usuarioId":7,"usuario":"cliente","nombre":"Cliente","correo":"c@t.co","id_rol":2}},"message":"OK"}"""))

        val result = repository.restoreSession()

        assertTrue(result is AuthResult.Success)
        assertEquals(7, (result as AuthResult.Success).value?.id)
        assertEquals("jwt-cliente", store.getToken())
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private class FakeSessionStore : SessionStore {
        private var token: String? = null
        override fun getToken(): String? = token
        override fun saveToken(token: String) { this.token = token }
        override fun clear() { token = null }
    }
}
