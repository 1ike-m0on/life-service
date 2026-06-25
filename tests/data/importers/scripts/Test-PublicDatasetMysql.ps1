<#
.SYNOPSIS
Validates imported public-dataset rows in MySQL.

.DESCRIPTION
Runs row-count, orphan-reference, and representative EXPLAIN checks for the
S1 public dataset import.
#>
[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [string] $MysqlContainer = "life-service-mysql",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlHost = "127.0.0.1",

    [ValidateRange(1, 65535)]
    [int] $MysqlPort = 3307,

    [ValidateNotNullOrEmpty()]
    [string] $MysqlDatabase = "life_service",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlUser = "root",

    [AllowEmptyString()]
    [string] $MysqlPassword = "root",

    [ValidateRange(1, [long]::MaxValue)]
    [long] $ImportedMerchantIdMin = 100000,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $ImportedNoteIdMin = 200000,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $ImportedFavoriteIdMin = 300000,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $ExpectedMerchantCount = 0,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $ExpectedNoteCount = 0,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $ExpectedFavoriteCount = 0,

    [string] $SqlPath = "",

    [switch] $UseHostMysql,

    [switch] $DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDirectory = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ScriptDirectory)) {
    $ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if ([string]::IsNullOrWhiteSpace($SqlPath)) {
    $SqlPath = Join-Path $ScriptDirectory "..\..\..\load\sql\validate-public-dataset-s1.sql"
}

function Get-MySqlArguments {
    if ($UseHostMysql) {
        $args = @(
            "--default-character-set=utf8mb4",
            "-h", $MysqlHost,
            "-P", "$MysqlPort",
            "-u$MysqlUser"
        )
        if (-not [string]::IsNullOrEmpty($MysqlPassword)) {
            $args += "-p$MysqlPassword"
        }
        $args += $MysqlDatabase
        return @{
            FilePath = "mysql"
            Arguments = $args
        }
    }

    $args = @(
        "exec",
        "-i",
        $MysqlContainer,
        "mysql",
        "--default-character-set=utf8mb4",
        "-u$MysqlUser"
    )
    if (-not [string]::IsNullOrEmpty($MysqlPassword)) {
        $args += "-p$MysqlPassword"
    }
    $args += $MysqlDatabase
    return @{
        FilePath = "docker"
        Arguments = $args
    }
}

$resolvedSqlPath = (Resolve-Path -LiteralPath $SqlPath).ProviderPath
$sql = @"
set @load_merchant_id_min := $ImportedMerchantIdMin;
set @load_note_id_min := $ImportedNoteIdMin;
set @load_favorite_id_min := $ImportedFavoriteIdMin;
set @expected_s2_merchants := nullif($ExpectedMerchantCount, 0);
set @expected_s2_notes := nullif($ExpectedNoteCount, 0);
set @expected_s2_favorites := nullif($ExpectedFavoriteCount, 0);
"@ + "`n" + (Get-Content -Raw -LiteralPath $resolvedSqlPath)

if ($DryRun) {
    Write-Host "DRY RUN: would execute validation SQL from $resolvedSqlPath"
    Write-Host $sql
    return
}

$command = Get-MySqlArguments
$sql | & $command.FilePath @($command.Arguments)
if ($LASTEXITCODE -ne 0) {
    throw "mysql validation failed with exit code $LASTEXITCODE."
}
