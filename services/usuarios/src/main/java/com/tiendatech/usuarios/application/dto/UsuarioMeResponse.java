package com.tiendatech.usuarios.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Forma real de la respuesta 200 de GET /api/usuarios/me. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioMeResponse {
    private UsuarioPerfilResponse data;
}
