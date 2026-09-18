// src/main/java/com/example/tienda_tech/controller/LoginController.java
package com.tiendatech.usuarios.presentation.controller;

import com.tiendatech.usuarios.domain.model.Usuario;
import com.tiendatech.usuarios.application.service.UsuarioService;
import com.tiendatech.usuarios.application.service.auth.RefreshTokenService;
import com.tiendatech.usuarios.application.service.audit.UsuarioAuditoriaService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoginController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioAuditoriaService usuarioAuditoriaService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Value("${auth.cookie.domain:}") private String cookieDomain;
    @Value("${auth.cookie.secure:false}") private boolean cookieSecure;
    @Value("${auth.cookie.samesite:Lax}") private String cookieSameSite;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletResponse response) {
        String usuario = body.get("usuario");
        String contrasenia = body.get("contrasena");

        var u = usuarioService.login(usuario, contrasenia);

        int rol = (u.getIdRol() == null) ? 0 : u.getIdRol();
        if (rol == 1 || rol == 3) {

           usuarioAuditoriaService.registrarLogin(u.getUsuarioId());

        }

        // Map.of(...) lanza NullPointerException si cualquier valor es null.
        // cedula y telefono son columnas opcionales (pueden ser null en un
        // usuario real), asi que se usa un mapa mutable que si tolera null
        // en vez de rechazar el login con un 500 (ver docs/evidencias/e2e/cierre-punto13.md).
        Map<String, Object> userPayload = new LinkedHashMap<>();
        userPayload.put("usuarioId", u.getUsuarioId());
        userPayload.put("usuario",   u.getUsuario());
        userPayload.put("nombre",    u.getNombre());
        userPayload.put("cedula",    u.getCedula());
        userPayload.put("correo",    u.getCorreo());
        userPayload.put("telefono",  u.getTelefono());
        userPayload.put("id_rol",    u.getIdRol());
        userPayload.put("idRol",     u.getIdRol());
        var tokens = refreshTokenService.issueOnLogin(
                u.getUsuarioId(),
                u.getUsuario(),
                roleName(rol)
        );
        writeRefreshCookie(response, tokens.refreshJwt(), tokens.absExp());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "user", userPayload,
                "token", tokens.access(),
                "access", tokens.access()
        ));
    }

    private void writeRefreshCookie(HttpServletResponse response, String jwt, Instant absoluteExpiration) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from("refresh", jwt)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(Duration.between(Instant.now(), absoluteExpiration));
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.domain(cookieDomain);
        }
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    private String roleName(int rol) {
        return switch (rol) {
            case 1 -> "ADMIN";
            case 2 -> "CLIENTE";
            case 3 -> "TRABAJADOR";
            default -> "UNKNOWN";
        };
    }

}
