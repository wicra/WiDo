<#
.SYNOPSIS
  Crée la clé de signature de WiDo et l'enregistre comme secrets GitHub Actions (Windows).
.DESCRIPTION
  Prérequis : un JDK 17+ (keytool) ; la CLI gh connectée (gh auth login) pour l'envoi automatique.
  Sans gh, le script prépare la valeur à coller à la main dans GitHub.
.EXAMPLE
  .\scripts\setup-signing.ps1 -Repo wicra/WiDo
#>
param(
    [string]$Repo = "wicra/WiDo",
    [string]$Out = (Join-Path $HOME "wido-signing")
)
$ErrorActionPreference = "Stop"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool introuvable : installe un JDK 17+ (winget install EclipseAdoptium.Temurin.17.JDK) puis rouvre PowerShell."
}
$ks = Join-Path $Out "wido.jks"
if (Test-Path $ks) {
    throw "$ks existe déjà : je refuse de l'écraser (perdre la clé empêche toute mise à jour)."
}
New-Item -ItemType Directory -Force -Path $Out | Out-Null

$secure = Read-Host "Choisis un mot de passe pour la clé (12 caractères minimum)" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $pass = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
if ($pass.Length -lt 12) { throw "Mot de passe trop court." }

& keytool -genkeypair -keystore $ks -alias wido -keyalg RSA -keysize 4096 -validity 10000 `
    -storepass $pass -keypass $pass -dname "CN=WiDo, O=WiDo"
if ($LASTEXITCODE -ne 0) { throw "keytool a échoué." }

$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($ks))

$ghReady = $false
if (Get-Command gh -ErrorAction SilentlyContinue) {
    & gh auth status *> $null
    $ghReady = ($LASTEXITCODE -eq 0)
}

if ($ghReady) {
    Write-Host "Envoi des 4 secrets vers $Repo…"
    & gh secret set WIDO_KEYSTORE_BASE64   --repo $Repo --body $b64
    & gh secret set WIDO_KEYSTORE_PASSWORD --repo $Repo --body $pass
    & gh secret set WIDO_KEY_ALIAS         --repo $Repo --body "wido"
    & gh secret set WIDO_KEY_PASSWORD      --repo $Repo --body $pass
    Write-Host "Secrets enregistrés."
} else {
    $b64 | Set-Clipboard
    Write-Host @"

La CLI gh n'est pas connectée. La clé encodée est dans ton presse-papiers.
Crée les secrets à la main (https://github.com/$Repo/settings/secrets/actions) :

  WIDO_KEYSTORE_BASE64    Ctrl+V (presse-papiers)
  WIDO_KEYSTORE_PASSWORD  le mot de passe que tu viens de saisir
  WIDO_KEY_ALIAS          wido
  WIDO_KEY_PASSWORD       le même mot de passe
"@
}

Write-Host ""
Write-Host "IMPORTANT : sauvegarde $ks et ton mot de passe dans un gestionnaire de mots de passe."
Write-Host "Sans eux, impossible de publier une mise à jour installable par-dessus l'existante."
