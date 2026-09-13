package com.tiendatech.pedidos.presentation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ApiResponseAdviceTest {
    private Object wrap(String path, Object body, MediaType type, int status) {
        var response = new MockHttpServletResponse();
        response.setStatus(status);
        return new ApiResponseAdvice().beforeBodyWrite(body, null, type, null,
                new ServletServerHttpRequest(new MockHttpServletRequest("GET", path)),
                new ServletServerHttpResponse(response));
    }

    @Test void preservesStatusBodyAndMessage() {
        var body = Map.of("message", "invalid", "field", "quantity");
        var result = (ApiResponseAdvice.ApiResponse) wrap("/api/orders", body, MediaType.APPLICATION_JSON, 422);
        assertEquals(422, result.status());
        assertSame(body, result.data());
        assertEquals("invalid", result.message());
        assertNotNull(result.timestamp());
        assertEquals("OK", ((ApiResponseAdvice.ApiResponse) wrap("/api/orders", null, MediaType.APPLICATION_JSON, 200)).message());
        assertEquals("Error", ((ApiResponseAdvice.ApiResponse) wrap("/api/orders", null, MediaType.APPLICATION_JSON, 500)).message());
    }

    @ParameterizedTest @ValueSource(strings = {"/actuator/health", "/health", "/metrics", "/internal/reserve"})
    void leavesInfrastructureResponsesUnwrapped(String path) {
        var body = Map.of("status", "UP");
        assertSame(body, wrap(path, body, MediaType.APPLICATION_JSON, 200));
    }

    @Test void preservesBinaryAndAlreadyWrappedResponses() {
        var bytes = new byte[]{1, 2};
        assertSame(bytes, wrap("/api/image", bytes, MediaType.IMAGE_PNG, 200));
        var envelope = new ApiResponseAdvice.ApiResponse(200, null, "OK", java.time.Instant.now());
        assertSame(envelope, wrap("/api/orders", envelope, MediaType.APPLICATION_JSON, 200));
    }
}
