# Prueba real y aislada del hallazgo (8): "agregar al carrito" sin
# docs/db/seed-inventario-stock.sql aplicado. NO reconstruye el stack --
# corre contra el que ya tengas levantado con docker-compose.yml. Si
# quieres la version mas rigurosa (clon realmente limpio), baja y sube el
# stack completo antes de correr esto:
#   docker compose -f docker-compose.yml down -v
#   docker compose -f docker-compose.yml up -d --build --wait --wait-timeout 900
#
# Que hace:
#   1) Crea un usuario de prueba nuevo (signup real, sin datos previos).
#   2) Aplica solo docs/db/seed-e2e.sql (usuario admin + UN producto,
#      producto_id=999999) -- a proposito NO aplica seed-inventario-stock.sql
#      todavia, para reproducir el estado real de un cluster recien migrado.
#   3) Intenta agregar ese producto al carrito del usuario de prueba y
#      muestra el codigo HTTP y el cuerpo real de la respuesta, para saber
#      con certeza cual es el fallo exacto (no una suposicion del comentario
#      del script).
#   4) Aplica docs/db/seed-inventario-stock.sql.
#   5) Repite el mismo agregar-al-carrito y muestra el resultado, para
#      confirmar que el seed es lo que arregla el problema (y no otra cosa).

param([string]$HostUrl = "http://localhost:8180")

$ErrorActionPreference = "Stop"

function Invoke-Diagnostico([string]$Uri, [string]$Method, $Body, $Headers) {
    try {
        $params = @{ Uri = $Uri; Method = $Method; Headers = $Headers; UseBasicParsing = $true }
        if ($Body) { $params.Body = $Body; $params.ContentType = "application/json" }
        $resp = Invoke-WebRequest @params
        return @{ Status = [int]$resp.StatusCode; Body = $resp.Content }
    } catch {
        $status = $null
        $body = $null
        if ($_.Exception.Response) {
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $body = $_.ErrorDetails.Message
        } elseif ($_.Exception.Response) {
            try {
                $stream = $_.Exception.Response.GetResponseStream()
                $stream.Position = 0
                $reader = New-Object System.IO.StreamReader($stream)
                $body = $reader.ReadToEnd()
            } catch { }
        }
        return @{ Status = $status; Body = $body; Error = $_.Exception.Message }
    }
}

Write-Host "1) Sembrando docs/db/seed-e2e.sql (usuario admin + un producto, SIN stock de inventario)..." -ForegroundColor Cyan
Get-Content docs/db/seed-e2e.sql -Raw | docker compose exec -T tiendatech-crdb-1 `
    cockroach sql --insecure --host=localhost:26257 --file=/dev/stdin

Write-Host "`n2) Creando un usuario de prueba nuevo..." -ForegroundColor Cyan
$sufijo = Get-Random -Maximum 999999
$usuario = "item8prueba$sufijo"
$contrasena = "Item8Prueba!2026"
$crearBody = @{
    nombre     = "Prueba Item 8"
    cedula     = "0000000000"
    correo     = "item8prueba$sufijo@example.com"
    telefono   = "0999999999"
    contrasena = $contrasena
    usuario    = $usuario
} | ConvertTo-Json
Invoke-RestMethod -Uri "$HostUrl/api/usuarios/crear" -Method Post -Body $crearBody -ContentType "application/json" | Out-Null

$loginBody = @{ usuario = $usuario; contrasena = $contrasena } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "$HostUrl/api/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $login.data.access
$usuarioId = $login.data.user.usuarioId
if (-not $token -or -not $usuarioId) { throw "Login fallo: $($login | ConvertTo-Json -Depth 5)" }
$headers = @{ Authorization = "Bearer $token" }
Write-Host "   usuarioId=$usuarioId" -ForegroundColor DarkGray

Write-Host "`n3) Obteniendo el carrito del usuario..." -ForegroundColor Cyan
$carritoResp = Invoke-RestMethod -Uri "$HostUrl/api/carrito/$usuarioId" -Headers $headers
$carritoId = $carritoResp.data.carritoId
Write-Host "   carritoId=$carritoId" -ForegroundColor DarkGray

$agregarBody = @{ productoId = 999999; cantidad = 1 } | ConvertTo-Json

Write-Host "`n4) *** SIN seed-inventario-stock.sql *** -- intentando agregar producto 999999 al carrito..." -ForegroundColor Yellow
$antes = Invoke-Diagnostico -Uri "$HostUrl/api/carrito/$carritoId/agregar" -Method Post -Body $agregarBody -Headers $headers
Write-Host "   HTTP status: $($antes.Status)" -ForegroundColor Yellow
Write-Host "   Cuerpo: $($antes.Body)" -ForegroundColor Yellow
if ($antes.Error) { Write-Host "   Excepcion .NET: $($antes.Error)" -ForegroundColor Yellow }

Write-Host "`n5) Aplicando docs/db/seed-inventario-stock.sql..." -ForegroundColor Cyan
Get-Content docs/db/seed-inventario-stock.sql -Raw | docker compose exec -T tiendatech-crdb-1 `
    cockroach sql --insecure --host=localhost:26257 -d tiendatech --file=/dev/stdin

Write-Host "`n6) *** CON seed-inventario-stock.sql *** -- repitiendo el mismo agregar-al-carrito..." -ForegroundColor Green
$despues = Invoke-Diagnostico -Uri "$HostUrl/api/carrito/$carritoId/agregar" -Method Post -Body $agregarBody -Headers $headers
Write-Host "   HTTP status: $($despues.Status)" -ForegroundColor Green
Write-Host "   Cuerpo: $($despues.Body)" -ForegroundColor Green
if ($despues.Error) { Write-Host "   Excepcion .NET: $($despues.Error)" -ForegroundColor Green }

Write-Host "`n=== Resumen ===" -ForegroundColor Magenta
Write-Host "Sin seed: HTTP $($antes.Status)"
Write-Host "Con seed: HTTP $($despues.Status)"
