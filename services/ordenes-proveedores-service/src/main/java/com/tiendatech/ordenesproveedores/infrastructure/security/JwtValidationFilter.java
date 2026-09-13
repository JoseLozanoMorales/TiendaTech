package com.tiendatech.ordenesproveedores.infrastructure.security;

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
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cada servicio valida firma HS256 y expiración, aun si se evita el Gateway. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class JwtValidationFilter extends OncePerRequestFilter {

    // Rutas publicas exactas: no requieren JWT bajo ningun metodo HTTP.
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/login",
            "/api/usuarios/crear",
            "/api/usuarios/recuperar-password");
    private static final String PUBLIC_PREFIX_OTP = "/api/otp/";

    // Prefijos de catalogo que son de lectura publica (solo GET/HEAD).
    private static final List<String> PUBLIC_READ_PREFIXES = List.of(
            "/api/productos",
            "/api/categorias",
            "/api/marcas",
            "/api/gamas",
            "/api/galeria",
            "/api/provincias",
            "/api/ciudades");

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
        String method = request.getMethod();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(method)) return true;
        if (isPublicPath(path)) return true;
        return isReadOnly(method) && isPublicReadPath(path);
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path) || path.startsWith(PUBLIC_PREFIX_OTP);
    }

    private boolean isReadOnly(String method) {
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method);
    }

    private boolean isPublicReadPath(String path) {
        return PUBLIC_READ_PREFIXES.stream().anyMatch(path::startsWith);
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                               FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Internal-Token");
        if (!internalToken.isBlank() && supplied != null && MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response); return;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) { unauthorized(response, "JWT requerido"); return; }
        try {
            String[] parts = authorization.substring(7).split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException();
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText())) throw new IllegalArgumentException();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] expected = mac.doFinal((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(parts[2]))) throw new IllegalArgumentException();
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!claims.has("exp") || claims.path("exp").asLong() <= Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            chain.doFilter(request, response);
        } catch (Exception ex) { unauthorized(response, "JWT invalido o expirado"); }
    }

    private void unauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(401); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 401); body.put("data", null); body.put("message", message); body.put("timestamp", Instant.now());
        mapper.writeValue(response.getWriter(), body);
    }
}
