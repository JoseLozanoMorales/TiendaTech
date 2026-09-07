package com.tiendatech.pedidos.infrastructure.client;

import com.tiendatech.pedidos.domain.ProductoInfo;
import com.tiendatech.pedidos.domain.ProductoPort;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.function.Supplier;

@Component
public class ProductoClient implements ProductoPort {

    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ProductoClient(RestClient.Builder restClientBuilder,
                           @Value("${productos.service.base-url}") String productosBaseUrl,
                           CircuitBreakerRegistry circuitBreakerRegistry,
                           RetryRegistry retryRegistry,
                           InboundAuthorizationInterceptor authorizationInterceptor) {
        this.restClient = restClientBuilder.baseUrl(productosBaseUrl)
                .requestInterceptor(authorizationInterceptor)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("productoClient");
        this.retry = retryRegistry.retry("productoClient");
    }

    /**
     * GET /api/productos/{id}: una sola consulta puntual por clave primaria.
     * detalleSql() ya hace JOIN con productos.iva, asi que precio e IVA llegan
     * juntos -- no hace falta una segunda llamada a /api/sp/ivas.
     *
     * Antes esto traia hasta 1000 productos completos (?size=1000) para
     * quedarse con uno via filtro en memoria: el mismo antipatron que ya se
     * habia corregido en la paginacion de ordenes, aqui sin corregir todavia.
     * Contra el CockroachDB de AWS (--max-sql-memory bajo) esa consulta
     * agotaba el presupuesto de memoria SQL con una sola peticion, sin
     * necesitar ninguna concurrencia.
     */
    @Override
    public ProductoInfo obtenerPrecioEIva(Integer productoId) {
        ApiEnvelope<ProductoDetalleItem> respuesta;
        try {
            respuesta = lectura(() -> restClient.get()
                    .uri("/api/productos/{id}", productoId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiEnvelope<ProductoDetalleItem>>() {
                    }));
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new IllegalArgumentException("Producto " + productoId + " no encontrado en productos-service");
        }
        ProductoDetalleItem detalle = respuesta == null ? null : respuesta.data();
        if (detalle == null) {
            throw new IllegalArgumentException("Producto " + productoId + " no encontrado en productos-service");
        }
        return new ProductoInfo(detalle.productoId(), detalle.precioUnitario(), detalle.ivaId(), detalle.porcentajeIva());
    }

    // Lectura (GET), idempotente por naturaleza: circuit breaker + reintento.
    // Se decora solo la llamada HTTP, no la logica de negocio de alrededor
    // (los IllegalArgumentException de "no encontrado" quedan fuera y nunca se
    // reintentan).
    private <T> T lectura(Supplier<T> operacion) {
        Supplier<T> conCircuitBreaker = CircuitBreaker.decorateSupplier(circuitBreaker, operacion);
        Supplier<T> conReintento = Retry.decorateSupplier(retry, conCircuitBreaker);
        return conReintento.get();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ProductoDetalleItem(
            @JsonProperty("producto_id") Integer productoId,
            @JsonProperty("preciounitario") BigDecimal precioUnitario,
            @JsonProperty("iva_id") Integer ivaId,
            @JsonProperty("iva") BigDecimal porcentajeIva) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ApiEnvelope<T>(T data) { }
}
