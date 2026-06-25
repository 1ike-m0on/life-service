<#
.SYNOPSIS
Prints Redis and MySQL checks for flash-sale fault-injection runs.

.DESCRIPTION
Queries Redis stock, Redis users set size, ready marker membership, MySQL order
count, duplicate user order count, and order status counts for one voucher.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [ValidateNotNullOrEmpty()]
    [string] $RedisContainer = "life-service-redis",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlContainer = "life-service-mysql",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlDatabase = "life_service",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlUser = "root",

    [AllowEmptyString()]
    [string] $MysqlPassword = "root",

    [string] $SqlPath = "",

    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($SqlPath)) {
    $SqlPath = Join-Path $PSScriptRoot "..\..\load\sql\flash-sale-fault-injection-checks.sql"
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

function Invoke-RedisCli {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $RedisArguments,

        [Parameter(Mandatory = $true)]
        [string] $Label
    )

    $arguments = @("exec", $RedisContainer, "redis-cli") + $RedisArguments
    if ($DryRun) {
        Write-Host "DRY RUN: $(Format-CommandLine -FilePath "docker" -Arguments $arguments)"
        return
    }

    Write-Host ""
    Write-Host $Label
    Invoke-CheckedNativeCommand -FilePath "docker" -Arguments $arguments -Action $Label
}

$stockKey = "life:flash:voucher:stock:$VoucherId"
$usersKey = "life:flash:voucher:users:$VoucherId"

Write-Host "Voucher: $VoucherId"

Invoke-RedisCli -RedisArguments @("GET", $stockKey) -Label "Redis stock: $stockKey"
Invoke-RedisCli -RedisArguments @("SCARD", $usersKey) -Label "Redis users SCARD: $usersKey"
Invoke-RedisCli -RedisArguments @("SISMEMBER", $usersKey, "__READY__") -Label "Redis users ready marker: $usersKey"

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
set @check_voucher_id := $VoucherId;
"@ + "`n" + (Get-Content -Raw -LiteralPath $resolvedSqlPath)

Write-Host ""
Write-Host "MySQL checks: $resolvedSqlPath"
if ($DryRun) {
    Write-Host "DRY RUN: $(Format-CommandLine -FilePath "docker" -Arguments $mysqlArgs)"
    return
}

$sql | & docker @mysqlArgs
if ($LASTEXITCODE -ne 0) {
    throw "MySQL acceptance checks failed with exit code $LASTEXITCODE."
}

