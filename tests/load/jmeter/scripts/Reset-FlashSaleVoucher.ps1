<#
.SYNOPSIS
Resets one flash-sale voucher for repeatable load-test scenarios.

.DESCRIPTION
Runs the SQL reset with caller-provided voucher and stock values, clears Redis
hot-path keys, clears flash-sale rate-limit keys, and optionally warms Redis
again through the backend warmup endpoint.

.PARAMETER VoucherId
Flash-sale voucher id to reset.

.PARAMETER Stock
MySQL stock value to set before warmup. Use 0 for sold-out fast-failure runs.

.PARAMETER FailClosed
Delete Redis hot-path keys and skip warmup so the order endpoint should return
FLASH_SALE_NOT_READY without falling back to MySQL.

.PARAMETER KeepRateLimitKeys
Preserve existing flash-sale rate-limit keys. By default they are cleared so
scenario runs do not inherit throttling from a previous run.

.PARAMETER DryRun
Print the reset actions without changing MySQL, Redis, or the backend.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $Stock = 12000,

    [ValidateNotNullOrEmpty()]
    [string] $BaseUrl = "http://localhost:8081",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlContainer = "life-service-mysql",

    [ValidateNotNullOrEmpty()]
    [string] $RedisContainer = "life-service-redis",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlDatabase = "life_service",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlUser = "root",

    [AllowEmptyString()]
    [string] $MysqlPassword = "root",

    [string] $SqlPath = "",

    [switch] $SkipMysql,

    [switch] $SkipRedis,

    [switch] $SkipWarmup,

    [switch] $FailClosed,

    [switch] $KeepRateLimitKeys,

    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($SqlPath)) {
    $SqlPath = Join-Path $PSScriptRoot "..\..\sql\reset-flash-sale-voucher.sql"
}

function Get-NormalizedBaseUrl {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Value
    )

    $parsed = $null
    if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref] $parsed)) {
        throw "BaseUrl must be an absolute HTTP or HTTPS URL. Received: $Value"
    }

    if ($parsed.Scheme -notin @("http", "https")) {
        throw "BaseUrl must use http or https. Received scheme: $($parsed.Scheme)"
    }

    return $Value.TrimEnd("/")
}

function Invoke-CheckedNativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $Action
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE."
    }
}

function Remove-RedisKeys {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RedisContainerName,

        [Parameter(Mandatory = $true)]
        [string[]] $Keys,

        [Parameter(Mandatory = $true)]
        [string] $Description
    )

    if ($Keys.Count -eq 0) {
        return
    }

    if ($DryRun) {
        Write-Host "DRY RUN: would delete $($Keys.Count) Redis keys for $Description."
        return
    }

    $arguments = @("exec", $RedisContainerName, "redis-cli", "DEL") + $Keys
    Invoke-CheckedNativeCommand -FilePath "docker" -Arguments $arguments -Action "Deleting Redis keys for $Description" | Out-Null
}

function Remove-RedisKeysByPattern {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RedisContainerName,

        [Parameter(Mandatory = $true)]
        [string] $Pattern,

        [Parameter(Mandatory = $true)]
        [string] $Description
    )

    if ($DryRun) {
        Write-Host "DRY RUN: would scan and delete Redis keys matching '$Pattern' for $Description."
        return
    }

    $keys = @(& docker exec $RedisContainerName redis-cli --scan --pattern $Pattern)
    if ($LASTEXITCODE -ne 0) {
        throw "Scanning Redis keys for $Description failed with exit code $LASTEXITCODE."
    }

    $keys = @($keys | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    if ($keys.Count -eq 0) {
        Write-Host "No Redis keys matched '$Pattern' for $Description."
        return
    }

    for ($i = 0; $i -lt $keys.Count; $i += 100) {
        $last = [Math]::Min($i + 99, $keys.Count - 1)
        $chunk = @($keys[$i..$last])
        Remove-RedisKeys -RedisContainerName $RedisContainerName -Keys $chunk -Description $Description
    }
}

$normalizedBaseUrl = Get-NormalizedBaseUrl -Value $BaseUrl
$resolvedSqlPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($SqlPath)
if (-not (Test-Path -LiteralPath $resolvedSqlPath)) {
    throw "SQL reset file was not found: $resolvedSqlPath"
}

$skipWarmupEffective = $SkipWarmup -or $FailClosed

if (-not $SkipMysql) {
    $sql = @"
set @load_voucher_id := $VoucherId;
set @load_stock := $Stock;
"@ + "`n" + (Get-Content -Raw -LiteralPath $resolvedSqlPath)

    $mysqlArgs = @("exec", "-i", $MysqlContainer, "mysql", "-u$MysqlUser")
    if (-not [string]::IsNullOrEmpty($MysqlPassword)) {
        $mysqlArgs += "-p$MysqlPassword"
    }
    $mysqlArgs += $MysqlDatabase

    if ($DryRun) {
        Write-Host "DRY RUN: would reset MySQL voucher $VoucherId to stock $Stock using $resolvedSqlPath."
    }
    else {
        $sql | & docker @mysqlArgs
        if ($LASTEXITCODE -ne 0) {
            throw "MySQL flash-sale reset failed with exit code $LASTEXITCODE."
        }
        Write-Host "Reset MySQL voucher $VoucherId to stock $Stock."
    }
}

if (-not $SkipRedis) {
    $hotKeys = @(
        "life:cache:flash-sale-voucher:$VoucherId",
        "life:flash:voucher:stock:$VoucherId",
        "life:flash:voucher:users:$VoucherId",
        "life:flash:voucher:released-orders:$VoucherId"
    )
    Remove-RedisKeys -RedisContainerName $RedisContainer -Keys $hotKeys -Description "flash-sale voucher $VoucherId"

    if (-not $KeepRateLimitKeys) {
        Remove-RedisKeysByPattern `
            -RedisContainerName $RedisContainer `
            -Pattern "life:rate:flash-sale:order:*" `
            -Description "flash-sale order rate limits"
    }
}

if ($skipWarmupEffective) {
    Write-Host "Skipped backend warmup for voucher $VoucherId."
    return
}

$warmupUrl = "$normalizedBaseUrl/api/v1/flash-sale-vouchers/$VoucherId/warmup"
if ($DryRun) {
    Write-Host "DRY RUN: would POST $warmupUrl."
    return
}

Invoke-RestMethod -Method Post -Uri $warmupUrl | Out-Null
Write-Host "Warmed Redis hot keys for voucher $VoucherId."
