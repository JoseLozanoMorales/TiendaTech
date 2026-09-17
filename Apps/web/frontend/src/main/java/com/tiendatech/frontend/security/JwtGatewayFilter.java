package com.tiendatech.frontend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtGatewayFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final Key signingKey;
    private final List<String> publicPaths;
    private final List<String> publicReadPaths;
    private final List<String> protectedPaths;
    private final List<String> observabilityPaths;
    private final String observabilityToken;

    public JwtGatewayFilter(Environment environment) {
        String secret = environment.getRequiredProperty("tiendatech.security.jwt.secret");
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.publicPaths = configuredOrDefault(
                environment,
                "tiendatech.security.jwt.public-paths",
                List.of(
                        "/api/login",
                        "/auth/**",
                        "/api/otp/**",
                        "/api/usuarios/crear",
                        "/api/usuarios/recuperar-password",
                        "/api/provincias/**",
                        "/api/ciudades/**",
                        "/uploads/**",
                        "/assets/**",
                        "/favicon.ico"
                ));
        this.publicReadPaths = configuredOrDefault(
                environment,
                "tiendatech.security.jwt.public-read-paths",
                List.of(
                        "/api/categorias/**",
                        "/api/marcas/**",
                        "/api/gamas/**",
                        "/api/galeria/**",
                        "/api/galeria_v2/**",
                        "/api/productos/**",
                        "/api/provincias/**",
                        "/api/ciudades/**"
                ));
        this.protectedPaths = configuredOrDefault(
                environment,
                "tiendatech.security.jwt.protected-paths",
                List.of("/api/**"));
        // Punto 17 de la guia de cierre: /metrics y /health no coinciden ni
        // con publicPaths ni con protectedPaths, asi que doFilterInternal
        // los dejaba pasar sin ningun chequeo. Requieren un token estatico
        // propio (no un JWT de usuario: el scraper de Prometheus y el
        // healthcheck de Docker no tienen sesion).
        this.observabilityPaths = configuredOrDefault(
                environment,
                "tiendatech.security.observability.paths",
                List.of("/metrics", "/metrics/**", "/health", "/health/**"));
        this.observabilityToken = environment.getRequiredProperty("tiendatech.security.observability.token");
    }

    private List<String> configuredOrDefault(Environment environment, String key, List<String> fallback) {
        String[] values = environment.getProperty(key, String[].class);
        if (values == null || values.length == 0) {
            return fallback;
        }
        return List.of(values);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (isObservability(request)) {
            if (hasValidObservabilityToken(request)) {
                filterChain.doFilter(request, response);
            } else {
                unauthorized(response, "Token de observabilidad requerido");
            }
            return;
        }

        if (isPublic(request) || !isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER)) {
            unauthorized(response, "JWT requerido");
            return;
        }

        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(authorization.substring(BEARER.length()))
                    .getBody();

            Map<String, String> trustedHeaders = new LinkedHashMap<>();
            trustedHeaders.put("X-User-Id", claims.getSubject());
            trustedHeaders.put("X-Usuario", stringClaim(claims, "username"));
            trustedHeaders.put("X-User-Role", stringClaim(claims, "role"));

            filterChain.doFilter(new TrustedUserHeaderRequest(request, trustedHeaders), response);
        } catch (JwtException | IllegalArgumentException ex) {
            unauthorized(response, "JWT invalido o expirado");
        }
    }

    private boolean isPublic(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if (publicPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path))) {
            return true;
        }
        String method = request.getMethod();
        return ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method))
                && publicReadPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        return protectedPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isObservability(HttpServletRequest request) {
        String path = request.getRequestURI();
        return observabilityPaths.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean hasValidObservabilityToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER)) {
            return false;
        }
        byte[] provided = authorization.substring(BEARER.length()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = observabilityToken.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(provided, expected);
    }

    private String stringClaim(Claims claims, String name) {
        Object value = claims.get(name);
        return value == null ? "" : String.valueOf(value);
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"status\":401,\"data\":null,\"message\":\"" + message
                + "\",\"timestamp\":\"" + java.time.Instant.now() + "\"}");
    }

    private static final class TrustedUserHeaderRequest extends HttpServletRequestWrapper {
        private final Map<String, String> trustedHeaders;

        private TrustedUserHeaderRequest(HttpServletRequest request, Map<String, String> trustedHeaders) {
            super(request);
            this.trustedHeaders = trustedHeaders;
        }

        @Override
        public String getHeader(String name) {
            String trusted = trustedHeader(name);
            return trusted != null ? trusted : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String trusted = trustedHeader(name);
            if (trusted != null) {
                return Collections.enumeration(List.of(trusted));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>();
            Enumeration<String> originalNames = super.getHeaderNames();
            while (originalNames.hasMoreElements()) {
                String name = originalNames.nextElement();
                if (trustedHeader(name) == null) {
                    names.add(name);
                }
            }
            names.addAll(trustedHeaders.keySet());
            return Collections.enumeration(names);
        }

        private String trustedHeader(String name) {
            String normalized = name.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : trustedHeaders.entrySet()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).equals(normalized)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }
}
