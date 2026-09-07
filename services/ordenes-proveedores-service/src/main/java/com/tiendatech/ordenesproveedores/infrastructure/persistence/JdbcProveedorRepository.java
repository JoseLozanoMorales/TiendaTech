package com.tiendatech.ordenesproveedores.infrastructure.persistence;

import com.tiendatech.ordenesproveedores.domain.Proveedor;
import com.tiendatech.ordenesproveedores.domain.ProveedorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;


@Repository
public class JdbcProveedorRepository implements ProveedorRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcProveedorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Integer crear(Proveedor p) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO ordenes_proveedores.proveedor
                    (nombre, ruc, contacto_nombre, telefono, correo, direccion, activo)
                VALUES (?, ?, ?, ?, ?, ?, true)
                RETURNING proveedor_id
                """, Integer.class, p.getNombre(), p.getRuc(), p.getContactoNombre(),
                p.getTelefono(), p.getCorreo(), p.getDireccion());
    }

    @Override
    public void actualizar(Proveedor p) {
        int changed = jdbcTemplate.update("""
                UPDATE ordenes_proveedores.proveedor
                   SET nombre=?, contacto_nombre=?, telefono=?, correo=?, direccion=?
                 WHERE proveedor_id=?
                """, p.getNombre(), p.getContactoNombre(), p.getTelefono(), p.getCorreo(),
                p.getDireccion(), p.getProveedorId());
        requireOne(changed, p.getProveedorId());
    }

    @Override
    public void desactivar(Integer proveedorId) {
        requireOne(jdbcTemplate.update("UPDATE ordenes_proveedores.proveedor SET activo=false WHERE proveedor_id=?",
                proveedorId), proveedorId);
    }

    @Override
    public void activar(Integer proveedorId) {
        requireOne(jdbcTemplate.update("UPDATE ordenes_proveedores.proveedor SET activo=true WHERE proveedor_id=?",
                proveedorId), proveedorId);
    }

    // Devuelve todos los proveedores (activos e inactivos): la lista de administracion
    // de proveedores debe seguir mostrando los desactivados, solo que marcados como
    // "Inactivo". El filtro a solo-activos se hace en el select de creacion de ordenes.
    @Override
    public List<Proveedor> listarTodos() {
        return jdbcTemplate.query("""
                SELECT proveedor_id, nombre, ruc, contacto_nombre, telefono, correo, direccion, activo
                  FROM ordenes_proveedores.proveedor ORDER BY nombre
                """, this::map);
    }

    @Override
    public Proveedor obtenerPorId(Integer proveedorId) {
        return jdbcTemplate.query("""
                SELECT proveedor_id, nombre, ruc, contacto_nombre, telefono, correo, direccion, activo
                  FROM ordenes_proveedores.proveedor WHERE proveedor_id=?
                """, this::map, proveedorId).stream().findFirst().orElse(null);
    }

    private Proveedor map(ResultSet rs, int row) throws SQLException {
        Proveedor p = new Proveedor();
        p.setProveedorId(rs.getInt("proveedor_id"));
        p.setNombre(rs.getString("nombre"));
        p.setRuc(rs.getString("ruc"));
        p.setContactoNombre(rs.getString("contacto_nombre"));
        p.setTelefono(rs.getString("telefono"));
        p.setCorreo(rs.getString("correo"));
        p.setDireccion(rs.getString("direccion"));
        p.setActivo(rs.getBoolean("activo"));
        return p;
    }

    private void requireOne(int changed, Integer id) {
        if (changed != 1) throw new IllegalArgumentException("Proveedor " + id + " no existe");
    }
}
