package com.tiendatech.ventas.infrastructure.client;

import com.tiendatech.ventas.domain.FacturaDetalle;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventarioClientTest {

    @Test
    void enviaPayloadIdempotenteYDejaElTimestampAlServidor() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> idempotencyKey = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/sp/movimiento-inventario", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            query.set(exchange.getRequestURI().getQuery());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        InventarioClient client = new InventarioClient(RestClient.builder(),
                "http://127.0.0.1:" + server.getAddress().getPort(), "internal", 1000, 1000);
        FacturaDetalle detalle = new FacturaDetalle(91, 11, "Producto", 2,
                BigDecimal.TEN, new BigDecimal("20"), new BigDecimal("3"),
                new BigDecimal("23"));

        try {
            client.registrarSalidasPorFactura(91, List.of(detalle), "coord-2pc");
        } finally {
            server.stop(0);
        }

        assertEquals("factura-inventario-91", idempotencyKey.get());
        assertEquals("usuario=coord-2pc", query.get());
        assertTrue(body.get().contains("\"producto_id\":11"));
        assertTrue(body.get().contains("\"subtipo_id\":4"));
        assertTrue(body.get().contains("\"cantidad\":2"));
        assertTrue(body.get().contains("\"referencia\":\"FAC-91\""));
        assertFalse(body.get().contains("\"fecha\""));
    }
}
