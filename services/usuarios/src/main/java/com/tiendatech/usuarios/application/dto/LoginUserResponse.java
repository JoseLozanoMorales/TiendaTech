package com.tiendatech.usuarios.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Forma real del campo "user" en la respuesta de POST /api/login (ver
 * LoginController#login). idRol se repite bajo dos claves por
 * compatibilidad con clientes existentes -- "id_rol" e "idRol" -- tal
 * como ya lo hacia el Map original; este DTO solo declara esa misma
 * forma para que springdoc deje de reportarla como "Contenido dinamico".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserResponse {
    private Integer usuarioId;
    private String usuario;
    private String nombre;
    private String cedula;
    private String correo;
    private String telefono;
    @JsonProperty("id_rol")
    private Short idRolSnake;
    private Short idRol;
}
