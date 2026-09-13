package com.tiendatech.pedidos.infrastructure.persistence;

import com.tiendatech.pedidos.domain.DireccionInfo;
import com.tiendatech.pedidos.domain.ProductoPort;
import com.tiendatech.pedidos.domain.UsuarioPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcOrdenRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final UsuarioPort users = mock(UsuarioPort.class);
    private final JdbcOrdenRepository repository = new JdbcOrdenRepository(
            jdbc, mock(ProductoPort.class), users, Optional.empty(), mock(ObjectProvider.class));

    @Test void rejectsDisabledAddressBeforePaymentQuery() {
        when(users.obtenerDirecciones(1)).thenReturn(List.of(
                new DireccionInfo(2, 1, "street", "", 1, "city", "province", false)));
        assertThrows(IllegalArgumentException.class, () -> repository.crear(1, 2, 3, null, null));
        verify(users).obtenerUsuario(1);
        verifyNoInteractions(jdbc);
    }

    @Test void rejectsInvalidPaymentBeforeCartAccess() {
        when(users.obtenerDirecciones(1)).thenReturn(List.of(
                new DireccionInfo(2, 1, "street", "", 1, "city", "province", true)));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(3), eq(1))).thenReturn(0);
        assertThrows(IllegalArgumentException.class, () -> repository.crear(1, 2, 3, null, null));
        verify(jdbc).queryForObject(contains("pedidos.metodopago"), eq(Integer.class), eq(3), eq(1));
        verifyNoMoreInteractions(jdbc);
    }

    @Test void translatesOnlyUserNotFoundAndPropagatesOtherFailures() {
        var missing = HttpClientErrorException.create(HttpStatus.NOT_FOUND, "missing", null, null, null);
        when(users.obtenerUsuario(1)).thenThrow(missing);
        var translated = assertThrows(IllegalArgumentException.class,
                () -> repository.crear(1, 2, 3, null, null));
        assertSame(missing, translated.getCause());
        var outage = new IllegalStateException("service unavailable");
        when(users.obtenerUsuario(7)).thenThrow(outage);
        assertSame(outage, assertThrows(IllegalStateException.class,
                () -> repository.crear(7, 2, 3, null, null)));
        verifyNoInteractions(jdbc);
    }
}
