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

function Wait-ForDnsResolution {
    param(
        [string]$Hostname,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null
    $dnsServers = @($null, "1.1.1.1", "8.8.8.8")

    while ((Get-Date) -lt $deadline) {
        foreach ($dnsServer in $dnsServers) {
            try {
                $params = @{
                    Name = $Hostname
                    ErrorAction = "Stop"
                }
                if ($dnsServer) {
                    $params.Server = $dnsServer
                }

                $result = Resolve-DnsName @params
                if ($result) {
                    return $result
                }
            }
            catch {
                $lastError = $_
            }
        }

        Start-Sleep -Milliseconds 1000
    }

    if ($null -ne $lastError) {
        throw $lastError
    }

    throw "Timed out waiting for DNS resolution: $Hostname"
}

function Get-ResolvedAddresses {
    param([string]$Hostname)

    $records = Wait-ForDnsResolution -Hostname $Hostname -TimeoutSeconds 5
    return $records |
        Where-Object { $_.Type -eq "A" -and $_.IPAddress } |
        Select-Object -ExpandProperty IPAddress -Unique
}

function Wait-ForPublicHealth {
    param(
        [string]$Uri,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastError = $null
    $uriObject = [Uri]$Uri
    $hostname = $uriObject.Host

    while ((Get-Date) -lt $deadline) {
        try {
            return Invoke-RestMethod -Method Get -Uri $Uri -TimeoutSec 5
        }
        catch {
            $lastError = $_

            try {
                $addresses = Get-ResolvedAddresses -Hostname $hostname

                if ($addresses -and (Get-Command "curl.exe" -ErrorAction SilentlyContinue)) {
                    foreach ($address in $addresses) {
                        $resolveArg = "${hostname}:$($uriObject.Port):$address"
                        $response = curl.exe --silent --show-error --fail --connect-timeout 5 --max-time 8 --resolve $resolveArg $Uri
                        if ($LASTEXITCODE -eq 0 -and -not [string]::IsNullOrWhiteSpace($response)) {
                            return $response | ConvertFrom-Json
                        }
                    }
                }
            }
            catch {
                $lastError = $_
            }

            Start-Sleep -Milliseconds 700
        }
    }

    if ($null -ne $lastError) {
        throw $lastError
    }

    throw "Timed out waiting for public health endpoint: $Uri"
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
        $cloudflaredPid = $info.cloudflaredPid
        if ($cloudflaredPid) {
            $process = Get-Process -Id $cloudflaredPid -ErrorAction SilentlyContinue
            if ($process) {
                Stop-Process -Id $cloudflaredPid -Force -ErrorAction Stop
                Write-Host "Stopped previous Cloudflare tunnel PID $cloudflaredPid"
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

    $tunnelHostname = ([Uri]$tunnelUrl).Host
    Write-Host "Waiting for public DNS propagation for $tunnelHostname..."
    Wait-ForDnsResolution -Hostname $tunnelHostname -TimeoutSeconds ([Math]::Max($TunnelTimeoutSeconds, 60)) | Out-Null

    $env:PUBLIC_BASE_URL = $tunnelUrl
    Write-Host "Recreating image server with updated PUBLIC_BASE_URL..."
    docker compose -f $composeFile up -d --force-recreate | Out-Null

    $health = Wait-ForHealth -Uri "http://localhost:8080/health" -TimeoutSeconds 40
    Start-Sleep -Seconds 2
    $publicHealth = Wait-ForPublicHealth -Uri "$tunnelUrl/health" -TimeoutSeconds ([Math]::Max($TunnelTimeoutSeconds, 60))

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
