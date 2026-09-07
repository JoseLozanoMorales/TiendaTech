package com.tiendatech.ventas.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;

import com.tiendatech.ventas.domain.FacturaDetalle;
import com.tiendatech.ventas.domain.InventarioPort;
import java.util.List;
import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class InventarioClient implements InventarioPort {

    // subtipo_id  en la BD
    private static final int SUBTIPO_VENTA = 4;

    private final RestClient restClient;

    public InventarioClient(RestClient.Builder restClientBuilder,
                            @Value("${inventario.service.base-url}") String inventarioBaseUrl,
                            @Value("${INTERNAL_SERVICE_TOKEN}") String internalToken,
                            @Value("${inventario.service.connect-timeout-ms:3000}") int connectTimeoutMs,
                            @Value("${inventario.service.read-timeout-ms:5000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        this.restClient = restClientBuilder
                .baseUrl(inventarioBaseUrl)
                .defaultHeader("X-Internal-Token", internalToken)
                .requestInterceptor((request, body, execution) -> {
                    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
                        copyHeader(attributes, request, "X-Trace-Id");
                        copyHeader(attributes, request, "X-Failure-Mode");
                    }
                    return execution.execute(request, body);
                })
                .requestFactory(requestFactory)
                .build();
    }

    private static void copyHeader(ServletRequestAttributes attributes, org.springframework.http.HttpRequest target,
                                   String name) {
        String value = attributes.getRequest().getHeader(name);
        if (value != null && !value.isBlank()) target.getHeaders().set(name, value);
    }

    /** Descuenta stock por cada línea de la factura recién generada. */
    @CircuitBreaker(name = "inventario", fallbackMethod = "registrarSalidasPorFacturaFallback")
    @Retry(name = "inventario")
    @Override
    public void registrarSalidasPorFactura(Integer facturaId, List<FacturaDetalle> detalle, String usuario) {
        List<java.util.Map<String, Object>> items = detalle.stream()
                .map(d -> java.util.Map.<String, Object>of(
                        "producto_id", d.getProductoId(),
                        "subtipo_id", SUBTIPO_VENTA,
                        "cantidad", d.getCantidad(),
                        "referencia", "FAC-" + facturaId
                ))
                .toList();

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/sp/movimiento-inventario")
                        .queryParamIfPresent("usuario", Optional.ofNullable(usuario))
                        .build())
                // Outbox y barrera sincrona pueden competir o reintentar. La
                // clave estable por factura impide descontar stock dos veces.
                .header("Idempotency-Key", "factura-inventario-" + facturaId)
                .body(items)
                .retrieve()
                .toBodilessEntity();
    }

    /** Se ejecuta cuando el circuito está abierto o se agotaron los reintentos. */
    private void registrarSalidasPorFacturaFallback(Integer facturaId, List<FacturaDetalle> detalle,
                                                    String usuario, Throwable t) {
        throw new IllegalStateException(
                "inventario-service no disponible (circuito abierto o reintentos agotados) para la factura "
                        + facturaId, t);
    }
}
