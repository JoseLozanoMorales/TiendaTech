# Correcciones de código — capturas rápidas

Se modificaron dos archivos de la aplicación con asistencia de IA. Sin commit ni push.

## 1. Validación por tokens completos

Archivo desde la raíz: `services/armado-ia/app/explicacion/validation.py`.
Captura las líneas **67–83**, la clase `VerifiedHardwareTokenHandler`.

Antes se buscaba una subcadena: 500 W se aceptaba dentro de 1500 W y RTX 4060 dentro de RTX 4060 Ti.
Ahora se extraen los tokens reconocidos del contexto y se comparan completos, normalizando espacios y mayúsculas.
El validador sigue limitado a los patrones de hardware declarados: no verifica toda la semántica del texto.

## 2. Una sola ejecución del respaldo

Archivo: `services/armado-ia/app/explicacion/service.py`.
Captura las líneas **37–55**, desde `try` hasta el retorno del respaldo.

Antes, si se rechazaba una respuesta y el respaldo fallaba, el mismo `except` interceptaba ese fallo y volvía a ejecutar el respaldo y su contador.
Ahora el respaldo se ejecuta fuera del bloque que captura fallos del proveedor. Si falla, su error se propaga sin una segunda llamada.

## 3. Pruebas de las correcciones

Archivo: `services/armado-ia/tests/test_explicacion_regresiones.py`.
- Captura líneas **14–31**: potencias, variante Ti y normalización.
- Captura líneas **33–45**: respaldo ejecutado una sola vez y contador único.

## 4. Antes y después reales

- Abre `09-pruebas-antes.txt`: final `Ran 15 tests` y `FAILED (failures=3)`.
- Abre `09-pruebas-despues.txt`: final `Ran 15 tests` y `OK`.

Las cuatro pruebas nuevas se ejecutaron junto a las once anteriores. Antes fallaban tres casos; después pasan los quince.
Pruebas unitarias con dobles del proveedor, sin Bedrock ni servicios distribuidos.
Entorno: Python incluido en Codex, Pydantic 2.13.5 y prometheus-client 0.26.0; no se verificó con todo el entorno fijado en requirements.txt.

Comando reproducible desde `services/armado-ia`, con las dependencias instaladas:

```text
python -m unittest tests.test_explicacion_regresiones tests.test_explicacion_resiliencia tests.test_explicacion_validation -v
```

También se ajustó el verificador para revisar únicamente los diagramas declarados en `referencias.json` y permitir que convivan las copias personales adicionales.
Las capturas y resultados anteriores se conservan como registros de su ejecución inicial.
