package com.tiendatech.inventario.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JdbcInventarioRepositoryTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Validator validator = mock(Validator.class);
    private final IdempotencyGuard guard = mock(IdempotencyGuard.class);
    private final JdbcInventarioRepository repository = new JdbcInventarioRepository(
            jdbc, new ObjectMapper(), guard, validator);

    private void prepare(String type) {
        when(validator.validate(any())).thenReturn(Set.of());
        when(jdbc.queryForObject(anyString(), eq(String.class), eq(1))).thenReturn(type);
        when(jdbc.queryForMap(anyString(), eq(7))).thenReturn(Map.of(
                "stock", 10, "costo", new BigDecimal("10"), "precio_referencia", new BigDecimal("12")));
    }

    private Object[] write(String table) {
        return mockingDetails(jdbc).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("update"))
                .map(call -> call.getArguments())
                .filter(args -> args[0].toString().contains(table))
                .findFirst().orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({"ENTRADA,10,20,20,15.00,true", "ENTRADA,10,null,20,10,false",
                "SALIDA,2,20,8,10,false", "AJUSTE,-2,null,8,10,false"})
    void preservesStockWeightedCostAndKardex(String type, int quantity, String entryCost,
                                            int expectedStock, BigDecimal expectedCost, boolean disabled) {
        prepare(type);
        String body = "{\"producto_id\":7,\"subtipo_id\":1,\"cantidad\":" + quantity
                + ",\"costo_unitario\":" + entryCost + "}";
        assertFalse(repository.registrarMovimiento(body, "admin", null));
        Object[] stock = write("UPDATE inventario.inventario_producto");
        assertEquals(expectedStock, stock[1]);
        assertEquals(0, expectedCost.compareTo((BigDecimal) stock[2]));
        assertEquals(disabled, stock[4]);
        Object[] kardex = write("INSERT INTO inventario.kardex_inventario");
        assertEquals(type, kardex[2]);
        assertEquals(type.equals("ENTRADA") ? quantity : 0, kardex[3]);
        assertEquals(type.equals("SALIDA") ? quantity : 0, kardex[6]);
        assertEquals(expectedStock, kardex[9]);
        assertEquals(0, expectedCost.compareTo((BigDecimal) kardex[10]));
        if (!type.equals("ENTRADA")) assertNull(kardex[4]);
        if (!type.equals("SALIDA")) assertNull(kardex[7]);
    }

    @Test void rejectsInsufficientStockBeforeWriting() {
        prepare("SALIDA");
        assertThrows(IllegalArgumentException.class, () -> repository.registrarMovimiento(
                "{\"producto_id\":7,\"subtipo_id\":1,\"cantidad\":11}", "admin", null));
        assertTrue(mockingDetails(jdbc).getInvocations().stream()
                .noneMatch(call -> call.getMethod().getName().equals("update")));
    }

    @Test void rejectsEmptyOrInvalidInputBeforeDatabaseAccess() {
        for (String json : new String[]{"null", "[]", "42", "{broken"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> repository.registrarMovimiento(json, "admin", null));
        }
        verifyNoInteractions(jdbc, guard);
    }
}
