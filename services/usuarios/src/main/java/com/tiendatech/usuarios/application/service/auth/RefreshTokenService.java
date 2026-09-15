package com.tiendatech.usuarios.application.service.auth;

import com.tiendatech.usuarios.domain.model.Usuario;
import com.tiendatech.usuarios.domain.model.auth.RefreshSession;
import com.tiendatech.usuarios.domain.port.out.RefreshSessionRepository;
import com.tiendatech.usuarios.domain.port.out.TokenPort;
import com.tiendatech.usuarios.domain.port.out.UsuarioRepositoryPort;
import com.tiendatech.usuarios.infrastructure.config.CrdbTransactionRetryExecutor;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshSessionRepository repo;
    private final TokenPort jwt;
    private final UsuarioRepositoryPort usuarios;
    private final CrdbTransactionRetryExecutor crdbRetry;

    @Value("${auth.access.minutes}")   private int accessMinutes;
    @Value("${auth.absolute.hours}")   private int absoluteHours;
    @Value("${auth.idle.ADMIN}")      private int idleAdmin;
    @Value("${auth.idle.TRABAJADOR}") private int idleTrab;
    @Value("${auth.idle.CLIENTE}")    private int idleCli;

    private int idleForRole(String role) {
        return switch (role) {
            case "ADMIN" -> idleAdmin;
            case "TRABAJADOR" -> idleTrab;
            default -> idleCli;
        };
    }

    public record Tokens(String access){}

    public record IssueResult(String access, String refreshJwt, UUID jti, UUID family, Instant absExp){}

    public IssueResult issueOnLogin(Integer userId, String username, String role){
        var now   = Instant.now();
        var abs   = now.plusSeconds(absoluteHours*3600L);
        var jti   = UUID.randomUUID();
        var fam   = UUID.randomUUID();

        var access = jwt.generateAccess(userId, username, role, accessMinutes);
        var refreshJwt = jwt.generateRefresh(userId, role, jti, fam, abs);

        repo.save(new RefreshSession(jti, userId, role, fam, now, now, abs, false));

        return new IssueResult(access, refreshJwt, jti, fam, abs);
    }

    public record KeepalivePayload(long remainingIdleSeconds, long remainingAbsoluteSeconds){}

    // Hallazgo real (carga del punto 14, 20 usuarios concurrentes): esta
    // rotación hace una lectura + dos escrituras (revocar la sesión vieja,
    // crear la nueva) contra refresh_sessions. Bajo aislamiento serializable,
    // CockroachDB aborta una de dos transacciones concurrentes que chocan
    // sobre las mismas filas y exige que el cliente la reintente
    // (TransactionRetryWithProtoRefreshError, SQLSTATE 40001) -- no es un bug
    // de la app, es el contrato normal de una base distribuida con control de
    // concurrencia optimista. Con 5 y 10 usuarios nunca se vio; con 20
    // apareció en 4 de 2209 peticiones (0.18%), todas acá. pedidos-service e
    // inventario-service ya tenían este mismo tratamiento (CrdbRetryExecutor /
    // CrdbTransactionRetryExecutor) para sus propias transacciones
    // multi-escritura; usuarios-service era el único que le faltaba. Por eso
    // ya NO se anota @Transactional este método: la transacción completa
    // (rotateSession) queda a cargo de CrdbTransactionRetryExecutor, que la
    // reintenta entera ante un 40001 en vez de solo un statement suelto.
    public KeepaliveResponse refresh(String refreshJwt){
        var claims = jwt.parseRefresh(refreshJwt);
        var jti = claims.jti();
        var role = claims.role();
        var userId = claims.userId();
        var family = claims.familyId();

        RotationResult rotation = crdbRetry.execute(() -> rotateSession(jti, role, userId, family));

        var access = jwt.generateAccess(userId, rotation.username(), role, accessMinutes);
        var newRefreshJwt = jwt.generateRefresh(userId, role, rotation.newJti(), family, rotation.absoluteExpiration());

        return new KeepaliveResponse(access, newRefreshJwt,
                new KeepalivePayload(rotation.remainingIdle(), rotation.remainingAbs()));
    }

    private record RotationResult(String username, UUID newJti, Instant absoluteExpiration,
                                   long remainingIdle, long remainingAbs) {}

    // Sin @Transactional: la transacción la abre y cierra
    // CrdbTransactionRetryExecutor (vía TransactionTemplate) alrededor de
    // cada intento -- una anotación acá sería ignorada de todos modos por
    // auto-invocación (este método se llama desde dentro de la misma clase,
    // fuera del proxy de Spring AOP) y, si se aplicara, duplicaría el manejo
    // de la transacción.
    RotationResult rotateSession(UUID jti, String role, Integer userId, UUID family){
        var rt = repo.findActive(jti)
                .orElseThrow(() -> new RuntimeException("refresh_revoked"));

        var now = Instant.now();
        if (now.isAfter(rt.absoluteExpiration())) throw new RuntimeException("session_absolute_expired");

        int idleMin = idleForRole(role);     // <<< AQUÍ
        long remainingIdle = -1;
        if (idleMin > 0){
            long secs = idleMin*60L - (now.getEpochSecond() - rt.lastSeen().getEpochSecond());
            if (secs <= 0) throw new RuntimeException("idle_timeout");
            remainingIdle = secs;
        }

        long remainingAbs = Math.max(0, rt.absoluteExpiration().getEpochSecond() - now.getEpochSecond());

        // Rotación: revoco el viejo y creo uno nuevo en la misma familia
        repo.save(rt.revoke());

        var newJti = UUID.randomUUID();
        repo.save(new RefreshSession(newJti, userId, role, family, now, now,
                rt.absoluteExpiration(), false));

        // Hallazgo real (carga del punto 14): esto pasaba username=null. El
        // refresh JWT (generateRefresh) nunca lleva el claim "username" -- solo
        // subject/role/family_id/jti --, así que no había forma de conservarlo
        // sin ir a buscarlo. JwtUtil.parseAccess exige username no-nulo para
        // aceptar un token como "access token" (username==null también matchea
        // un refresh token, que por diseño no lo tiene, y lanza "El token
        // presentado no es un access token"). Con username=null, TODO access
        // token emitido por /auth/refresh o /auth/keepalive quedaba
        // estructuralmente inválido: no expiraba antes de tiempo, nacía roto,
        // y la siguiente petición autenticada fallaba con 401 "JWT invalido o
        // expirado" en cualquier servicio, sin importar qué tan reciente fuera
        // el refresh. Confirmado end-to-end con el load test del camino
        // crítico: en cuanto empezaban a dispararse renovaciones forzadas
        // (REFRESH_EVERY_N_ITERATIONS), el checkout empezaba a fallar en
        // cascada para esos usuarios.
        String username = usuarios.findById(userId)
                .map(Usuario::getUsuario)
                .orElseThrow(() -> new RuntimeException("usuario_no_encontrado_para_refresh"));

        return new RotationResult(username, newJti, rt.absoluteExpiration(), remainingIdle, remainingAbs);
    }

    public record KeepaliveResponse(String access, String refreshJwt, KeepalivePayload meta){}

    @Transactional
    public void logoutFamily(UUID family){ repo.revokeFamily(family); }
}
