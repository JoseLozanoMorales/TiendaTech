package com.tiendatech.ordenesproveedores.presentation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Contrato transversal obligatorio de respuestas JSON del Paso 4. */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    public record ApiResponse(int status, Object data, String message, Instant timestamp) {}

    // Rutas exactas que quedan fuera del envoltorio ApiResponse (ademas de los prefijos abajo).
    private static final Set<String> EXEMPT_EXACT_PATHS = Set.of("/health", "/metrics");
    private static final List<String> EXEMPT_PATH_PREFIXES = List.of("/actuator", "/internal/");

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = pathOf(request);
        if (isExemptPath(path) || isExemptBody(body) || isExemptContentType(contentType)) {
            return body;
        }
        int status = statusOf(response);
        return new ApiResponse(status, body, messageFor(body, status), Instant.now());
    }

    private String pathOf(ServerHttpRequest request) {
        return request instanceof ServletServerHttpRequest servlet
                ? servlet.getServletRequest().getRequestURI() : "";
    }

    private boolean isExemptPath(String path) {
        return EXEMPT_EXACT_PATHS.contains(path) || EXEMPT_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isExemptBody(Object body) {
        return body instanceof ApiResponse || body instanceof Resource || body instanceof byte[];
    }

    private boolean isExemptContentType(MediaType contentType) {
        return MediaType.APPLICATION_OCTET_STREAM.includes(contentType) || contentType.getType().equals("image");
    }

    private int statusOf(ServerHttpResponse response) {
        return response instanceof ServletServerHttpResponse servlet
                ? servlet.getServletResponse().getStatus() : 200;
    }

    private String messageFor(Object body, int status) {
        if (body instanceof Map<?, ?> map && map.get("message") != null) {
            return String.valueOf(map.get("message"));
        }
        return status < 400 ? "OK" : "Error";
    }
}
