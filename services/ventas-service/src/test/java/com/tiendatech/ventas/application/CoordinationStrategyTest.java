package com.tiendatech.ventas.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoordinationStrategyTest {
    @Test void aceptaValoresDelContrato() {
        assertEquals(CoordinationStrategy.TWO_PHASE, CoordinationStrategy.fromWire("2PC"));
        assertEquals(CoordinationStrategy.SAGA, CoordinationStrategy.fromWire(" saga "));
    }

    @Test void rechazaEtiquetasDesconocidas() {
        assertThrows(IllegalArgumentException.class, () -> CoordinationStrategy.fromWire("otra"));
    }
}
