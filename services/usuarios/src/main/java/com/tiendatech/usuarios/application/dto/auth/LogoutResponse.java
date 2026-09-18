package com.tiendatech.usuarios.application.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Forma real de la respuesta 200 de POST /auth/logout. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LogoutResponse {
    private boolean ok;
}
