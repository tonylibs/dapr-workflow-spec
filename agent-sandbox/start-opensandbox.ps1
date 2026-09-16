$ErrorActionPreference = "Stop"

# OpenSandbox's TOML parser intentionally does not expand environment variables,
# while Docker bind mounts on Windows need an absolute host path. Materialize a
# per-process config so the checked-in profile stays portable and only the host
# kubeconfig file is exposed to sandbox containers.
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$templatePath = Join-Path $scriptDirectory "opensandbox\docker.toml"
$userHome = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
$kubeconfigPath = Join-Path $userHome ".kube\config"

if (-not (Test-Path -LiteralPath $kubeconfigPath -PathType Leaf)) {
    throw "Host kubeconfig not found at $kubeconfigPath"
}

$absoluteKubeconfigPath = (Resolve-Path -LiteralPath $kubeconfigPath).Path.Replace("\", "/")
$configText = Get-Content -LiteralPath $templatePath -Raw
$configText = $configText.Replace("__DWS_HOST_KUBECONFIG__", $absoluteKubeconfigPath)

$generatedConfigPath = Join-Path ([System.IO.Path]::GetTempPath()) "dws-opensandbox-$PID.toml"
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($generatedConfigPath, $configText, $utf8NoBom)

try {
    & uvx opensandbox-server --config $generatedConfigPath @args
    exit $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $generatedConfigPath -Force -ErrorAction SilentlyContinue
}
