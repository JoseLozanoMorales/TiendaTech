package com.tiendatech.frontend.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Protección local por ventana fija. Varias réplicas requieren un limitador compartido. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayTrafficFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(GatewayTrafficFilter.class);
    private final Clock clock;
    private final MeterRegistry registry;
    private final AtomicInteger active = new AtomicInteger();
    private final int limit;
    private final int maxClients;
    private final long windowMillis;
    private final Map<String, Integer> counts = new HashMap<>();
    private long windowStart;

    @Autowired
    public GatewayTrafficFilter(Environment environment, MeterRegistry registry) {
        this(environment, Clock.systemUTC(), registry);
    }

    // Sin MeterRegistry: usado por la prueba de configuracion invalida
    // (rechazaConfiguracionInvalida), que no necesita metricas, solo
    // disparar la validacion de limites del constructor.
    GatewayTrafficFilter(Environment environment) {
        this(environment, Clock.systemUTC(), null);
    }

    GatewayTrafficFilter(Environment environment, Clock clock) {
        this(environment, clock, null);
    }

    GatewayTrafficFilter(Environment environment, Clock clock, MeterRegistry registry) {
        this.clock = clock;
        this.registry = registry;
        if (registry != null) {
            Gauge.builder("active_connections", active, AtomicInteger::get)
                    .tag("service", "tiendatech-gateway").register(registry);
        }
        limit = environment.getProperty("GATEWAY_RATE_LIMIT_REQUESTS", Integer.class, 300);
        maxClients = environment.getProperty("GATEWAY_RATE_LIMIT_MAX_CLIENTS", Integer.class, 10000);
        int seconds = environment.getProperty("GATEWAY_RATE_LIMIT_WINDOW_SECONDS", Integer.class, 60);
        if (limit < 1 || maxClients < 1 || seconds < 1) {
            throw new IllegalArgumentException("Gateway rate limits must be positive");
        }
        windowMillis = seconds * 1000L;
        windowStart = clock.millis();
    }

    // Usa el par de transporte; nunca confía en X-Forwarded-For enviado por el cliente.
    synchronized long retryAfter(String peer) {
        long now = clock.millis();
        if (now < windowStart || now - windowStart >= windowMillis) {
            counts.clear();
            windowStart = now;
        }
        int count = counts.getOrDefault(peer, 0);
        if (count >= limit || (count == 0 && counts.size() >= maxClients)) {
            return Math.max(1, (windowMillis - (now - windowStart) + 999) / 1000);
        }
        counts.put(peer, count + 1);
        return 0;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean failed = false;
        long start = System.nanoTime();
        active.incrementAndGet();
        try {
            String path = request.getRequestURI();
            boolean api = path.equals("/api") || path.startsWith("/api/")
                    || path.equals("/auth") || path.startsWith("/auth/");
            long retry = api && !"OPTIONS".equals(request.getMethod())
                    ? retryAfter(request.getRemoteAddr()) : 0;
            if (retry > 0) {
                response.setStatus(429);
                response.setHeader("Retry-After", Long.toString(retry));
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write("{\"status\":\"error\",\"data\":null,"
                        + "\"message\":\"Limite de peticiones excedido\",\"timestamp\":\""
                        + Instant.now(clock) + "\"}");
                return;
            }
            chain.doFilter(request, response);
        } catch (IOException | ServletException | RuntimeException error) {
            failed = true;
            throw error;
        } finally {
            int status = failed ? 500 : response.getStatus();
            if (registry != null) {
                // Mismo tag "route" que HttpObservabilityFilter (la implementacion de
                // referencia en los microservicios Java), para que el Gateway quede con
                // el mismo formato y convencion de nombres, no solo los mismos 3 nombres
                // de metrica -- el punto 17 de la guia de cierre senalo que el Gateway
                // no emitia esta etiqueta pese a que el documento afirmaba lo contrario.
                String[] tags = {"service", "tiendatech-gateway", "method", request.getMethod(),
                        "route", request.getRequestURI(), "status", Integer.toString(status)};
                Counter.builder("request_count").tags(tags).register(registry).increment();
                Timer.builder("request_duration").publishPercentileHistogram()
                        .tags(tags).register(registry)
                        .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            }
            active.decrementAndGet();
            LOG.info("gateway_request timestamp={} method={} path={} origin={} status={}",
                    Instant.now(clock), safe(request.getMethod()), safe(request.getRequestURI()),
                    safe(request.getRemoteAddr()), status);
        }
    }

    private static String safe(String value) {
        if (value == null) return "unknown";
        return value.substring(0, Math.min(value.length(), 512)).replaceAll("[\\p{Cntrl}\\s]", "_");
    }
}
