[CmdletBinding()]
param(
    [string]$ConfigPath = (Join-Path $PSScriptRoot "ssh-sandbox.psd1")
)

$ErrorActionPreference = "Stop"
$config = Import-PowerShellDataFile -LiteralPath $ConfigPath

foreach ($commandName in @("osb", "docker", "ssh-keygen", "ssh")) {
    if (-not (Get-Command $commandName -ErrorAction SilentlyContinue)) {
        throw "$commandName is required but was not found in PATH."
    }
}

$userHome = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
$privateKeyPath = Join-Path $userHome $config.PrivateKey
$publicKeyPath = Join-Path $userHome $config.PublicKey
$keyDirectory = Split-Path -Parent $privateKeyPath

New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null

if (-not (Test-Path -LiteralPath $privateKeyPath -PathType Leaf) -and
    -not (Test-Path -LiteralPath $publicKeyPath -PathType Leaf)) {
    & ssh-keygen -q -t ed25519 -N "" -f $privateKeyPath -C "dws-sandbox"
    if ($LASTEXITCODE -ne 0) {
        throw "ssh-keygen failed while creating the dedicated sandbox key."
    }
}
elseif ((Test-Path -LiteralPath $privateKeyPath -PathType Leaf) -and
        -not (Test-Path -LiteralPath $publicKeyPath -PathType Leaf)) {
    $derivedPublicKey = (& ssh-keygen -y -f $privateKeyPath | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($derivedPublicKey)) {
        throw "Could not derive dws_sandbox.pub from the existing private key."
    }
    [System.IO.File]::WriteAllText($publicKeyPath, "$derivedPublicKey`n")
}
elseif (-not (Test-Path -LiteralPath $privateKeyPath -PathType Leaf)) {
    throw "dws_sandbox.pub exists but its matching private key is missing."
}

$privateFingerprint = ((& ssh-keygen -lf $privateKeyPath | Out-String).Trim() -split "\s+")[1]
$publicFingerprint = ((& ssh-keygen -lf $publicKeyPath | Out-String).Trim() -split "\s+")[1]
if ([string]::IsNullOrWhiteSpace($privateFingerprint) -or
    $privateFingerprint -ne $publicFingerprint) {
    throw "dws_sandbox and dws_sandbox.pub are inconsistent."
}

# Validate the lifecycle server and avoid creating a sandbox that cannot claim
# the fixed local SSH port.
& osb sandbox list -o json *> $null
if ($LASTEXITCODE -ne 0) {
    throw "The OpenSandbox server is not reachable. Start agent-sandbox/start-opensandbox.ps1 first."
}

$portOwnerOutput = & docker ps --filter "publish=$($config.SshPort)/tcp" --format "{{.Names}}" | Select-Object -First 1
$portOwner = if ($portOwnerOutput) { $portOwnerOutput.ToString().Trim() } else { "" }
if (-not [string]::IsNullOrWhiteSpace($portOwner)) {
    $forwarderPrefix = "dws-ssh-forward-"
    if ($portOwner.StartsWith($forwarderPrefix)) {
        $oldSandboxId = $portOwner.Substring($forwarderPrefix.Length)
        & docker inspect "sandbox-$oldSandboxId" *> $null
        if ($LASTEXITCODE -ne 0) {
            & docker rm --force $portOwner *> $null
        }
        else {
            throw "SSH port $($config.SshPort) is already in use by active bridge $portOwner"
        }
    }
    else {
        throw "SSH port $($config.SshPort) is already in use by: $portOwner"
    }
}

$sandboxId = $null
$bridgeName = $null
try {
    $createArgs = @(
        "sandbox", "create",
        "--image", [string]$config.Image,
        "--timeout", [string]$config.Timeout,
        "--skip-health-check",
        "--resource", "cpu=$($config.Cpu)",
        "--resource", "memory=$($config.Memory)",
        "-o", "json"
    )
    foreach ($entrypointItem in $config.EntryPoint) {
        $createArgs += @("--entrypoint", [string]$entrypointItem)
    }

    $createJson = (& osb @createArgs | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "OpenSandbox sandbox creation failed."
    }
    $created = $createJson | ConvertFrom-Json
    $sandboxId = if ($created.sandbox_id) {
        [string]$created.sandbox_id
    }
    else {
        [string]$created.id
    }
    if ([string]::IsNullOrWhiteSpace($sandboxId)) {
        throw "OpenSandbox did not return a sandbox ID."
    }

    $containerName = "sandbox-$sandboxId"
    $containerReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & docker inspect $containerName *> $null
        if ($LASTEXITCODE -eq 0) {
            $containerReady = $true
            break
        }
        Start-Sleep -Milliseconds 250
    }
    if (-not $containerReady) {
        throw "Docker container $containerName did not appear."
    }

    # Do not print the key; stream it directly into the sandbox.
    Get-Content -Raw -LiteralPath $publicKeyPath |
        & docker exec -i $containerName sh -c "cat > /root/.ssh/authorized_keys"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not provision the sandbox public key."
    }
    & docker exec $containerName sh -lc "chown -R root:root /root/.ssh; chmod 700 /root/.ssh; chmod 600 /root/.ssh/authorized_keys"
    if ($LASTEXITCODE -ne 0) {
        throw "Could not secure the sandbox SSH directory."
    }

    $sandboxIp = (& docker inspect --format "{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}" $containerName).Trim()
    if ([string]::IsNullOrWhiteSpace($sandboxIp)) {
        throw "Could not determine the sandbox Docker IP address."
    }

    $bridgeName = "dws-ssh-forward-$sandboxId"
    & docker run --detach `
        --name $bridgeName `
        --publish "$($config.SshHost):$($config.SshPort):$($config.BridgePort)" `
        --network $config.Network `
        $config.BridgeImage `
        "TCP-LISTEN:$($config.BridgePort),fork,reuseaddr" `
        "TCP:${sandboxIp}:22" *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create the localhost SSH bridge."
    }

    $sshReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        & ssh `
            -o BatchMode=yes `
            -o ConnectTimeout=2 `
            -o StrictHostKeyChecking=no `
            -o UserKnownHostsFile=NUL `
            -i $privateKeyPath `
            -p $config.SshPort `
            "root@$($config.SshHost)" `
            "true" *> $null
        if ($LASTEXITCODE -eq 0) {
            $sshReady = $true
            break
        }
        Start-Sleep -Milliseconds 500
    }
    if (-not $sshReady) {
        throw "The SSH bridge did not accept a key-only SSH connection."
    }

    Write-Host "Sandbox created: $sandboxId"
    Write-Host "SSH: $($config.SshHost):$($config.SshPort)"
    Write-Host "Identity: $privateKeyPath"
    Write-Host "Orca username: root"
}
catch {
    if ($bridgeName) {
        & docker rm --force $bridgeName *> $null
    }
    if ($sandboxId) {
        & osb sandbox kill $sandboxId *> $null
    }
    throw
}
