package com.tiendatech.usuarios.application.dto;

import lombok.Data;

/**
 * Forma real del cuerpo de POST /api/login (punto 4: antes era
 * Map<String, String>, que springdoc no puede tipar y el generador de
 * OpenAPI declaraba como objeto abierto sin propiedades).
 */
@Data
public class LoginRequest {
    private String usuario;
    private String contrasena;
}
