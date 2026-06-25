<#
.SYNOPSIS
Holds the flash-sale voucher stock row lock for a bounded time window.

.DESCRIPTION
Starts one MySQL transaction, locks ls_flash_sale_voucher for a single voucher
with SELECT ... FOR UPDATE, sleeps, then rolls back. Use a second terminal to
run load while the lock is held.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [ValidateRange(1, 3600)]
    [int] $HoldSeconds = 60,

    [ValidateNotNullOrEmpty()]
    [string] $MysqlContainer = "life-service-mysql",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlDatabase = "life_service",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlUser = "root",

    [AllowEmptyString()]
    [string] $MysqlPassword = "root",

    [string] $SqlPath = "",

    [switch] $Force,

    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($SqlPath)) {
    $SqlPath = Join-Path $PSScriptRoot "..\..\load\sql\hold-flash-sale-voucher-row-lock.sql"
}

function Format-CommandLine {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    $parts = @($FilePath) + ($Arguments | ForEach-Object {
        if ($_ -match "\s") { '"' + $_ + '"' } else { $_ }
    })
    return $parts -join " "
}

$resolvedSqlPath = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($SqlPath)
if (-not (Test-Path -LiteralPath $resolvedSqlPath)) {
    throw "SQL file was not found: $resolvedSqlPath"
}

$mysqlArgs = @("exec", "-i", $MysqlContainer, "mysql", "--table", "-u$MysqlUser")
if (-not [string]::IsNullOrEmpty($MysqlPassword)) {
    $mysqlArgs += "-p$MysqlPassword"
}
$mysqlArgs += $MysqlDatabase

$sql = @"
set @lock_voucher_id := $VoucherId;
set @lock_hold_seconds := $HoldSeconds;
"@ + "`n" + (Get-Content -Raw -LiteralPath $resolvedSqlPath)

if ($DryRun) {
    Write-Host "DRY RUN: would hold row lock on voucher $VoucherId for $HoldSeconds second(s)."
    Write-Host "DRY RUN: $(Format-CommandLine -FilePath "docker" -Arguments $mysqlArgs)"
    return
}

if (-not $Force) {
    $answer = Read-Host "This will hold a MySQL row lock for $HoldSeconds second(s). Type LOCK to continue"
    if ($answer -ne "LOCK") {
        Write-Host "Canceled."
        return
    }
}

$sql | & docker @mysqlArgs
if ($LASTEXITCODE -ne 0) {
    throw "Holding MySQL row lock failed with exit code $LASTEXITCODE."
}

Write-Host "Row lock window finished for voucher $VoucherId."

