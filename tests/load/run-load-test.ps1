param(
    [string]$HostUrl = "http://localhost:8180",
    [int]$Users = 50,
    [int]$SpawnRate = 5,
    [string]$RunTime = "60s"
)

$ErrorActionPreference = "Stop"
$results = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $results | Out-Null

python -m locust `
    -f (Join-Path $PSScriptRoot "locustfile.py") `
    --host $HostUrl `
    --headless `
    --users $Users `
    --spawn-rate $SpawnRate `
    --run-time $RunTime `
    --stop-timeout 10 `
    --csv (Join-Path $results "tiendatech-50-users") `
    --html (Join-Path $results "tiendatech-50-users.html")

$loadExitCode = $LASTEXITCODE
# Sellar también resultados de una corrida fallida, conservando su código de salida.
python (Join-Path $PSScriptRoot "../../experiments/paso8/campaign_checksums.py") --directory $results --write
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
exit $loadExitCode
