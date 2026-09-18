package com.tiendatech.usuarios.presentation.controller;

import com.tiendatech.usuarios.application.dto.ClienteUpdateRequest;
import com.tiendatech.usuarios.application.dto.UsuarioDTO;
import com.tiendatech.usuarios.application.dto.UsuarioMeResponse;
import com.tiendatech.usuarios.application.dto.UsuarioMinDTO;
import com.tiendatech.usuarios.application.dto.UsuarioPerfilResponse;
import com.tiendatech.usuarios.domain.model.Usuario;
import com.tiendatech.usuarios.application.service.UsuarioService;
import com.tiendatech.usuarios.presentation.support.UserAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;
    private final UserAccessGuard accessGuard;

    public UsuarioController(UsuarioService usuarioService, UserAccessGuard accessGuard) {
        this.usuarioService = usuarioService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/crear")
    public ResponseEntity<?> crearCliente(@RequestBody UsuarioDTO dto) {
        Usuario creado = usuarioService.crearClienteConSP(dto);
        return createdUsuario(creado, "Cliente creado");
    }

    @PostMapping("/crear-usuario")
    public ResponseEntity<?> crearUsuario(@RequestBody UsuarioDTO dto) {
        if (dto.getIdRol() == null) {
            throw new IllegalArgumentException("idRol es obligatorio para crear usuario");
        }
        Usuario creado = usuarioService.crearUsuarioConSP(dto);
        return createdUsuario(creado, "Usuario creado");
    }

    @PutMapping("/cliente/{id}")
    public ResponseEntity<?> actualizarCliente(@PathVariable Integer id, @RequestBody ClienteUpdateRequest dto) {
        accessGuard.requireOwnerOrAdmin(id);
        usuarioService.actualizarCliente(id, dto);
        return ResponseEntity.ok(Map.of("success", true, "message", "Cliente actualizado"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable Integer id) {
        accessGuard.requireOwnerOrAdmin(id);
        Usuario usuario = usuarioService.getById(id);
        return ResponseEntity.ok(usuarioPayload(usuario));
    }

    // Punto 4: antes devolvia ResponseEntity<?> con Map.of("data", ...), que
    // springdoc no puede tipar. El JSON no cambia -- se declara con
    // UsuarioMeResponse/UsuarioPerfilResponse en vez de un Map, siguiendo
    // exactamente la misma forma que ya arma usuarioPayload(usuario).
    @GetMapping("/me")
    public ResponseEntity<UsuarioMeResponse> me() {
        Integer userId = accessGuard.currentUserId();
        Usuario usuario = usuarioService.getById(userId);
        return ResponseEntity.ok(new UsuarioMeResponse(usuarioPerfilResponse(usuario)));
    }

    @PostMapping("/crear-usuarioAdmin")
    public ResponseEntity<?> crearAdminOTrabajador(@Valid @RequestBody UsuarioDTO dto) {
        Usuario creado = usuarioService.crearAdminOTrabajador(dto);
        return createdUsuario(creado, "Usuario administrativo creado");
    }

    @PutMapping("/admin/{id}")
    public ResponseEntity<Void> actualizarAdmin(
            @PathVariable Integer id,
            @RequestParam Integer rolId,
            @Valid @RequestBody ClienteUpdateRequest req) {
        usuarioService.actualizarAdmin(id, rolId, req);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> deshabilitarAdmin(@PathVariable Integer id, @RequestParam Integer rolId) {
        usuarioService.deshabilitarAdmin(id, rolId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<Usuario>> buscarPorUsuario(
            @RequestParam("usuario") String usuario,
            @RequestParam(value = "rolId", required = false) Integer rolId,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        return ResponseEntity.ok(usuarioService.buscarPorUsuario(usuario, rolId, limit));
    }

    @GetMapping("/buscar-min")
    public ResponseEntity<List<UsuarioMinDTO>> buscarMin(
            @RequestParam("q") String q,
            @RequestParam(value = "rolId", required = false) Integer rolId,
            @RequestParam(value = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(usuarioService.buscarMin(q, rolId, limit));
    }

    private ResponseEntity<Map<String, Object>> createdUsuario(Usuario usuario, String message) {
        URI location = URI.create("/api/usuarios/" + usuario.getUsuarioId());
        return ResponseEntity.created(location).body(Map.of(
                "success", true,
                "message", message,
                "data", usuarioPayload(usuario)
        ));
    }

    private Map<String, Object> usuarioPayload(Usuario usuario) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usuarioId", usuario.getUsuarioId());
        payload.put("usuario", usuario.getUsuario());
        payload.put("nombre", usuario.getNombre());
        payload.put("cedula", usuario.getCedula());
        payload.put("correo", usuario.getCorreo());
        payload.put("telefono", usuario.getTelefono());
        payload.put("id_rol", usuario.getIdRol());
        payload.put("avatar_path", usuario.getAvatarPath());
        payload.put("avatarPath", usuario.getAvatarPath());
        return payload;
    }

    // Misma forma que usuarioPayload(usuario), pero tipada para que
    // GET /api/usuarios/me quede descrita en el contrato OpenAPI (punto 4).
    // Los demas metodos de este controlador siguen usando el Map de arriba
    // sin cambios, fuera del alcance de este punto.
    private UsuarioPerfilResponse usuarioPerfilResponse(Usuario usuario) {
        UsuarioPerfilResponse perfil = new UsuarioPerfilResponse();
        perfil.setUsuarioId(usuario.getUsuarioId());
        perfil.setUsuario(usuario.getUsuario());
        perfil.setNombre(usuario.getNombre());
        perfil.setCedula(usuario.getCedula());
        perfil.setCorreo(usuario.getCorreo());
        perfil.setTelefono(usuario.getTelefono());
        perfil.setIdRol(usuario.getIdRol());
        perfil.setAvatarPathSnake(usuario.getAvatarPath());
        perfil.setAvatarPath(usuario.getAvatarPath());
        return perfil;
    }
}
