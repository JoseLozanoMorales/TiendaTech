-- Semilla determinista para las pruebas E2E de navegador (punto 13 de la
-- rúbrica: "Pruebas de extremo a extremo").
--
-- Antes de este archivo, las dos pruebas que ejercitan datos (catalog.spec.js
-- y el login de auth-admin.spec.js) solo podían pasar contra un interceptor
-- que respondía desde constantes escritas en el propio archivo de prueba,
-- porque no existía ningún usuario administrador ni ningún producto sembrado
-- en la base real. Este script deja lo mínimo necesario para que esas mismas
-- aserciones sigan siendo ciertas contra el sistema real:
--   - un usuario con rol admin (usuario "admin", contraseña "Secreto123!")
--   - un producto "Procesador Ryzen 7" habilitado, visible en /api/productos
-- Es idempotente (UPSERT / ON CONFLICT DO NOTHING) para poder aplicarse en
-- cada corrida de CI sin fallar si ya se había aplicado antes.

USE tiendatech;

-- Catálogos mínimos que necesita productos.producto (mismos valores que
-- docs/db/product-management-reference.sql; se repiten aquí para que este
-- archivo sea autosuficiente y no dependa del orden de otros scripts).
UPSERT INTO productos.categoria_producto
    (id_categoria, nombre, habilitado, obligatoria_pc, peso_presupuesto)
VALUES
    (2, 'CPU', true, true, 0.20);

UPSERT INTO productos.marca (marca_id, nombre, habilitado) VALUES
    (2, 'AMD', true);

UPSERT INTO productos.gama (gama_id, tipo_gama, precio_ensamblado, habilitado) VALUES
    (3, 'Alta', 40.00, true);

UPSERT INTO productos.iva (iva_id, porcentaje, habilitado) VALUES
    (1, 15.00, true);

-- Producto que catalog.spec.js espera ver listado y encontrar por nombre.
-- producto_id fijo y alto (999999) para no chocar con IDs generados por otras
-- semillas (por ejemplo docs/db/seed-catalogo-sintetico.sql, que usa 20000+).
UPSERT INTO productos.producto
    (producto_id, nombre, preciounitario, stock, marca_id, gama_id, iva_id,
     costo, categoria_id, habilitado)
VALUES
    (999999, 'Procesador Ryzen 7', 349.99, 10, 2, 3, 1, 200.00, 2, true);

-- Roles mínimos que exige UsuarioService (1 = admin, 3 = trabajador; 2 queda
-- reservado para clientes, ver LoginController.roleName).
UPSERT INTO usuarios.rol (rol_id, nombre, habilitado) VALUES
    (1, 'ADMIN', true),
    (2, 'CLIENTE', true),
    (3, 'TRABAJADOR', true);

-- Usuario administrador que auth-admin.spec.js usa para iniciar sesión.
-- El hash corresponde a la contraseña en claro "Secreto123!" (BCrypt, costo
-- 12, igual que SecurityBeans.passwordEncoder() en el servicio de usuarios).
-- Generado con: python3 -c "import bcrypt; print(bcrypt.hashpw(b'Secreto123!', bcrypt.gensalt(rounds=12)).decode())"
--
-- cedula y telefono se siembran con un valor (no NULL) a propósito: son
-- columnas opcionales del esquema, pero LoginController.login() arma la
-- respuesta con Map.of(...), que en Java lanza NullPointerException si
-- CUALQUIER valor es null. Un usuario real sin cédula u sin teléfono
-- registrado haría que /api/login devuelva 500 hoy mismo — no es un
-- requisito de este seed, es un defecto real del backend que este seed
-- sortea a propósito para no bloquear el punto 13 con un bug ajeno a él
-- (ver el aviso aparte sobre esto).
UPSERT INTO usuarios.usuario
    (usuario_id, nombre, cedula, correo, telefono, usuario, contrasenia, rol_id, habilitado)
VALUES
    (999999, 'Administrador E2E', '9999999999', 'admin.e2e@tiendatech.local',
     '0999999999', 'admin',
     '$2b$12$lYPCF.IIZQr9YHCWcH6QC.ik4FvU7X1EcGi9RQPxw2xv5xyct0bU.', 1, true);
