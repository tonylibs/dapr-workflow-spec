param(
    # Bind mount the host Docker Desktop socket into every sandbox this server creates, so
    # the sandbox's Docker CLI drives the host daemon (docker pull/run/build). This is
    # host-root-equivalent access and bypasses the profile's capability drops; leave it off
    # unless the sandboxes need Docker.
    [switch]$MountDockerSocket,
    # Everything else is passed through to opensandbox-server unchanged.
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ServerArgs = @()
)

$ErrorActionPreference = "Stop"

# OpenSandbox's TOML parser intentionally does not expand environment variables,
# while Docker bind mounts on Windows need an absolute host path. Materialize a
# per-process config so the checked-in profile stays portable and only the host
# kubeconfig file (and, on request, the Docker socket) is exposed to sandbox containers.
$scriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$templatePath = Join-Path $scriptDirectory "opensandbox\docker.toml"
$userHome = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
$kubeconfigPath = Join-Path $userHome ".kube\config"

if (-not (Test-Path -LiteralPath $kubeconfigPath -PathType Leaf)) {
    throw "Host kubeconfig not found at $kubeconfigPath"
}

$absoluteKubeconfigPath = (Resolve-Path -LiteralPath $kubeconfigPath).Path.Replace("\", "/")
$binds = @("${absoluteKubeconfigPath}:/root/.kube/config:ro")
if ($MountDockerSocket) {
    # Docker Desktop maps this Linux path to its daemon socket for containers on every
    # backend (WSL 2, Hyper-V, macOS), so the same source path works from a Windows host.
    $binds += "/var/run/docker.sock:/var/run/docker.sock"
    Write-Warning "Sandboxes get full control of the host Docker daemon (-MountDockerSocket)."
}
$tomlBinds = ($binds | ForEach-Object { '"' + $_ + '"' }) -join ", "

$configText = Get-Content -LiteralPath $templatePath -Raw
if (-not $configText.Contains('"__DWS_SANDBOX_BINDS__"')) {
    throw "Bind marker not found in $templatePath"
}
$configText = $configText.Replace('"__DWS_SANDBOX_BINDS__"', $tomlBinds)

$generatedConfigPath = Join-Path ([System.IO.Path]::GetTempPath()) "dws-opensandbox-$PID.toml"
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($generatedConfigPath, $configText, $utf8NoBom)

try {
    if (-not $env:OPENSANDBOX_INSECURE_SERVER) {
        $env:OPENSANDBOX_INSECURE_SERVER = "YES"
    }
    & uvx opensandbox-server==0.2.3 --config $generatedConfigPath @ServerArgs
    exit $LASTEXITCODE
}
finally {
    Remove-Item -LiteralPath $generatedConfigPath -Force -ErrorAction SilentlyContinue
}
