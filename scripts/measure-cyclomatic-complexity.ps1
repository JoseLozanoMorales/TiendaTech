param(
    [string]$OutputDirectory = "docs/experimentos/resultados/iso25010/complejidad"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$onWindows = $env:OS -eq "Windows_NT"
$wrapperPath = if ($onWindows) { "Apps/web/frontend/mvnw.cmd" } else { "Apps/web/frontend/mvnw" }
$mvnw = Join-Path $root $wrapperPath
$ruleset = Join-Path $root "docs/experimentos/pmd-cyclomatic-ruleset.xml"
$repository = Join-Path ([System.IO.Path]::GetTempPath()) "tiendatech-pmd-m2"
$pmdLib = Join-Path ([System.IO.Path]::GetTempPath()) "tiendatech-pmd-lib"
$output = Join-Path $root $OutputDirectory
New-Item -ItemType Directory -Force -Path $output | Out-Null
New-Item -ItemType Directory -Force -Path $pmdLib | Out-Null

if ($onWindows) {
    & $mvnw -q -f (Join-Path $root "scripts/pmd-cli-pom.xml") "-Dmaven.repo.local=$repository" dependency:copy-dependencies "-DoutputDirectory=$pmdLib"
} else {
    & bash $mvnw -q -f (Join-Path $root "scripts/pmd-cli-pom.xml") "-Dmaven.repo.local=$repository" dependency:copy-dependencies "-DoutputDirectory=$pmdLib"
}
if ($LASTEXITCODE -ne 0) { throw "No se pudieron resolver las dependencias fijadas de PMD" }

$modules = [ordered]@{
    gateway = "Apps/web/frontend/pom.xml"
    inventario = "services/inventario-service/pom.xml"
    ordenes_proveedores = "services/ordenes-proveedores-service/pom.xml"
    pedidos = "services/pedidos-service/pom.xml"
    productos = "services/productos-service/pom.xml"
    usuarios = "services/usuarios/pom.xml"
    ventas = "services/ventas-service/pom.xml"
}

$classpathSeparator = if ($onWindows) { ";" } else { ":" }
$classpath = (Get-ChildItem -Path $pmdLib -Filter "*.jar" | ForEach-Object { $_.FullName }) -join $classpathSeparator

$rows = @()
foreach ($entry in $modules.GetEnumerator()) {
    $targetReport = Join-Path $output "$($entry.Key)-pmd.xml"
    $sourceDir = Join-Path (Split-Path (Join-Path $root $entry.Value)) "src/main/java"
    # El comodin "dir/*" de la JVM funciona en Windows porque PowerShell no lo
    # expande antes de invocar java, pero pwsh en Linux si hace glob-expansion
    # del "*", mandando los jars como argumentos sueltos y descuadrando -cp.
    # Se arma el classpath explicito para que sea igual en ambos sistemas.
    & java -cp $classpath net.sourceforge.pmd.cli.PmdCli check --dir $sourceDir --rulesets $ruleset --format xml --report-file $targetReport --no-progress
    if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne 4) { throw "PMD fallo para $($entry.Key)" }
    [xml]$xml = Get-Content $targetReport
    $values = @($xml.pmd.file.violation | Where-Object { $_.method } | ForEach-Object {
        if ($_.InnerText -match "cyclomatic complexity of (\d+)") { [int]$Matches[1] }
    })
    $maximum = if ($values.Count) { ($values | Measure-Object -Maximum).Maximum } else { 1 }
    $rows += [pscustomobject]@{ module = $entry.Key; max_method_complexity = $maximum; objective = "<10"; status = if ($maximum -lt 10) { "CUMPLE" } else { "NO CUMPLE" }; report = $targetReport }
}
$rows | Export-Csv -NoTypeInformation -Encoding utf8 -Path (Join-Path $output "summary.csv")
$rows | Format-Table -AutoSize
