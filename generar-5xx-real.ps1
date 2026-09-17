# Genera un 5xx REAL usando el mecanismo de inyeccion de fallos ya
# existente en el sistema (X-Failure-Mode), para completar el panel
# "Tasa de errores HTTP 5xx" del dashboard con datos legitimos.
#
# Crea cuentas de prueba desde cero (usuario, direccion y metodo de
# pago), asi que no necesitas tener ninguna cuenta previa.
#
# NOTA: usuarios-service, pedidos-service y productos-service envuelven
# TODAS sus respuestas exitosas en {status, data, message, timestamp}
# via un ResponseBodyAdvice global -- por eso cada llamada aqui abajo
# desempaqueta ".data" antes de usar el campo real.
#
# IMPORTANTE si vas a correr esto en paralelo con un test de carga (para
# que el panel 4xx y el 5xx muestren datos a la vez): con 400 usuarios
# concurrentes saturando el rate limiter del Gateway, los propios pasos de
# preparacion de este script (crear cuenta, direccion, metodo de pago)
# TAMBIEN pueden chocar contra el limiter -- no solo el checkout. Por eso
# el script tiene dos fases separables:
#
#   1) Preparar las cuentas ANTES de arrancar la carga (limiter tranquilo):
#        .\generar-5xx-real.ps1 -Fase Preparar -Repeticiones 5
#
#   2) Arrancar el test de carga, y recien ahi disparar los checkouts ya
#      preparados (solo esta parte necesita solaparse en el tiempo):
#        .\generar-5xx-real.ps1 -Fase Disparar -OmitirReinicio
#
# Uso de una sola pasada (sin carga concurrente, o con margen de sobra):
#   .\generar-5xx-real.ps1
#   .\generar-5xx-real.ps1 -Repeticiones 5

param(
    [string]$HostUrl = "http://localhost:8180",
    [int]$Repeticiones = 1,
    [switch]$OmitirReinicio,
    [ValidateSet("Completo", "Preparar", "Disparar")]
    [string]$Fase = "Completo",
    [string]$ArchivoPreparados = (Join-Path $env:TEMP "generar-5xx-preparados.json"),
    # Dato crudo del punto 17: antes solo quedaba la captura de Grafana como
    # evidencia de la corrida 5xx, sin ningun registro de que checkout disparo
    # que codigo de respuesta y cuando. Se acumula aqui uno por intento.
    [string]$ArchivoRegistro = "docs/evidencias/punto17-observabilidad-gateway/disparos-5xx-real.json"
)

$script:RegistroDisparos = @()

$ErrorActionPreference = "Stop"

function Habilitar-InyeccionFallos {
    if ($OmitirReinicio) {
        Write-Host "1) Omitido (ya deberia estar habilitada de una corrida anterior)." -ForegroundColor DarkGray
        return
    }
    Write-Host "1) Habilitando EXPERIMENT_FAULT_INJECTION_ENABLED y reiniciando ventas..." -ForegroundColor Cyan
    $env:EXPERIMENT_FAULT_INJECTION_ENABLED = "true"
    docker compose up -d --force-recreate tiendatech-ventas
    Start-Sleep -Seconds 8
}

function Preparar-Cuenta([string]$sufijo) {
    $usuario = "test5xx$sufijo"
    $contrasena = "Test5xx!2026"
    $correo = "test5xx$sufijo@example.com"

    Write-Host "  Creando cuenta de prueba ($usuario)..." -ForegroundColor Cyan
    $crearBody = @{
        nombre     = "Test 5xx"
        cedula     = "0000000000"
        correo     = $correo
        telefono   = "0999999999"
        contrasena = $contrasena
        usuario    = $usuario
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$HostUrl/api/usuarios/crear" -Method Post -Body $crearBody -ContentType "application/json" | Out-Null

    Write-Host "  Iniciando sesion..." -ForegroundColor Cyan
    $loginBody = @{ usuario = $usuario; contrasena = $contrasena } | ConvertTo-Json
    $login = Invoke-RestMethod -Uri "$HostUrl/api/login" -Method Post -Body $loginBody -ContentType "application/json"
    $token = $login.data.access
    $usuarioId = $login.data.user.usuarioId
    if (-not $token -or -not $usuarioId) { throw "Login fallo o respuesta inesperada: $($login | ConvertTo-Json -Depth 5)" }
    $headers = @{ Authorization = "Bearer $token" }

    Write-Host "  Creando direccion..." -ForegroundColor Cyan
    $ciudadesResp = Invoke-RestMethod -Uri "$HostUrl/api/ciudades"
    $ciudades = $ciudadesResp.data
    if (-not $ciudades -or $ciudades.Count -eq 0) { throw "No hay ciudades cargadas en el sistema." }
    $ciudadId = $ciudades[0].ciudadId
    $direccionBody = @{ calle = "Calle de prueba 123"; referencia = "Prueba 5xx"; ciudadId = $ciudadId } | ConvertTo-Json
    $direccionResp = Invoke-RestMethod -Uri "$HostUrl/api/usuarios/$usuarioId/direcciones" -Method Post -Headers $headers -Body $direccionBody -ContentType "application/json"
    $direccionId = $direccionResp.data.direccionId

    Write-Host "  Creando metodo de pago..." -ForegroundColor Cyan
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

    Write-Host "  Agregando un producto al carrito..." -ForegroundColor Cyan
    $productosResp = Invoke-RestMethod -Uri "$HostUrl/api/productos?page=0&size=1"
    $productosPayload = $productosResp.data
    $listaProductos = if ($productosPayload.content) { $productosPayload.content } else { $productosPayload }
    if (-not $listaProductos -or $listaProductos.Count -eq 0) { throw "No se encontro ningun producto: $($productosResp | ConvertTo-Json -Depth 5)" }
    $productoId = $listaProductos[0].producto_id
    $carritoResp = Invoke-RestMethod -Uri "$HostUrl/api/carrito/$usuarioId" -Headers $headers
    $carritoId = $carritoResp.data.carritoId
    $agregarBody = @{ productoId = $productoId; cantidad = 1 } | ConvertTo-Json
    Invoke-RestMethod -Uri "$HostUrl/api/carrito/$carritoId/agregar" -Method Post -Headers $headers -Body $agregarBody -ContentType "application/json" | Out-Null

    Write-Host "  Listo: usuarioId=$usuarioId direccionId=$direccionId metodopagoId=$metodopagoId carritoId=$carritoId" -ForegroundColor DarkGray
    return @{
        token        = $token
        usuarioId    = $usuarioId
        direccionId  = $direccionId
        metodopagoId = $metodopagoId
    }
}

function Disparar-Checkout($cuenta) {
    Write-Host "  Enviando checkout con X-Failure-Mode: omission (tarda ~9s, es el delay real del fallo)..." -ForegroundColor Cyan
    $checkoutHeaders = @{ Authorization = "Bearer $($cuenta.token)"; "X-Failure-Mode" = "omission" }
    $checkoutBody = @{ direccionId = $cuenta.direccionId; metodopagoId = $cuenta.metodopagoId } | ConvertTo-Json
    $inicio = Get-Date
    $registro = [ordered]@{
        usuarioId    = $cuenta.usuarioId
        timestampUtc = $inicio.ToUniversalTime().ToString("o")
        statusCode   = $null
        resultado    = $null
        mensaje      = $null
    }
    try {
        $resultado = Invoke-RestMethod -Uri "$HostUrl/api/ordenes/checkout" -Method Post -Headers $checkoutHeaders -Body $checkoutBody -ContentType "application/json"
        Write-Host "  El checkout respondio 2xx -- revisa si el fallo se propago igual en ventas-service:" -ForegroundColor Yellow
        Write-Host "  docker logs tiendatech-ventas --tail 50" -ForegroundColor Yellow
        $resultado | ConvertTo-Json -Depth 5
        $registro.statusCode = 200
        $registro.resultado = "2xx-inesperado"
    } catch {
        $statusCode = $null
        if ($_.Exception.Response) { $statusCode = $_.Exception.Response.StatusCode.value__ }
        Write-Host "  Respuesta con error (esperado): $statusCode" -ForegroundColor Green
        if ($_.ErrorDetails.Message) { Write-Host "  $($_.ErrorDetails.Message)" -ForegroundColor DarkGray }
        $registro.statusCode = $statusCode
        $registro.resultado = if ($statusCode -ge 500) { "5xx-real" } elseif ($statusCode -eq 429) { "429-rate-limiter" } else { "otro" }
        $registro.mensaje = $_.ErrorDetails.Message
    }
    $registro.duracionSegundos = [Math]::Round(((Get-Date) - $inicio).TotalSeconds, 1)
    $script:RegistroDisparos += [pscustomobject]$registro
}

if ($Fase -eq "Disparar") {
    Habilitar-InyeccionFallos
    if (-not (Test-Path $ArchivoPreparados)) { throw "No existe $ArchivoPreparados -- corre primero -Fase Preparar." }
    $cuentas = Get-Content $ArchivoPreparados -Raw | ConvertFrom-Json
    Write-Host "Disparando checkout para $($cuentas.Count) cuenta(s) preparada(s)..." -ForegroundColor White
    $i = 0
    foreach ($cuenta in $cuentas) {
        $i++
        Write-Host "`n=== Checkout $i de $($cuentas.Count) ===" -ForegroundColor White
        Disparar-Checkout $cuenta
        if ($i -lt $cuentas.Count) { Start-Sleep -Seconds 3 }
    }
    Write-Host "`nListo. Revisa el dashboard de Grafana (Last 15 minutes) -- el panel 'Tasa de errores HTTP 5xx' deberia mostrar una serie ahora." -ForegroundColor Cyan
}
elseif ($Fase -eq "Preparar") {
    Habilitar-InyeccionFallos
    $cuentas = @()
    for ($rep = 1; $rep -le $Repeticiones; $rep++) {
        Write-Host "`n=== Preparando cuenta $rep de $Repeticiones ===" -ForegroundColor White
        $sufijo = "$(Get-Date -Format 'yyyyMMddHHmmss')-$rep"
        $cuentas += Preparar-Cuenta $sufijo
    }
    $cuentas | ConvertTo-Json -Depth 5 | Set-Content -Path $ArchivoPreparados -Encoding utf8
    Write-Host "`n$($cuentas.Count) cuenta(s) preparada(s) y guardada(s) en $ArchivoPreparados." -ForegroundColor Cyan
    Write-Host "Arranca ahora el test de carga y, mientras siga corriendo, ejecuta:" -ForegroundColor Cyan
    Write-Host "  .\generar-5xx-real.ps1 -Fase Disparar -OmitirReinicio" -ForegroundColor Cyan
}
else {
    Habilitar-InyeccionFallos
    for ($rep = 1; $rep -le $Repeticiones; $rep++) {
        if ($Repeticiones -gt 1) { Write-Host "`n=== Repeticion $rep de $Repeticiones ===" -ForegroundColor White }
        $sufijo = "$(Get-Date -Format 'yyyyMMddHHmmss')-$rep"
        $cuenta = Preparar-Cuenta $sufijo
        Disparar-Checkout $cuenta
        if ($rep -lt $Repeticiones) { Start-Sleep -Seconds 3 }
    }
    Write-Host "`nListo ($Repeticiones repeticion(es)). Revisa el dashboard de Grafana (Last 15 minutes) -- el panel 'Tasa de errores HTTP 5xx' deberia mostrar una serie ahora." -ForegroundColor Cyan
}

if ($script:RegistroDisparos.Count -gt 0) {
    $carpetaRegistro = Split-Path -Parent $ArchivoRegistro
    if ($carpetaRegistro -and -not (Test-Path $carpetaRegistro)) {
        New-Item -ItemType Directory -Force -Path $carpetaRegistro | Out-Null
    }
    # -Encoding utf8 en Windows PowerShell 5.1 escribe BOM UTF-8, que
    # check_no_bom.py rechaza para archivos versionados (encontrado al
    # comitear disparos-5xx-real.json por primera vez, CI "Integridad de
    # datos" #83). Se escribe explicitamente sin BOM, igual que la
    # normalizacion ya aplicada a los CSV de Locust en run-load-test.ps1.
    $jsonRegistro = $script:RegistroDisparos | ConvertTo-Json -Depth 5
    $utf8SinBom = New-Object System.Text.UTF8Encoding($false)
    $rutaAbsolutaRegistro = Join-Path (Get-Location) $ArchivoRegistro
    [System.IO.File]::WriteAllText($rutaAbsolutaRegistro, $jsonRegistro, $utf8SinBom)
    $resumen = $script:RegistroDisparos | Group-Object resultado | ForEach-Object { "$($_.Name)=$($_.Count)" }
    Write-Host "`nDato crudo de esta corrida guardado en $ArchivoRegistro ($($resumen -join ', '))." -ForegroundColor Cyan
}

Write-Host ""
Write-Host "IMPORTANTE: cuando termines de capturar la evidencia, desactiva la bandera para volver al comportamiento normal:" -ForegroundColor Magenta
Write-Host '  $env:EXPERIMENT_FAULT_INJECTION_ENABLED = "false"'
Write-Host "  docker compose up -d --force-recreate tiendatech-ventas"
