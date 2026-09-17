param(
    [string]$HostUrl = "http://localhost:8180",
    [int]$Users = 50,
    [int]$SpawnRate = 5,
    [string]$RunTime = "60s",
    # Antes fijo en "tiendatech-50-users": con un solo nivel de concurrencia
    # nunca hacia falta distinguir corridas. Se deriva de -Users por defecto
    # para que la invocacion sin argumentos (50 usuarios) siga escribiendo
    # exactamente "tiendatech-50-users" como antes; pasar un valor explicito
    # al correr varios niveles o el escenario de camino critico autenticado
    # evita que una corrida pise los archivos de la anterior.
    [string]$OutputPrefix = "tiendatech-$Users-users",
    [string]$LocustFile = "locustfile.py"
)

$ErrorActionPreference = "Stop"
$results = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $results | Out-Null

# El lanzador de Python difiere entre maquinas de equipo: algunas tienen
# "python" en el PATH, otras solo el lanzador "py" (comun en instalaciones
# de Windows desde el Store/instalador oficial). Se detecta cual existe en
# vez de asumir uno fijo, para que el script funcione igual en cualquier
# maquina del equipo sin que cada quien tenga que editarlo a mano.
$pythonCmd = if (Get-Command python -ErrorAction SilentlyContinue) { "python" }
    elseif (Get-Command py -ErrorAction SilentlyContinue) { "py" }
    else { throw "No se encontro ni 'python' ni 'py' en el PATH de esta maquina." }

# Locust escribe sus logs normales (INFO) a stderr. Con $ErrorActionPreference
# = "Stop" (fijado arriba), Windows PowerShell (5.1) y PowerShell 7 tratan esa
# salida de stderr como un error terminante aunque el proceso siga bien -- se
# baja la preferencia solo alrededor de esta llamada puntual (y se redirige
# stderr al flujo normal con 2>&1 para que no quede como registro de error),
# restaurando la preferencia despues; no afecta el resto del script.
$prevErrPref = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    & $pythonCmd -m locust `
        -f (Join-Path $PSScriptRoot $LocustFile) `
        --host $HostUrl `
        --headless `
        --users $Users `
        --spawn-rate $SpawnRate `
        --run-time $RunTime `
        --stop-timeout 10 `
        --csv (Join-Path $results $OutputPrefix) `
        --csv-full-history `
        --html (Join-Path $results "$OutputPrefix.html") 2>&1 | ForEach-Object { Write-Host $_ }
} finally {
    $ErrorActionPreference = $prevErrPref
}

$loadExitCode = $LASTEXITCODE

# Bug real encontrado en revisión (guía de cierre, punto 17): Locust, en esta
# maquina Windows, escribe los .csv con "\r\r\n" en vez de "\r\n" (probable
# doble traduccion: su propio escritor csv ya emite "\r\n", y el archivo se
# abre ademas en modo texto de Windows, que traduce "\n" -> "\r\n" otra vez).
# Python csv.reader en modo universal ve ese "\r\r\n" como una fila vacia
# despues de cada fila real (filas de longitud alternada [22,0,22,0,...]),
# que es justo el patron que senalo el docente. Se normaliza aqui, a la
# salida, a LF puro sin BOM -- no se puede corregir dentro de Locust mismo.
Get-ChildItem (Join-Path $results "$OutputPrefix*.csv") -ErrorAction SilentlyContinue | ForEach-Object {
    $bytes = [System.IO.File]::ReadAllBytes($_.FullName)
    $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    $normalized = $text -replace "`r+`n", "`n"
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($_.FullName, $normalized, $utf8NoBom)
}

# Sellar también resultados de una corrida fallida, conservando su código de salida.
& $pythonCmd (Join-Path $PSScriptRoot "../../experiments/paso8/campaign_checksums.py") --directory $results --write
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
exit $loadExitCode
