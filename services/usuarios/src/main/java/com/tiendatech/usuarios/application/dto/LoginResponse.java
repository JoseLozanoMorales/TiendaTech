package com.tiendatech.usuarios.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Forma real de la respuesta 200 de POST /api/login. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private boolean success;
    private LoginUserResponse user;
    private String token;
    private String access;
}
