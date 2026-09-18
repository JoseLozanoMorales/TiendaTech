package com.tiendatech.usuarios.application.dto.auth;

import com.tiendatech.usuarios.application.service.auth.RefreshTokenService.KeepalivePayload;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Forma real de la respuesta 200 de POST /auth/refresh y /auth/keepalive. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshResponse {
    private String access;
    private KeepalivePayload meta;
}
