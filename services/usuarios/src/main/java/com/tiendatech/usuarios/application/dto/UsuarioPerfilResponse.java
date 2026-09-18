package com.tiendatech.usuarios.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Forma real del perfil de usuario que devuelve GET /api/usuarios/me
 * (ver UsuarioController#usuarioPayload). avatar_path/avatarPath se
 * repiten por la misma compatibilidad que ya tenia el Map original.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioPerfilResponse {
    private Integer usuarioId;
    private String usuario;
    private String nombre;
    private String cedula;
    private String correo;
    private String telefono;
    @JsonProperty("id_rol")
    private Short idRol;
    @JsonProperty("avatar_path")
    private String avatarPathSnake;
    private String avatarPath;
}
