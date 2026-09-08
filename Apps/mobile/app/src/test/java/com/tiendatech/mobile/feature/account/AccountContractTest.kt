package com.tiendatech.mobile.feature.account

import com.tiendatech.mobile.feature.account.data.AccountApi
import com.tiendatech.mobile.feature.account.data.AccountValidator
import com.tiendatech.mobile.feature.account.data.CheckoutRequest
import com.tiendatech.mobile.feature.account.data.PasswordRequest
import com.tiendatech.mobile.feature.account.data.PaymentExpiration
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
import java.time.YearMonth

class AccountContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AccountApi

    @Before fun setUp() {
        server = MockWebServer(); server.start()
        api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build().create(AccountApi::class.java)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `checkout sends stable idempotency header and identifiers`() = runTest {
        server.enqueue(json("""{"status":201,"data":{"ordenId":5,"usuarioId":7,"direccionId":2,"metodopagoId":3,"subtotal":100,"total":115,"fecha":"2026-08-21"},"message":"OK"}""", 201))
        assertTrue(api.checkout("intent-123", CheckoutRequest(2, 3)).isSuccessful)
        val request = server.takeRequest()
        assertEquals("intent-123", request.getHeader("Idempotency-Key"))
        assertEquals("{\"direccionId\":2,\"metodopagoId\":3}", request.body.readUtf8())
    }

    @Test fun `account lists decode the production response envelope`() = runTest {
        server.enqueue(json("""{"status":200,"data":[{"direccionId":1,"usuarioId":4,"calle":"Vía Valencia","ciudadId":1}],"message":"OK"}"""))

        val address = api.addresses(4).body()?.data?.single()

        assertEquals(1L, address?.direccionId)
        assertEquals("Vía Valencia", address?.calle)
    }

    @Test fun `profile decodes its legacy inner data plus production envelope`() = runTest {
        server.enqueue(json("""{"status":200,"data":{"data":{"usuarioId":4,"usuario":"jmoralito","nombre":"José","correo":"j@example.com"}},"message":"OK"}"""))

        val profile = api.profile().body()?.data?.data

        assertEquals(4L, profile?.usuarioId)
        assertEquals("jmoralito", profile?.usuario)
    }

    @Test fun `password request excludes repeated password`() = runTest {
        server.enqueue(json("{}"))
        api.changePassword(PasswordRequest("actual", "nueva123"))
        assertEquals("{\"actual\":\"actual\",\"nueva\":\"nueva123\"}", server.takeRequest().body.readUtf8())
    }

    @Test fun `payment validation accepts academic contract`() {
        assertNull(AccountValidator.payment("4111111111111111", "12/28"))
    }

    @Test fun `payment validation rejects malformed date`() {
        assertEquals("La fecha debe usar el formato MM/AA", AccountValidator.payment("4111111111111111", "13/28"))
    }

    @Test fun `expiration converts month and year to backend date`() {
        assertEquals("2026-07-31", PaymentExpiration.toApiDate("07/26"))
        assertEquals("07/26", PaymentExpiration.display("2026-07-31"))
    }

    @Test fun `expiration rejects a previous month`() {
        assertTrue(!PaymentExpiration.isCurrentOrFuture("08/23", YearMonth.of(2026, 8)))
        assertTrue(PaymentExpiration.isCurrentOrFuture("08/26", YearMonth.of(2026, 8)))
    }

    @Test fun `password confirmation is validated locally`() {
        assertEquals("Las contraseñas nuevas no coinciden", AccountValidator.password("actual", "nueva123", "distinta9"))
    }

    private fun json(body: String, code: Int = 200) = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
}
