package com.tiendatech.pedidos.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiendatech.pedidos.domain.ReservationCommand;
import com.tiendatech.pedidos.domain.ReservationPort;
import com.tiendatech.pedidos.domain.ReservationResult;
import com.tiendatech.pedidos.infrastructure.observability.TraceContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

/**
 * El canal de reservas de stock es un socket TCP crudo, fuera del alcance de
 * la auto-instrumentacion HTTP de Micrometer/OTel (que solo envuelve
 * RestClient/RestTemplate). Este cliente abre un span manual
 * "reservation.tcp.reconcile" (CLIENT) y propaga el contexto de traza dentro
 * del propio mensaje -- campo "traceparent" del sobre JSON -- porque no hay
 * headers HTTP donde llevarlo. Ver TcpReservationServer en
 * inventario-service para el lado que extrae ese contexto y abre el span
 * SERVER correspondiente, bajo el mismo trace ID.
 */
@Component
public class LengthPrefixedTcpReservationClient implements ReservationPort {
    private static final int MAX_MESSAGE_BYTES = 64 * 1024;
    private final String host;
    private final int port;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final ObjectMapper mapper;
    private final Tracer tracer;
    private final Propagator propagator;
    public LengthPrefixedTcpReservationClient(@Value("${reservation.tcp.host:localhost}") String host,
            @Value("${reservation.tcp.port:9091}") int port,
            @Value("${reservation.tcp.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${reservation.tcp.read-timeout-ms:5000}") int readTimeoutMs,
            ObjectMapper mapper, Tracer tracer, Propagator propagator) {
        this.host = host; this.port = port; this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs; this.mapper = mapper;
        this.tracer = tracer; this.propagator = propagator;
    }

    @Override
    public ReservationResult reconcile(ReservationCommand command) {
        Span span = tracer.spanBuilder().name("reservation.tcp.reconcile").kind(Span.Kind.CLIENT).start();
        span.tag("net.transport", "tcp");
        span.tag("net.peer.name", host);
        span.tag("net.peer.port", String.valueOf(port));
        span.tag("reservation.cartId", String.valueOf(command.cartId()));
        span.tag("reservation.productId", String.valueOf(command.productId()));
        try (Tracer.SpanInScope ignored = tracer.withSpan(span)) {
            try {
                return exchange(command, span);
            } catch (IOException firstFailure) {
                try {
                    return exchange(command, span);
                } catch (IOException retryFailure) {
                    span.error(retryFailure);
                    throw new IllegalStateException("Canal TCP de reservas no disponible", retryFailure);
                }
            }
        } finally {
            span.end();
        }
    }

    private ReservationResult exchange(ReservationCommand command, Span span) throws IOException {
        Map<String, String> carrier = new HashMap<>();
        propagator.inject(span.context(), carrier, Map::put);
        byte[] payload = mapper.writeValueAsBytes(
                new WireEnvelope(command, carrier.get("traceparent"), TraceContext.traceId()));
        // Cada llamada usa su propia conexion. El servidor atiende conexiones
        // con virtual threads, así que no existe motivo para serializar todos
        // los carritos detrás de un único socket/monitor del cliente.
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            socket.setSoTimeout(readTimeoutMs);
            socket.setKeepAlive(true);
            try (DataInputStream input = new DataInputStream(socket.getInputStream());
                 DataOutputStream output = new DataOutputStream(socket.getOutputStream())) {
                output.writeInt(payload.length);
                output.write(payload);
                output.flush();
                int length = input.readInt();
                if (length <= 0 || length > MAX_MESSAGE_BYTES)
                    throw new IOException("Respuesta TCP inválida: " + length);
                byte[] response = new byte[length];
                input.readFully(response);
                return mapper.readValue(response, ReservationResult.class);
            }
        }
    }

    /**
     * Sobre de transporte: agrega el traceparent (contexto OTel del span
     * reservation.tcp.reconcile) y el businessTraceId (X-Trace-Id de negocio,
     * leido de TraceContext -- el mismo publicado por CarritoController para
     * este request) sin tocar el record de dominio ReservationCommand. El
     * traceparent ya viajaba; businessTraceId es lo que faltaba para que los
     * logs de inventario-service puedan correlacionarse con los de pedidos
     * por el mismo identificador manual, no solo por el trace ID de OTel.
     */
    private record WireEnvelope(ReservationCommand command, String traceparent, String businessTraceId) {}
}
