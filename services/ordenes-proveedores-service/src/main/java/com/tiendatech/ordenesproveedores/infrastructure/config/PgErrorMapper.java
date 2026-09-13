package com.tiendatech.ordenesproveedores.infrastructure.config;

import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;

import java.util.Map;

public final class PgErrorMapper {

    // sqlState -> HttpStatus. Antes era un switch de 6 casos + default; se extrae a mapa
    // para que la complejidad ciclomatica no crezca al agregar mas codigos de error.
    private static final Map<String, HttpStatus> SQLSTATE_STATUS = Map.of(
            "P0002", HttpStatus.NOT_FOUND,
            "25000", HttpStatus.CONFLICT,
            "23505", HttpStatus.CONFLICT,
            "23503", HttpStatus.CONFLICT,
            "40001", HttpStatus.CONFLICT,
            "P0001", HttpStatus.BAD_REQUEST);

    private PgErrorMapper() {
    }

    public static HttpStatus statusFor(Throwable ex) {
        PSQLException pg = findPsqlException(ex);
        if (pg == null || pg.getServerErrorMessage() == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        String sqlState = pg.getServerErrorMessage().getSQLState();
        if (sqlState == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return SQLSTATE_STATUS.getOrDefault(sqlState, HttpStatus.BAD_REQUEST);
    }

    public static String messageFor(Throwable ex) {
        PSQLException pg = findPsqlException(ex);
        if (pg != null && pg.getServerErrorMessage() != null) {
            String sqlState = pg.getServerErrorMessage().getSQLState();
            if ("23505".equals(sqlState)) {
                return friendlyDuplicateMessage(pg.getServerErrorMessage().getConstraint());
            }
            return pg.getServerErrorMessage().getMessage();
        }
        return ex.getMessage();
    }

    // 23505 = unique_violation. El mensaje crudo de Postgres/CockroachDB expone el nombre
    // de la constraint y detalles internos de la tabla -- no es apto para mostrarlo tal cual
    // en la UI. Lo traducimos segun la constraint que fallo.
    private static String friendlyDuplicateMessage(String constraint) {
        if (constraint == null) {
            return "Ya existe un registro con esos datos.";
        }
        return switch (constraint) {
            case "uq_proveedor_ruc" -> "Ya existe un proveedor registrado con ese RUC.";
            case "uq_proveedor_nombre" -> "Ya existe un proveedor registrado con ese nombre.";
            default -> "Ya existe un registro con esos datos.";
        };
    }

    private static PSQLException findPsqlException(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof PSQLException psql) {
                return psql;
            }
            current = current.getCause();
        }
        return null;
    }
}
