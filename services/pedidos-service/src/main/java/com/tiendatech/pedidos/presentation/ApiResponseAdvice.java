package com.tiendatech.pedidos.presentation;

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
import java.util.Map;

/** Contrato transversal obligatorio de respuestas JSON del Paso 4. */
@RestControllerAdvice
public class ApiResponseAdvice implements ResponseBodyAdvice<Object> {
    public record ApiResponse(int status, Object data, String message, Instant timestamp) {}

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) { return true; }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                  Class<? extends HttpMessageConverter<?>> converterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        String path = request instanceof ServletServerHttpRequest servlet
                ? servlet.getServletRequest().getRequestURI() : "";
        if (isInfrastructurePath(path) || isRawResponse(body, contentType)) return body;
        int status = response instanceof ServletServerHttpResponse servlet
                ? servlet.getServletResponse().getStatus() : 200;
        String message = responseMessage(body, status);
        return new ApiResponse(status, body, message, Instant.now());
    }
    private boolean isInfrastructurePath(String path) {
        return path.startsWith("/actuator") || path.equals("/health")
                || path.equals("/metrics") || path.startsWith("/internal/");
    }

    private boolean isRawResponse(Object body, MediaType contentType) {
        return body instanceof ApiResponse || body instanceof Resource || body instanceof byte[]
                || MediaType.APPLICATION_OCTET_STREAM.includes(contentType)
                || contentType.getType().equals("image");
    }

    private String responseMessage(Object body, int status) {
        return body instanceof Map<?, ?> map && map.get("message") != null
                ? String.valueOf(map.get("message")) : status < 400 ? "OK" : "Error";
    }

}

