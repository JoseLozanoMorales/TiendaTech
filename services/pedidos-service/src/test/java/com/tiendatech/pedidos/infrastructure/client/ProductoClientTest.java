package com.tiendatech.pedidos.infrastructure.client;

import com.tiendatech.pedidos.domain.ProductoInfo;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductoClientTest {

    @Test
    void obtienePrecioEIvaConUnaConsultaPuntual() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RetryRegistry retries = RetryRegistry.of(RetryConfig.custom().maxAttempts(1).build());
        ProductoClient client = new ProductoClient(
                builder,
                "http://productos",
                CircuitBreakerRegistry.ofDefaults(),
                retries,
                new InboundAuthorizationInterceptor());

        server.expect(once(), requestTo("http://productos/api/productos/11"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":200,"data":{"producto_id":11,"preciounitario":149.99,
                        "iva_id":2,"iva":15.00},"message":"OK"}
                        """, MediaType.APPLICATION_JSON));

        ProductoInfo producto = client.obtenerPrecioEIva(11);

        assertEquals(11, producto.productoId());
        assertEquals(new BigDecimal("149.99"), producto.precioUnitario());
        assertEquals(2, producto.ivaId());
        assertEquals(new BigDecimal("15.00"), producto.porcentajeIva());
        server.verify();
    }
}
