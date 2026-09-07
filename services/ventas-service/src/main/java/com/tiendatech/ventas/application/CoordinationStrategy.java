package com.tiendatech.ventas.application;

/** Estrategias conmutables del flujo factura-inventario. */
public enum CoordinationStrategy {
    TWO_PHASE("2pc"),
    SAGA("saga");

    private final String wireValue;

    CoordinationStrategy(String wireValue) {
        this.wireValue = wireValue;
    }

    public String wireValue() {
        return wireValue;
    }

    public static CoordinationStrategy fromWire(String value) {
        if (value != null) {
            for (CoordinationStrategy strategy : values()) {
                if (strategy.wireValue.equalsIgnoreCase(value.trim())) {
                    return strategy;
                }
            }
        }
        throw new IllegalArgumentException("X-Coordination-Strategy debe ser 2pc o saga");
    }
}
