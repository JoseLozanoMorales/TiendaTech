package com.tiendatech.inventario.application.reservation;

import com.tiendatech.inventario.domain.reservation.ReservationCommand;
import com.tiendatech.inventario.domain.reservation.ReservationResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class StockReservationService {
    private final JdbcTemplate jdbc;
    private final LamportClock clock;
    private final CrdbTransactionRetryExecutor retryExecutor;

    @Autowired
    public StockReservationService(JdbcTemplate jdbc, LamportClock clock,
                                   CrdbTransactionRetryExecutor retryExecutor) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.retryExecutor = retryExecutor;
    }

    StockReservationService(JdbcTemplate jdbc, LamportClock clock) {
        this(jdbc, clock, null);
    }

    public ReservationResult reconcile(ReservationCommand command) {
        validate(command);
        // Prechequeo fuera de la transaccion: una repeticion ya resuelta no debe
        // competir por el lock del producto. reconcileOnce vuelve a comprobarlo
        // dentro de la transaccion para cerrar la carrera entre ambas consultas.
        Optional<ReservationResult> replay = findReplay(command);
        if (replay.isPresent()) return replay.get();
        return retryExecutor == null
                ? reconcileOnce(command)
                : retryExecutor.execute(() -> reconcileOnce(command));
    }

    private ReservationResult reconcileOnce(ReservationCommand command) {
        // Debe ser la PRIMERA sentencia de la transaccion. Si otro request ya
        // posee el lock, CockroachDB espera/reintenta esta sentencia sin dejar
        // que el resto de la transaccion trabaje con un snapshot envejecido.
        Map<String, Object> product = jdbc.queryForMap(
                "SELECT stock FROM inventario.inventario_producto WHERE producto_id = ? AND habilitado FOR UPDATE",
                command.productId());
        Optional<ReservationResult> replay = findReplay(command);
        if (replay.isPresent()) return replay.get();

        UUID operationId = UUID.fromString(command.operationId());
        int physicalStock = ((Number) product.get("stock")).intValue();
        List<State> states = jdbc.query("""
                SELECT cantidad, lamport, dispositivo_id FROM inventario.reserva_stock
                 WHERE carrito_id = ? AND producto_id = ?
                """, (rs, row) -> new State(rs.getInt(1), rs.getLong(2), rs.getString(3)),
                command.cartId(), command.productId());
        State current = states.isEmpty() ? new State(0, -1, "") : states.getFirst();
        Long reservedByOthersValue = jdbc.queryForObject("""
                SELECT COALESCE(sum(cantidad), 0) FROM inventario.reserva_stock
                 WHERE producto_id = ? AND carrito_id <> ?
                """, Long.class, command.productId(), command.cartId());
        int reservedByOthers = reservedByOthersValue == null ? 0 : reservedByOthersValue.intValue();
        int availableForCart = physicalStock - reservedByOthers;
        long serverTimestamp = clock.receive(command.lamportTimestamp());

        boolean newer = command.lamportTimestamp() > current.lamport
                || command.lamportTimestamp() == current.lamport
                && command.deviceId().compareTo(current.deviceId) > 0;
        ReservationResult result;
        if (!newer) {
            result = new ReservationResult(false, "Evento anterior al estado reconciliado",
                    current.quantity, Math.max(0, availableForCart - current.quantity), serverTimestamp,
                    current.deviceId, false);
        } else if (command.quantity() > availableForCart) {
            result = new ReservationResult(false, "Stock insuficiente",
                    current.quantity, Math.max(0, availableForCart - current.quantity), serverTimestamp,
                    current.deviceId, false);
        } else {
            jdbc.update("""
                    UPSERT INTO inventario.reserva_stock
                      (carrito_id, producto_id, usuario_id, cantidad, lamport, dispositivo_id, actualizado_en)
                    VALUES (?, ?, ?, ?, ?, ?, now())
                    """, command.cartId(), command.productId(), command.userId(), command.quantity(),
                    command.lamportTimestamp(), command.deviceId());
            result = new ReservationResult(true, "Reserva reconciliada", command.quantity(),
                    Math.max(0, availableForCart - command.quantity()), serverTimestamp,
                    command.deviceId(), false);
        }
        jdbc.update("""
                INSERT INTO inventario.operacion_reserva
                  (operacion_id, carrito_id, producto_id, aceptada, cantidad_reservada,
                   stock_disponible, lamport, dispositivo_ganador, mensaje)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, operationId, command.cartId(), command.productId(), result.accepted(),
                result.reservedQuantity(), result.availableStock(), result.lamportTimestamp(),
                result.winningDeviceId(), result.message());
        return result;
    }

    private Optional<ReservationResult> findReplay(ReservationCommand command) {
        UUID operationId = UUID.fromString(command.operationId());
        List<ReservationResult> replay = jdbc.query("""
                SELECT aceptada, mensaje, cantidad_reservada, stock_disponible, lamport, dispositivo_ganador
                  FROM inventario.operacion_reserva WHERE operacion_id = ?
                """, (rs, row) -> new ReservationResult(rs.getBoolean(1), rs.getString(2),
                rs.getInt(3), rs.getInt(4), rs.getLong(5), rs.getString(6), true), operationId);
        return replay.stream().findFirst();
    }

    private static void validate(ReservationCommand command) {
        if (command.cartId() <= 0 || command.userId() <= 0 || command.productId() <= 0)
            throw new IllegalArgumentException("carrito, usuario y producto deben ser positivos");
        if (command.quantity() < 0) throw new IllegalArgumentException("cantidad no puede ser negativa");
        if (command.lamportTimestamp() < 0) throw new IllegalArgumentException("lamport no puede ser negativo");
        if (command.deviceId() == null || command.deviceId().isBlank())
            throw new IllegalArgumentException("deviceId es obligatorio");
        UUID.fromString(command.operationId());
    }

    private record State(int quantity, long lamport, String deviceId) {}
}
