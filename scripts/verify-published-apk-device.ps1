param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,

    [string]$ApkPath = "release/tiendatech-release.apk",

    [string]$OutputDirectory = "docs/evidencias/firma-release-jose/dispositivos-apk-publicado",

    [string]$AdbPath
)

$ErrorActionPreference = "Stop"
$expectedSha256 = "b1e622a201e40cc662a1a768e3832ba53e5274b6aacd2d10f5150f4eccb1d304"
$expectedBytes = 35849007
$packageName = "com.tiendatech.mobile"

$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path
$apk = Get-Item -LiteralPath $resolvedApk
$actualSha256 = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToLowerInvariant()

if ($actualSha256 -ne $expectedSha256) {
    throw "APK incorrecto: SHA-256 $actualSha256; se esperaba $expectedSha256"
}
if ($apk.Length -ne $expectedBytes) {
    throw "APK incorrecto: $($apk.Length) bytes; se esperaban $expectedBytes"
}

$adb = $null
if ($AdbPath) {
    $adb = (Resolve-Path -LiteralPath $AdbPath).Path
} else {
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        $adb = $adbCommand.Source
    }
}
if (-not $adb) {
    throw "adb no está disponible. Usa -AdbPath con la ruta al adb.exe de Android Platform Tools."
}

$deviceState = (& $adb -s $Serial get-state 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $deviceState -ne "device") {
    throw "El dispositivo indicado no está autorizado o conectado: $deviceState"
}

$model = (& $adb -s $Serial shell getprop ro.product.model | Out-String).Trim()
$manufacturer = (& $adb -s $Serial shell getprop ro.product.manufacturer | Out-String).Trim()
$androidVersion = (& $adb -s $Serial shell getprop ro.build.version.release | Out-String).Trim()
$androidSdk = (& $adb -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()

# No desinstala aplicaciones ni borra datos. Si existe una versión con otra
# firma, adb fallará y el usuario deberá decidir explícitamente cómo proceder.
$installOutput = (& $adb -s $Serial install -r $resolvedApk 2>&1 | Out-String).Trim()
$installExitCode = $LASTEXITCODE
if ($installExitCode -ne 0 -or $installOutput -notmatch "Success") {
    throw "La instalación falló sin modificar ni desinstalar automáticamente: $installOutput"
}

$packagePath = (& $adb -s $Serial shell pm path $packageName 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0 -or $packagePath -notmatch "^package:") {
    throw "El paquete instalado no se pudo localizar: $packagePath"
}

# `adb shell monkey` escribe líneas informativas en stderr incluso cuando
# termina correctamente. Windows PowerShell transforma esas líneas en
# NativeCommandError bajo Stop; se relaja solo durante esta invocación y se
# conserva el código de salida real de adb.
$previousErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    $launchOutput = (& $adb -s $Serial shell monkey -p $packageName -c android.intent.category.LAUNCHER 1 2>&1 | Out-String).Trim()
    $launchExitCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previousErrorActionPreference
}
if ($launchExitCode -ne 0 -or $launchOutput -notmatch "Events injected: 1") {
    throw "La aplicación se instaló, pero no se pudo iniciar: $launchOutput"
}

$serialBytes = [Text.Encoding]::UTF8.GetBytes($Serial)
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $serialDigest = $sha256.ComputeHash($serialBytes)
} finally {
    $sha256.Dispose()
}
$serialHash = -join ($serialDigest | ForEach-Object { $_.ToString("x2") })
$safeDeviceId = $serialHash.Substring(0, 12)
$timestamp = [DateTimeOffset]::UtcNow.ToString("o")

New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$outputPath = Join-Path $OutputDirectory "device-$safeDeviceId.json"

$evidence = [ordered]@{
    checked_at_utc = $timestamp
    apk = [ordered]@{
        source = "release/tiendatech-release.apk"
        bytes = $apk.Length
        sha256 = $actualSha256
        release_url = "https://github.com/JoseLozanoMorales/TiendaTech/releases/download/v4.0.0/tiendatech-release.apk"
    }
    device = [ordered]@{
        serial_sha256 = $serialHash
        manufacturer = $manufacturer
        model = $model
        android_version = $androidVersion
        sdk = $androidSdk
    }
    validation = [ordered]@{
        install_command = "adb -s <serial> install -r release/tiendatech-release.apk"
        install_exit_code = $installExitCode
        install_output = $installOutput
        installed_package_path = $packagePath
        launch_package = $packageName
        launch_exit_code = $launchExitCode
        launch_confirmed = $true
    }
}

$json = $evidence | ConvertTo-Json -Depth 6
# Windows PowerShell 5.1 escribe BOM con `Set-Content -Encoding utf8`. El
# repositorio exige UTF-8 sin BOM, por eso se usa explícitamente UTF8Encoding.
$utf8WithoutBom = New-Object Text.UTF8Encoding($false)
[IO.File]::WriteAllText((Join-Path (Get-Location) $outputPath), $json + "`n", $utf8WithoutBom)
Write-Host "Evidencia creada: $outputPath"
Write-Host "APK verificado: $actualSha256 ($($apk.Length) bytes)"
Write-Host "Dispositivo: $manufacturer $model, Android $androidVersion (SDK $androidSdk)"
