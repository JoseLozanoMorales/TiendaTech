from decimal import Decimal

from app.clients.producto_client import ProductoClient


def test_desenvuelve_respuesta_uniforme_de_productos_service():
    producto = {"id": 27, "nombre": "Ryzen 7", "precio": 519.0}
    respuesta = {
        "status": 200,
        "data": producto,
        "message": "OK",
        "timestamp": "2026-09-08T04:02:37Z",
    }

    assert ProductoClient._desenvolver_respuesta(respuesta) == producto


def test_conserva_payload_legado_sin_sobre():
    producto = {"id": 27, "nombre": "Ryzen 7", "precio": 519.0}

    assert ProductoClient._desenvolver_respuesta(producto) == producto


def test_mapea_producto_y_desenvuelve_jsonb_anidado():
    fila = {
        "id": 27,
        "nombre": "Ryzen 7",
        "precio": 519.0,
        "categoria_id": 2,
        "categoria": "Procesador",
        "atributos": {
            "type": "jsonb",
            "value": (
                '{"type":"jsonb","value":"{\\"nucleos\\":8,'
                '\\"frecuencia_turbo_ghz\\":5.2,\\"hilos\\":16}"}'
            ),
        },
    }

    producto = ProductoClient.__new__(ProductoClient)._mapear(fila)

    assert producto.id == 27
    assert producto.precio == Decimal("519.0")
    assert producto.atributos == {
        "nucleos": 8,
        "frecuencia_turbo_ghz": 5.2,
        "hilos": 16,
    }
