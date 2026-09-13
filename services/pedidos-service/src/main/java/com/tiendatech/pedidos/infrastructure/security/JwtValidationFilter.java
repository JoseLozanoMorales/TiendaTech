package com.tiendatech.pedidos.infrastructure.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/** Cada servicio valida firma HS256 y expiración, aun si se evita el Gateway. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class JwtValidationFilter extends OncePerRequestFilter {
    private final byte[] secret;
    private final String internalToken;
    private final ObjectMapper mapper;

    public JwtValidationFilter(Environment env, ObjectMapper mapper) {
        String value = env.getProperty("AUTH_JWT_SECRET");
        if (value == null || value.length() < 32) throw new IllegalStateException("AUTH_JWT_SECRET debe tener al menos 32 caracteres");
        secret = value.getBytes(StandardCharsets.UTF_8);
        internalToken = env.getProperty("INTERNAL_SERVICE_TOKEN", "");
        this.mapper = mapper;
    }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        if (path.equals("/api/login") || path.startsWith("/api/otp/") || path.equals("/api/usuarios/crear")
                || path.equals("/api/usuarios/recuperar-password")) return true;
        boolean read = "GET".equalsIgnoreCase(request.getMethod()) || "HEAD".equalsIgnoreCase(request.getMethod());
        return read && (path.startsWith("/api/productos") || path.startsWith("/api/categorias")
                || path.startsWith("/api/marcas") || path.startsWith("/api/gamas")
                || path.startsWith("/api/galeria") || path.startsWith("/api/provincias")
                || path.startsWith("/api/ciudades"));
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        if (hasInternalToken(request)) {
            chain.doFilter(request, response); return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) { unauthorized(response, "JWT requerido"); return; }
        try {
            validateJwt(authorization.substring(7));
            chain.doFilter(request, response);
        } catch (Exception ex) { unauthorized(response, "JWT invalido o expirado"); }
    }

    private boolean hasInternalToken(HttpServletRequest request) {
        String supplied = request.getHeader("X-Internal-Token");
        return !internalToken.isBlank() && supplied != null && MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
    }

    private void validateJwt(String token) throws Exception {
        String[] parts = token.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException();
        JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
        if (!"HS256".equals(header.path("alg").asText())) throw new IllegalArgumentException();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]))) throw new IllegalArgumentException();
        validateExpiration(parts[1]);
    }

    private void validateExpiration(String payload) throws IOException {
        JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(payload));
        if (!claims.has("exp") || claims.path("exp").asLong() <= Instant.now().getEpochSecond()) throw new IllegalArgumentException();
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 401); body.put("data", null); body.put("message", message); body.put("timestamp", Instant.now());
        mapper.writeValue(response.getWriter(), body);
    }
}

