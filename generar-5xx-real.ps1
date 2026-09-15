# Genera un 5xx REAL usando el mecanismo de inyeccion de fallos ya
# existente en el sistema (X-Failure-Mode), para completar el panel
# "Tasa de errores HTTP 5xx" del dashboard con datos legitimos.
#
# Crea una cuenta de prueba desde cero (usuario, direccion y metodo de
# pago), asi que no necesitas tener ninguna cuenta previa.
#
# NOTA: usuarios-service, pedidos-service y productos-service envuelven
# TODAS sus respuestas exitosas en {status, data, message, timestamp}
# via un ResponseBodyAdvice global -- por eso cada llamada aqui abajo
# desempaqueta ".data" antes de usar el campo real.
#
# Uso:
#   .\generar-5xx-real.ps1

param(
    [string]$HostUrl = "http://localhost:8180"
)

$ErrorActionPreference = "Stop"

# 0) Datos unicos para no chocar con nada existente
$sufijo = Get-Date -Format "yyyyMMddHHmmss"
$usuario = "test5xx$sufijo"
$contrasena = "Test5xx!2026"
$correo = "test5xx$sufijo@example.com"

# 1) Habilitar temporalmente la inyeccion de fallos SOLO en ventas-service
Write-Host "1) Habilitando EXPERIMENT_FAULT_INJECTION_ENABLED y reiniciando ventas..." -ForegroundColor Cyan
$env:EXPERIMENT_FAULT_INJECTION_ENABLED = "true"
docker compose up -d --force-recreate tiendatech-ventas
Start-Sleep -Seconds 8

# 2) Crear cuenta de prueba
#    Respuesta: { status, data: { success, message, data: { usuarioId, ... } }, message, timestamp }
Write-Host "2) Creando cuenta de prueba ($usuario)..." -ForegroundColor Cyan
$crearBody = @{
    nombre    = "Test 5xx"
    cedula    = "0000000000"
    correo    = $correo
    telefono  = "0999999999"
    contrasena = $contrasena
    usuario   = $usuario
} | ConvertTo-Json
Invoke-RestMethod -Uri "$HostUrl/api/usuarios/crear" -Method Post -Body $crearBody -ContentType "application/json" | Out-Null

# 3) Login
#    Respuesta: { status, data: { token, success, user: { usuarioId, ... }, access }, message, timestamp }
Write-Host "3) Iniciando sesion..." -ForegroundColor Cyan
$loginBody = @{ usuario = $usuario; contrasena = $contrasena } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$HostUrl/api/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $login.data.access
$usuarioId = $login.data.user.usuarioId
if (-not $token -or -not $usuarioId) { throw "Login fallo o respuesta inesperada: $($login | ConvertTo-Json -Depth 5)" }
$headers = @{ Authorization = "Bearer $token" }
Write-Host "   usuarioId=$usuarioId" -ForegroundColor DarkGray

# 4) Crear una direccion (necesita una ciudad valida)
Write-Host "4) Creando direccion..." -ForegroundColor Cyan
$ciudadesResp = Invoke-RestMethod -Uri "$HostUrl/api/ciudades"
$ciudades = $ciudadesResp.data
if (-not $ciudades -or $ciudades.Count -eq 0) { throw "No hay ciudades cargadas en el sistema." }
$ciudadId = $ciudades[0].ciudadId

$direccionBody = @{ calle = "Calle de prueba 123"; referencia = "Prueba 5xx"; ciudadId = $ciudadId } | ConvertTo-Json
$direccionResp = Invoke-RestMethod -Uri "$HostUrl/api/usuarios/$usuarioId/direcciones" -Method Post -Headers $headers -Body $direccionBody -ContentType "application/json"
$direccionId = $direccionResp.data.direccionId
Write-Host "   direccionId=$direccionId (ciudadId=$ciudadId)" -ForegroundColor DarkGray

# 5) Crear un metodo de pago (necesita un tipo valido)
Write-Host "5) Creando metodo de pago..." -ForegroundColor Cyan
$tiposResp = Invoke-RestMethod -Uri "$HostUrl/api/metodopago/tipos" -Headers $headers
$tipos = $tiposResp.data
if (-not $tipos -or $tipos.Count -eq 0) { throw "No hay tipos de metodo de pago cargados en el sistema." }
$tipoId = $tipos[0].tipoId

$metodoBody = @{ numeroTarjeta = "4111111111111111"; fechaExpiracion = "2030-01-01"; tipoId = $tipoId } | ConvertTo-Json
Invoke-RestMethod -Uri "$HostUrl/api/metodopago" -Method Post -Headers $headers -Body $metodoBody -ContentType "application/json" | Out-Null

$metodosResp = Invoke-RestMethod -Uri "$HostUrl/api/metodopago/usuario/$usuarioId" -Headers $headers
$metodosPayload = $metodosResp.data
$listaMetodos = if ($metodosPayload.content) { $metodosPayload.content } else { $metodosPayload }
if (-not $listaMetodos -or $listaMetodos.Count -eq 0) { throw "No se pudo listar el metodo de pago recien creado: $($metodosResp | ConvertTo-Json -Depth 5)" }
$metodopagoId = $listaMetodos[0].metodopagoId
Write-Host "   metodopagoId=$metodopagoId (tipoId=$tipoId)" -ForegroundColor DarkGray

# 6) Agregar un producto real al carrito
Write-Host "6) Agregando un producto al carrito..." -ForegroundColor Cyan
$productosResp = Invoke-RestMethod -Uri "$HostUrl/api/productos?page=0&size=1"
$productosPayload = $productosResp.data
$listaProductos = if ($productosPayload.content) { $productosPayload.content } else { $productosPayload }
if (-not $listaProductos -or $listaProductos.Count -eq 0) { throw "No se encontro ningun producto: $($productosResp | ConvertTo-Json -Depth 5)" }
$productoId = $listaProductos[0].producto_id

$carritoResp = Invoke-RestMethod -Uri "$HostUrl/api/carrito/$usuarioId" -Headers $headers
$carritoId = $carritoResp.data.carritoId

$agregarBody = @{ productoId = $productoId; cantidad = 1 } | ConvertTo-Json
Invoke-RestMethod -Uri "$HostUrl/api/carrito/$carritoId/agregar" -Method Post -Headers $headers -Body $agregarBody -ContentType "application/json" | Out-Null
Write-Host "   productoId=$productoId agregado al carritoId=$carritoId" -ForegroundColor DarkGray

# 7) Checkout con X-Failure-Mode: omission -> dispara un 504 real en ventas-service
Write-Host "7) Enviando checkout con X-Failure-Mode: omission (tarda ~9s, es el delay real del fallo)..." -ForegroundColor Cyan
$checkoutHeaders = $headers.Clone()
$checkoutHeaders["X-Failure-Mode"] = "omission"
$checkoutBody = @{ direccionId = $direccionId; metodopagoId = $metodopagoId } | ConvertTo-Json

try {
    $resultado = Invoke-RestMethod -Uri "$HostUrl/api/ordenes/checkout" -Method Post -Headers $checkoutHeaders -Body $checkoutBody -ContentType "application/json"
    Write-Host "   El checkout respondio 2xx -- revisa si el fallo se propago igual en ventas-service:" -ForegroundColor Yellow
    Write-Host "   docker logs tiendatech-ventas --tail 50" -ForegroundColor Yellow
    $resultado | ConvertTo-Json -Depth 5
} catch {
    $statusCode = $null
    if ($_.Exception.Response) { $statusCode = $_.Exception.Response.StatusCode.value__ }
    Write-Host "   Respuesta con error (esperado): $statusCode" -ForegroundColor Green
    if ($_.ErrorDetails.Message) { Write-Host "   $($_.ErrorDetails.Message)" -ForegroundColor DarkGray }
}

Write-Host ""
Write-Host "Listo. Revisa el dashboard de Grafana (Last 15 minutes) -- el panel 'Tasa de errores HTTP 5xx' deberia mostrar una serie ahora." -ForegroundColor Cyan
Write-Host ""
Write-Host "IMPORTANTE: cuando termines de capturar la evidencia, desactiva la bandera para volver al comportamiento normal:" -ForegroundColor Magenta
Write-Host '  $env:EXPERIMENT_FAULT_INJECTION_ENABLED = "false"'
Write-Host "  docker compose up -d --force-recreate tiendatech-ventas"
