param(
    [int]$TunnelTimeoutSeconds = 30
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $repoRoot "docker-compose.image-server.yml"
$localPropertiesPath = Join-Path $repoRoot "local.properties"
$tunnelInfoPath = Join-Path $repoRoot "scripts\.cloudflare-tunnel.json"
$runStamp = Get-Date -Format "yyyyMMdd-HHmmss"
$cloudflaredLogPath = Join-Path $env:TEMP "pintxomatch-cloudflared-$runStamp.log"
$cloudflaredErrLogPath = Join-Path $env:TEMP "pintxomatch-cloudflared-$runStamp.err.log"

function Assert-Command {
    param([string]$Name)

    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Set-Or-AppendProperty {
    param(
        [string]$FilePath,
        [string]$Key,
        [string]$Value
    )

    if (-not (Test-Path $FilePath)) {
        Set-Content -Path $FilePath -Value "$Key=$Value"
        return
    }

    $content = Get-Content -Path $FilePath -Raw
    $pattern = "(?m)^" + [Regex]::Escape($Key) + "=.*$"

    if ($content -match $pattern) {
        $updated = [Regex]::Replace($content, $pattern, "$Key=$Value")
        Set-Content -Path $FilePath -Value $updated
        return
    }

    if (-not $content.EndsWith("`n")) {
        $content += "`r`n"
    }

    $content += "$Key=$Value`r`n"
    Set-Content -Path $FilePath -Value $content
}

function Wait-ForTunnelUrl {
    param(
        [string]$OutLogPath,
        [string]$ErrLogPath,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $regex = "https://[a-z0-9-]+\.trycloudflare\.com"

    while ((Get-Date) -lt $deadline) {
        foreach ($path in @($OutLogPath, $ErrLogPath)) {
            if (Test-Path $path) {
                $raw = Get-Content -Path $path -Raw -ErrorAction SilentlyContinue
                if ([string]::IsNullOrWhiteSpace($raw)) {
                    continue
                }

                $match = [Regex]::Match($raw, $regex)
                if ($match.Success) {
                    return $match.Value
                }
            }
        }

        Start-Sleep -Milliseconds 400
    }

    throw "Timed out waiting for Cloudflare tunnel URL in logs: $OutLogPath, $ErrLogPath"
}

function Wait-ForHealth {
    param(
        [string]$Uri,
        [int]$TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null

    while ((Get-Date) -lt $deadline) {
        try {
            return Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 5
        }
        catch {
            $lastError = $_
            Start-Sleep -Milliseconds 700
        }
    }

    if ($null -ne $lastError) {
        throw $lastError
    }

    throw "Timed out waiting for health endpoint: $Uri"
}

function Get-PropertyValue {
    param(
        [string]$FilePath,
        [string]$Key
    )

    if (-not (Test-Path $FilePath)) {
        return $null
    }

    $pattern = "(?m)^" + [Regex]::Escape($Key) + "=(.*)$"
    $content = Get-Content -Path $FilePath -Raw -ErrorAction SilentlyContinue
    $match = [Regex]::Match($content, $pattern)
    if ($match.Success) {
        return $match.Groups[1].Value.Trim()
    }

    return $null
}

function Stop-PreviousTunnel {
    param([string]$TunnelInfoFilePath)

    if (-not (Test-Path $TunnelInfoFilePath)) {
        return
    }

    try {
        $info = Get-Content -Path $TunnelInfoFilePath -Raw | ConvertFrom-Json
        $pid = $info.cloudflaredPid
        if ($pid) {
            $process = Get-Process -Id $pid -ErrorAction SilentlyContinue
            if ($process) {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Write-Host "Stopped previous Cloudflare tunnel PID $pid"
            }
        }
    }
    catch {
        Write-Warning "Could not stop previous Cloudflare tunnel from $TunnelInfoFilePath"
    }
}

try {
    Assert-Command -Name "docker"
    Assert-Command -Name "cloudflared"

    if (-not (Test-Path $composeFile)) {
        throw "Could not find compose file at $composeFile"
    }

    Write-Host "Starting image server container..."
    docker compose -f $composeFile up -d | Out-Null

    $previousLocalImageBaseUrl = Get-PropertyValue -FilePath $localPropertiesPath -Key "LOCAL_IMAGE_BASE_URL"
    Stop-PreviousTunnel -TunnelInfoFilePath $tunnelInfoPath

    Write-Host "Starting Cloudflare tunnel..."
    $cloudflaredArgs = @(
        "tunnel"
        "--protocol"
        "http2"
        "--url"
        "http://localhost:8080"
    )
    $cloudflaredProcess = Start-Process -FilePath "cloudflared" -ArgumentList $cloudflaredArgs -RedirectStandardOutput $cloudflaredLogPath -RedirectStandardError $cloudflaredErrLogPath -PassThru

    $tunnelUrl = Wait-ForTunnelUrl -OutLogPath $cloudflaredLogPath -ErrLogPath $cloudflaredErrLogPath -TimeoutSeconds $TunnelTimeoutSeconds

    Write-Host "Tunnel URL detected: $tunnelUrl"

    $env:PUBLIC_BASE_URL = $tunnelUrl
    Write-Host "Recreating image server with updated PUBLIC_BASE_URL..."
    docker compose -f $composeFile up -d --force-recreate | Out-Null

    $health = Wait-ForHealth -Uri "http://localhost:8080/health" -TimeoutSeconds 40
    $publicHealth = Wait-ForHealth -Uri "$tunnelUrl/health" -TimeoutSeconds 40

    Set-Or-AppendProperty -FilePath $localPropertiesPath -Key "LOCAL_IMAGE_BASE_URL" -Value $tunnelUrl

    @{
        tunnelUrl = $tunnelUrl
        cloudflaredPid = $cloudflaredProcess.Id
        logPath = $cloudflaredLogPath
        errLogPath = $cloudflaredErrLogPath
        startedAt = (Get-Date).ToString("o")
    } | ConvertTo-Json | Set-Content -Path $tunnelInfoPath

    Write-Host ""
    Write-Host "Ready."
    Write-Host "- LOCAL_IMAGE_BASE_URL updated in local.properties"
    Write-Host "- PUBLIC_BASE_URL set in image server container"
    Write-Host "- Health check: $($health.status)"
    Write-Host "- Public health check: $($publicHealth.status)"
    Write-Host "- cloudflared PID: $($cloudflaredProcess.Id)"
    Write-Host "- Log file: $cloudflaredLogPath"
    Write-Host "- Error log file: $cloudflaredErrLogPath"
    Write-Host ""
    Write-Host "Rebuild and run your Android app now so BuildConfig picks up the latest URL."
}
catch {
    if ($previousLocalImageBaseUrl) {
        Set-Or-AppendProperty -FilePath $localPropertiesPath -Key "LOCAL_IMAGE_BASE_URL" -Value $previousLocalImageBaseUrl
    }
    Write-Error $_
    exit 1
}
