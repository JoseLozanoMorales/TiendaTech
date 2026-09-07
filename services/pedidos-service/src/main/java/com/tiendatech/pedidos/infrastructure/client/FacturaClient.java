package com.tiendatech.pedidos.infrastructure.client;

import com.tiendatech.pedidos.domain.FacturaPort;
import com.tiendatech.pedidos.domain.DetalleOrden;
import com.tiendatech.pedidos.domain.Orden;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.function.Supplier;

@Component
public class FacturaClient implements FacturaPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final String coordination;

    @Autowired
    public FacturaClient(RestClient.Builder restClientBuilder,
                         @Value("${ventas.service.base-url}") String ventasBaseUrl,
                         @Value("${coordination.strategy:${COORD:2pc}}") String coordination,
                         CircuitBreakerRegistry circuitBreakerRegistry,
                         InboundAuthorizationInterceptor authorizationInterceptor,
                         @Value("${pedidos.factura-client.connect-timeout-ms:2000}") long connectTimeoutMs,
                         @Value("${pedidos.factura-client.read-timeout-ms:30000}") long readTimeoutMs) {
        this(configurarTimeoutDeFacturacion(restClientBuilder, connectTimeoutMs, readTimeoutMs),
                ventasBaseUrl, coordination, circuitBreakerRegistry, authorizationInterceptor);
    }

    // Constructor conservado para pruebas con MockRestServiceServer. En produccion
    // Spring usa el constructor @Autowired de arriba y aplica el timeout dedicado.
    FacturaClient(RestClient.Builder restClientBuilder,
                  String ventasBaseUrl,
                  String coordination,
                  CircuitBreakerRegistry circuitBreakerRegistry,
                  InboundAuthorizationInterceptor authorizationInterceptor) {
        this.coordination = "saga".equalsIgnoreCase(coordination == null ? "" : coordination.trim())
                ? "saga" : "2pc";
        this.restClient = restClientBuilder.baseUrl(ventasBaseUrl)
                .requestInterceptor(authorizationInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("facturaClient");
    }

    private static RestClient.Builder configurarTimeoutDeFacturacion(RestClient.Builder builder,
                                                                      long connectTimeoutMs,
                                                                      long readTimeoutMs) {
        if (connectTimeoutMs <= 0 || readTimeoutMs <= 0) {
            throw new IllegalArgumentException("Los timeouts de facturacion deben ser positivos");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        // El Builder es prototype: reemplazar su factory solo afecta a este
        // cliente. ProductoClient y UsuarioClient conservan el limite general.
        return builder.requestFactory(factory);
    }

    // Escritura (crea una factura en ventas-service): solo circuit breaker, SIN
    // reintento. Este servicio no controla ventas-service y no puede demostrar
    // que POST /api/facturas sea idempotente; reintentar tras un timeout podria
    // duplicar la factura de la misma orden. El llamador (OrdenService) ya
    // convierte cualquier fallo aqui en un mensaje explicito: la orden quedo
    // creada pero la facturacion fallo, en vez de fingir exito o reintentar solo.
    @Override
    public Integer generarFactura(Orden orden, List<DetalleOrden> detalle) {
        List<Map<String, Object>> lineas = detalle.stream().map(item -> Map.<String, Object>of(
                "productoId", item.getProductoId(), "cantidad", item.getCantidad(),
                "precio", item.getPrecioUnitario(), "subtotal", item.getSubtotal(),
                "iva", item.getIva(), "total", item.getTotal())).toList();
        Map<String, Object> snapshot = Map.of(
                "ordenId", orden.getOrdenId(), "fechaOrden", orden.getFecha(),
                "usuarioId", orden.getUsuarioId(), "subtotal", orden.getSubtotal(),
                "total", orden.getTotal(), "lineas", lineas);
        Supplier<Map> llamada = () -> restClient.post()
                .uri("/api/facturas")
                .header("X-Coordination-Strategy", coordination)
                .body(snapshot)
                .retrieve()
                .body(Map.class);
        Map resp = CircuitBreaker.decorateSupplier(circuitBreaker, llamada).get();
        Map data = resp != null && resp.get("data") instanceof Map envelopeData ? envelopeData : resp;
        if (data == null || !(data.get("facturaId") instanceof Number facturaId)) {
            throw new IllegalStateException("ventas-service no devolvió el identificador de la factura");
        }
        if (!coordination.equalsIgnoreCase(String.valueOf(data.get("coordination")))) {
            throw new IllegalStateException(
                    "ventas-service no confirmó la estrategia de coordinación " + coordination);
        }
        return facturaId.intValue();
    }
}
