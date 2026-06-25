<#
.SYNOPSIS
Deletes Redis hot-path keys for one flash-sale voucher.

.DESCRIPTION
Use this to verify fail-closed behavior when Redis warmup data disappears.
The script defaults to dry-run. Pass -Apply and confirm, or pass -Force, to
delete keys.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [ValidateNotNullOrEmpty()]
    [string] $RedisContainer = "life-service-redis",

    [switch] $IncludeRateLimitKeys,

    [switch] $DryRun,

    [switch] $Force,

    [switch] $Apply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$shouldDelete = ($Apply -or $Force) -and (-not $DryRun)

if ($shouldDelete -and (-not $Force)) {
    $answer = Read-Host "Type DELETE to remove Redis keys for voucher $VoucherId"
    if ($answer -ne "DELETE") {
        Write-Host "Canceled."
        return
    }
}

function Invoke-Docker {
    param([string[]] $Arguments)
    & docker @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed with exit code $LASTEXITCODE."
    }
}

function Remove-Keys {
    param([string[]] $Keys)

    if ($Keys.Count -eq 0) {
        return
    }

    if (-not $shouldDelete) {
        Write-Host "DRY RUN: would delete Redis keys:"
        $Keys | ForEach-Object { Write-Host " - $_" }
        return
    }

    Invoke-Docker (@("exec", $RedisContainer, "redis-cli", "DEL") + $Keys)
}

$hotKeys = @(
    "life:cache:flash-sale-voucher:$VoucherId",
    "life:flash:voucher:stock:$VoucherId",
    "life:flash:voucher:users:$VoucherId",
    "life:flash:voucher:released-orders:$VoucherId"
)

Remove-Keys $hotKeys

if ($IncludeRateLimitKeys) {
    if (-not $shouldDelete) {
        Write-Host "DRY RUN: would scan and delete Redis keys matching life:rate:flash-sale:order:*"
        return
    }

    $keys = @(& docker exec $RedisContainer redis-cli --scan --pattern "life:rate:flash-sale:order:*")
    if ($LASTEXITCODE -ne 0) {
        throw "Redis scan failed with exit code $LASTEXITCODE."
    }

    $keys = @($keys | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    for ($i = 0; $i -lt $keys.Count; $i += 100) {
        $last = [Math]::Min($i + 99, $keys.Count - 1)
        Remove-Keys @($keys[$i..$last])
    }
}

Write-Host "Flash-sale hot-key fault prepared for voucher $VoucherId."
