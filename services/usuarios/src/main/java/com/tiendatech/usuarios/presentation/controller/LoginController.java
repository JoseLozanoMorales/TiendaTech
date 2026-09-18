// src/main/java/com/example/tienda_tech/controller/LoginController.java
package com.tiendatech.usuarios.presentation.controller;

import com.tiendatech.usuarios.application.dto.LoginRequest;
import com.tiendatech.usuarios.application.dto.LoginResponse;
import com.tiendatech.usuarios.application.dto.LoginUserResponse;
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
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest body, HttpServletResponse response) {
        String usuario = body.getUsuario();
        String contrasenia = body.getContrasena();

        var u = usuarioService.login(usuario, contrasenia);

        int rol = (u.getIdRol() == null) ? 0 : u.getIdRol();
        if (rol == 1 || rol == 3) {

           usuarioAuditoriaService.registrarLogin(u.getUsuarioId());

        }

        // Antes se armaba con Map.of(...), que lanza NullPointerException si
        // cualquier valor es null (cedula y telefono son columnas opcionales,
        // ver docs/evidencias/e2e/cierre-punto13.md). LoginUserResponse es un
        // DTO mutable con setters, tolera null igual que el LinkedHashMap
        // que lo reemplazo, y ademas le da a springdoc una forma real en vez
        // de "Contenido dinamico" (punto 4: el evaluador demostro que
        // renombrar estos campos no hacia fallar la compuerta de OpenAPI).
        LoginUserResponse userPayload = new LoginUserResponse();
        userPayload.setUsuarioId(u.getUsuarioId());
        userPayload.setUsuario(u.getUsuario());
        userPayload.setNombre(u.getNombre());
        userPayload.setCedula(u.getCedula());
        userPayload.setCorreo(u.getCorreo());
        userPayload.setTelefono(u.getTelefono());
        userPayload.setIdRolSnake(u.getIdRol());
        userPayload.setIdRol(u.getIdRol());
        var tokens = refreshTokenService.issueOnLogin(
                u.getUsuarioId(),
                u.getUsuario(),
                roleName(rol)
        );
        writeRefreshCookie(response, tokens.refreshJwt(), tokens.absExp());
        return ResponseEntity.ok(new LoginResponse(true, userPayload, tokens.access(), tokens.access()));
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
