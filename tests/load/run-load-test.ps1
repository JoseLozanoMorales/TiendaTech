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

python -m locust `
    -f (Join-Path $PSScriptRoot $LocustFile) `
    --host $HostUrl `
    --headless `
    --users $Users `
    --spawn-rate $SpawnRate `
    --run-time $RunTime `
    --stop-timeout 10 `
    --csv (Join-Path $results $OutputPrefix) `
    --html (Join-Path $results "$OutputPrefix.html")

$loadExitCode = $LASTEXITCODE
# Sellar también resultados de una corrida fallida, conservando su código de salida.
python (Join-Path $PSScriptRoot "../../experiments/paso8/campaign_checksums.py") --directory $results --write
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
exit $loadExitCode
