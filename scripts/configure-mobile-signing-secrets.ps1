param(
    [string]$Keystore = (Join-Path $env:USERPROFILE '.tiendatech-signing/jose-lozano-release.p12'),
    [string]$Alias = 'jose-lozano'
)
$ErrorActionPreference = 'Stop'
function Set-RepositorySecret([string]$Name, [string]$Value) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Command gh).Source
    $start.Arguments = "secret set $Name --repo JoseLozanoMorales/TiendaTech"
    $start.UseShellExecute = $false
    $start.RedirectStandardInput = $true
    $process = [Diagnostics.Process]::Start($start)
    $process.StandardInput.Write($Value)
    $process.StandardInput.Close()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw "No se pudo configurar $Name" }
}
if (!(Test-Path -LiteralPath $Keystore -PathType Leaf)) { throw 'No existe el almacén privado.' }
$secure = Read-Host 'Contraseña del almacén PKCS12 de José (entrada oculta)' -AsSecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if ([string]::IsNullOrEmpty($password)) { throw 'Contraseña vacía.' }
    $certificates = [Security.Cryptography.X509Certificates.X509Certificate2Collection]::new()
    $certificates.Import($Keystore, $password, [Security.Cryptography.X509Certificates.X509KeyStorageFlags]::EphemeralKeySet)
    try {
        $expected = '6ad168c152fd8090144c25c75d630116cc912fbe60b25150a81d88f354bc8bbd'
        $matching = @($certificates | Where-Object {
            $_.HasPrivateKey -and $_.GetCertHashString([Security.Cryptography.HashAlgorithmName]::SHA256).ToLowerInvariant() -eq $expected
        })
        if ($matching.Count -ne 1) { throw 'El almacén no contiene la identidad de firma de José esperada.' }
    } finally {
        foreach ($certificate in $certificates) { $certificate.Dispose() }
    }
    # El PKCS12 creado para José usa la misma contraseña para almacén y clave.
    Set-RepositorySecret 'TIENDATECH_KEYSTORE_PASSWORD' $password
    Set-RepositorySecret 'TIENDATECH_KEY_PASSWORD' $password
    Set-RepositorySecret 'TIENDATECH_KEY_ALIAS' $Alias
    Set-RepositorySecret 'TIENDATECH_KEYSTORE_BASE64' ([Convert]::ToBase64String([IO.File]::ReadAllBytes($Keystore)))
    Write-Host 'Los cuatro secretos de firma quedaron configurados en GitHub.'
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $password = $null
    $secure.Dispose()
}
