package com.tiendatech.frontend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtGatewayFilterTest {

    private static final String SECRET = "TiendaTechDistribuidaJwtSecretKey2026Seguro";
    private static final String OBSERVABILITY_TOKEN = "token-observabilidad-de-prueba";

    private JwtGatewayFilter filter() {
        return new JwtGatewayFilter(new MockEnvironment()
                .withProperty("tiendatech.security.jwt.secret", SECRET)
                .withProperty("tiendatech.security.observability.token", OBSERVABILITY_TOKEN));
    }

    @Test
    void permiteLecturaPublicaDelCatalogoSinToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/productos/10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void protegeEscrituraAunqueCompartaRutaConCatalogoPublico() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/productos/10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> invoked.set(true));

        assertFalse(invoked.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    void protegeFacturasSinToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/facturas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();

        filter().doFilter(request, response, (req, res) -> invoked.set(true));

        assertFalse(invoked.get());
        assertEquals(401, response.getStatus());
    }

    @Test
    void validaTokenYSobrescribeCabecerasDeIdentidad() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
        request.addHeader("Authorization", "Bearer " + token());
        request.addHeader("X-User-Id", "usuario-falsificado");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean();
        FilterChain chain = (req, res) -> {
            invoked.set(true);
            assertEquals("42", ((jakarta.servlet.http.HttpServletRequest) req).getHeader("X-User-Id"));
            assertEquals("jose", ((jakarta.servlet.http.HttpServletRequest) req).getHeader("X-Usuario"));
            assertEquals("ADMIN", ((jakarta.servlet.http.HttpServletRequest) req).getHeader("X-User-Role"));
        };

        filter().doFilter(request, response, chain);

        assertTrue(invoked.get());
        assertEquals(200, response.getStatus());
    }

    @Test
    void rechazaTokenCaducado() throws Exception {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder().setSubject("42")
                .setExpiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key, SignatureAlgorithm.HS256).compact();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
        request.addHeader("Authorization", "Bearer " + expired);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("No debe reenviar un token caducado");
        });
        assertEquals(401, response.getStatus());
    }

    @Test
    void rechazaTokenMalformado() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
        request.addHeader("Authorization", "Bearer invalid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("No debe reenviar un token invalido");
        });
        assertEquals(401, response.getStatus());
    }

    // Punto 17 (guia de cierre del docente): /metrics y /health quedaban
    // expuestos sin ninguna autenticacion porque no coinciden ni con
    // publicPaths ni con protectedPaths, asi que doFilterInternal los
    // dejaba pasar de largo. Ahora exigen un token de observabilidad propio,
    // distinto del JWT de usuario.
    @Test
    void rechazaMetricsSinToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/metrics");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("No debe exponer /metrics sin token de observabilidad");
        });
        assertEquals(401, response.getStatus());
    }

    @Test
    void rechazaHealthConTokenIncorrecto() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/health");
        request.addHeader("Authorization", "Bearer token-incorrecto");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("No debe aceptar un token de observabilidad incorrecto");
        });
        assertEquals(401, response.getStatus());
    }

    @Test
    void permiteMetricsYHealthConTokenDeObservabilidadCorrecto() throws Exception {
        for (String path : new String[]{"/metrics", "/health"}) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.addHeader("Authorization", "Bearer " + OBSERVABILITY_TOKEN);
            MockHttpServletResponse response = new MockHttpServletResponse();
            AtomicBoolean invoked = new AtomicBoolean();

            filter().doFilter(request, response, (req, res) -> invoked.set(true));

            assertTrue(invoked.get(), "Debe reenviar " + path + " con el token de observabilidad correcto");
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    void tokenDeObservabilidadNoSirveComoJwtDeUsuario() throws Exception {
        // El token de observabilidad es un secreto plano, no un JWT firmado:
        // no debe colar como credencial de usuario en rutas /api/**.
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/ordenes");
        request.addHeader("Authorization", "Bearer " + OBSERVABILITY_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter().doFilter(request, response, (req, res) -> {
            throw new AssertionError("El token de observabilidad no debe autenticar rutas de usuario");
        });
        assertEquals(401, response.getStatus());
    }

    private String token() {
        Key key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .setSubject("42")
                .claim("username", "jose")
                .claim("role", "ADMIN")
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }
}
