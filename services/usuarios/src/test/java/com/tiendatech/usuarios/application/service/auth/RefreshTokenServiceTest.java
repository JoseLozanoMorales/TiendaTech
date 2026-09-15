package com.tiendatech.usuarios.application.service.auth;

import com.tiendatech.usuarios.domain.model.Usuario;
import com.tiendatech.usuarios.domain.model.auth.RefreshClaims;
import com.tiendatech.usuarios.domain.model.auth.RefreshSession;
import com.tiendatech.usuarios.domain.port.out.RefreshSessionRepository;
import com.tiendatech.usuarios.domain.port.out.TokenPort;
import com.tiendatech.usuarios.domain.port.out.UsuarioRepositoryPort;
import com.tiendatech.usuarios.infrastructure.config.CrdbTransactionRetryExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private final RefreshSessionRepository repo = mock(RefreshSessionRepository.class);
    private final TokenPort jwt = mock(TokenPort.class);
    private final UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
    // Hallazgo real (punto 14, 20 usuarios concurrentes): refresh() ahora
    // envuelve su transacción con CrdbTransactionRetryExecutor para
    // sobrevivir a SQLSTATE 40001 de CockroachDB bajo choque de
    // transacciones concurrentes. Acá se mockea como simple passthrough
    // (ejecuta el supplier una vez, sin reintentar) porque el reintento en
    // sí ya tiene su propia cobertura en CrdbTransactionRetryExecutorTest.
    private final CrdbTransactionRetryExecutor crdbRetry = mock(CrdbTransactionRetryExecutor.class);
    private final RefreshTokenService service = new RefreshTokenService(repo, jwt, usuarios, crdbRetry);

    @BeforeEach void config() {
        when(crdbRetry.execute(any())).thenAnswer(invocation -> {
            Supplier<?> operation = invocation.getArgument(0);
            return operation.get();
        });
        ReflectionTestUtils.setField(service, "accessMinutes", 15);
        ReflectionTestUtils.setField(service, "absoluteHours", 8);
        ReflectionTestUtils.setField(service, "idleAdmin", 30);
        ReflectionTestUtils.setField(service, "idleTrab", 20);
        ReflectionTestUtils.setField(service, "idleCli", 10);
    }

    @Test void issueOnLoginGeneraTokensYGuardaSesion() {
        when(jwt.generateAccess(eq(1), eq("ana"), eq("CLIENTE"), eq(15))).thenReturn("access-jwt");
        when(jwt.generateRefresh(eq(1), eq("CLIENTE"), any(), any(), any())).thenReturn("refresh-jwt");

        var result = service.issueOnLogin(1, "ana", "CLIENTE");

        assertEquals("access-jwt", result.access());
        assertEquals("refresh-jwt", result.refreshJwt());
        verify(repo).save(argThat(s -> s.userId().equals(1) && s.role().equals("CLIENTE") && !s.revoked()));
    }

    @Test void refreshRotaSesionCuandoEstaActiva() {
        UUID jti = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        Instant now = Instant.now();
        RefreshSession activa = new RefreshSession(jti, 1, "CLIENTE", family, now, now, now.plusSeconds(3600), false);
        Usuario ana = new Usuario(1, "Ana", "0999999999", "ana@test.com", "0999999999",
                "ana", "hash", true, null, (short) 2, null);

        when(jwt.parseRefresh("token")).thenReturn(new RefreshClaims(jti, family, 1, "CLIENTE"));
        when(repo.findActive(jti)).thenReturn(Optional.of(activa));
        when(usuarios.findById(1)).thenReturn(Optional.of(ana));
        // Hallazgo real (punto 14): generateAccess NUNCA debe recibir username=null
        // aquí -- un access token sin username es rechazado por JwtUtil.parseAccess
        // ("El token presentado no es un access token") en TODA petición
        // subsiguiente, sin importar que el refresh en sí haya sido exitoso.
        when(jwt.generateAccess(eq(1), eq("ana"), eq("CLIENTE"), eq(15))).thenReturn("new-access");
        when(jwt.generateRefresh(eq(1), eq("CLIENTE"), any(), eq(family), any())).thenReturn("new-refresh");

        var result = service.refresh("token");

        assertEquals("new-access", result.access());
        assertEquals("new-refresh", result.refreshJwt());
        verify(repo).save(argThat(RefreshSession::revoked));
        verify(repo).save(argThat(s -> !s.revoked() && s.familyId().equals(family)));
    }

    @Test void refreshFallaSiElUsuarioYaNoExiste() {
        UUID jti = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        Instant now = Instant.now();
        RefreshSession activa = new RefreshSession(jti, 1, "CLIENTE", family, now, now, now.plusSeconds(3600), false);

        when(jwt.parseRefresh("token")).thenReturn(new RefreshClaims(jti, family, 1, "CLIENTE"));
        when(repo.findActive(jti)).thenReturn(Optional.of(activa));
        when(usuarios.findById(1)).thenReturn(Optional.empty());

        var ex = assertThrows(RuntimeException.class, () -> service.refresh("token"));
        assertEquals("usuario_no_encontrado_para_refresh", ex.getMessage());
    }

    @Test void refreshRechazaSiSesionNoActiva() {
        UUID jti = UUID.randomUUID();
        when(jwt.parseRefresh("token")).thenReturn(new RefreshClaims(jti, UUID.randomUUID(), 1, "CLIENTE"));
        when(repo.findActive(jti)).thenReturn(Optional.empty());

        var ex = assertThrows(RuntimeException.class, () -> service.refresh("token"));
        assertEquals("refresh_revoked", ex.getMessage());
    }

    @Test void refreshRechazaSiExpiroAbsolutamente() {
        UUID jti = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        Instant now = Instant.now();
        RefreshSession expirada = new RefreshSession(jti, 1, "CLIENTE", family,
                now.minusSeconds(7200), now.minusSeconds(7200), now.minusSeconds(1), false);

        when(jwt.parseRefresh("token")).thenReturn(new RefreshClaims(jti, family, 1, "CLIENTE"));
        when(repo.findActive(jti)).thenReturn(Optional.of(expirada));

        var ex = assertThrows(RuntimeException.class, () -> service.refresh("token"));
        assertEquals("session_absolute_expired", ex.getMessage());
    }

    @Test void refreshRechazaSiSuperoElIdleTimeout() {
        UUID jti = UUID.randomUUID();
        UUID family = UUID.randomUUID();
        Instant now = Instant.now();
        RefreshSession inactiva = new RefreshSession(jti, 1, "ADMIN", family,
                now.minusSeconds(3600), now.minusSeconds(3600), now.plusSeconds(7200), false);

        when(jwt.parseRefresh("token")).thenReturn(new RefreshClaims(jti, family, 1, "ADMIN"));
        when(repo.findActive(jti)).thenReturn(Optional.of(inactiva));

        var ex = assertThrows(RuntimeException.class, () -> service.refresh("token"));
        assertEquals("idle_timeout", ex.getMessage());
    }

    @Test void logoutFamilyDelegaAlRepositorio() {
        UUID family = UUID.randomUUID();
        service.logoutFamily(family);
        verify(repo).revokeFamily(family);
    }
}
