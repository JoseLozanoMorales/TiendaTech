package com.tiendatech.pedidos.infrastructure.client;

import com.tiendatech.pedidos.domain.DetalleOrden;
import com.tiendatech.pedidos.domain.Orden;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FacturaClientTest {

    @Test
    void enviaYExigeLaEstrategiaConfigurada() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FacturaClient client = client(builder, "saga");

        server.expect(once(), requestTo("http://ventas/api/facturas"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Coordination-Strategy", "saga"))
                .andRespond(withSuccess(
                        "{\"data\":{\"facturaId\":91,\"coordination\":\"saga\"}}",
                        MediaType.APPLICATION_JSON));

        assertEquals(91, client.generarFactura(orden(), detalle()));
        server.verify();
    }

    @Test
    void rechazaUnaRespuestaDeOtraEstrategia() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FacturaClient client = client(builder, "saga");
        server.expect(once(), requestTo("http://ventas/api/facturas"))
                .andRespond(withSuccess(
                        "{\"data\":{\"facturaId\":91,\"coordination\":\"2pc\"}}",
                        MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> client.generarFactura(orden(), detalle()));
        server.verify();
    }

    private static FacturaClient client(RestClient.Builder builder, String coordination) {
        return new FacturaClient(builder, "http://ventas", coordination,
                CircuitBreakerRegistry.ofDefaults(), new InboundAuthorizationInterceptor());
    }

    private static Orden orden() {
        return new Orden(7, 8, 9, 10, new BigDecimal("100.00"),
                new BigDecimal("115.00"), LocalDate.of(2026, 9, 5));
    }

    private static List<DetalleOrden> detalle() {
        return List.of(new DetalleOrden(7, 11, 1, new BigDecimal("100.00"),
                new BigDecimal("100.00"), new BigDecimal("15.00"), new BigDecimal("115.00")));
    }
}
