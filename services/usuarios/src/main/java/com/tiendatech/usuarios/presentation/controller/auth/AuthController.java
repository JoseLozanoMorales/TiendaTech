package com.tiendatech.usuarios.presentation.controller.auth;

import com.tiendatech.usuarios.application.dto.auth.LogoutResponse;
import com.tiendatech.usuarios.application.dto.auth.RefreshResponse;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import com.tiendatech.usuarios.domain.port.out.TokenPort;
import com.tiendatech.usuarios.application.service.auth.RefreshTokenService;

@RestController @RequiredArgsConstructor
public class AuthController {

    private final RefreshTokenService svc;
    private final TokenPort tokenPort;

    @Value("${auth.cookie.domain:}")  String cookieDomain;
    @Value("${auth.cookie.secure}")   boolean cookieSecure;
    @Value("${auth.cookie.samesite}") String cookieSameSite;

    // Hallazgo real (punto 14, camino critico autenticado): con
    // auth.cookie.domain=localhost, el navegador y curl reenvian la cookie
    // sin problema, pero el cookiejar estandar de Python (http.cookiejar,
    // usado por requests/Locust) descarta en silencio cualquier atributo
    // Domain sin un punto incrustado (unica excepcion hardcodeada: ".local",
    // no "localhost") -- confirmado reproduciendo el mismo requests.Session
    // que usa Locust. Por eso /auth/refresh fallaba 100% con
    // MissingRequestCookieException bajo carga aunque /api/login si emitia
    // la cookie. Domain vacio/ausente => cookie "host-only", que SI
    // sobrevive esa politica y sigue siendo valida para navegadores. Este
    // metodo ya replica el guard que LoginController.writeRefreshCookie
    // aplicaba solo en /api/login: omitir .domain(...) cuando la propiedad
    // esta en blanco, en vez de pasar cookieDomain="" (que ResponseCookie
    // imprimiria igual como "; Domain=" invalido).
    private void writeRefreshCookie(HttpServletResponse res, String jwt, Instant absExp){
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from("refresh", jwt)
                .httpOnly(true).secure(cookieSecure)
                .sameSite(cookieSameSite).path("/").maxAge(Duration.between(Instant.now(), absExp));
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            cookie.domain(cookieDomain);
        }
        res.addHeader(HttpHeaders.SET_COOKIE, cookie.build().toString());
    }

    // Punto 4: antes devolvia ResponseEntity<?> con Map.of(...), que springdoc
    // no puede tipar (el generador lo declaraba "Contenido dinamico"). El
    // JSON que viaja por la red no cambia -- solo se declara con RefreshResponse
    // en vez de un Map, para que el contrato quede descrito de verdad.
    @PostMapping("/auth/refresh")
    public ResponseEntity<RefreshResponse> refresh(@CookieValue("refresh") String refresh, HttpServletResponse res){
        var r = svc.refresh(refresh);
        writeRefreshCookie(res, r.refreshJwt(), Instant.now().plusSeconds(3600*8)); // abs exp ya está dentro; maxAge lo recalcula
        return ResponseEntity.ok(new RefreshResponse(r.access(), r.meta()));
    }

    @PostMapping("/auth/keepalive")
    public ResponseEntity<RefreshResponse> keepalive(@CookieValue("refresh") String refresh, HttpServletResponse res){
        var r = svc.refresh(refresh); // misma lógica que refresh
        writeRefreshCookie(res, r.refreshJwt(), Instant.now().plusSeconds(3600*8));
        return ResponseEntity.ok(new RefreshResponse(r.access(), r.meta()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<LogoutResponse> logout(@CookieValue(value="refresh", required=false) String refresh,
                                    HttpServletResponse res){
        if (refresh != null){
            svc.logoutFamily(tokenPort.parseRefresh(refresh).familyId());
        }
        // borrar cookie
        ResponseCookie.ResponseCookieBuilder gone = ResponseCookie.from("refresh", "")
                .path("/").maxAge(0).httpOnly(true).secure(cookieSecure).sameSite(cookieSameSite);
        if (cookieDomain != null && !cookieDomain.isBlank()) {
            gone.domain(cookieDomain);
        }
        res.addHeader(HttpHeaders.SET_COOKIE, gone.build().toString());
        return ResponseEntity.ok(new LogoutResponse(true));
    }
}
