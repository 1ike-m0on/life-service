<#
.SYNOPSIS
Imports generated public-dataset CSVs into the local MySQL schema.

.DESCRIPTION
Loads normalized merchant and merchant-note CSV files produced by the existing
public dataset converters into ls_merchant and ls_merchant_note.

The script defaults to a non-destructive import: matching primary keys are
replaced, but existing rows outside the imported id ranges are not removed.
Use -ClearImportedRange explicitly when a clean rerun is required.
#>
[CmdletBinding()]
param(
    [string] $MerchantCsv = "",

    [string] $NoteCsv = "",

    [string] $FavoriteCsv = "",

    [string] $ValidationSqlPath = "",

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

    [ValidateNotNullOrEmpty()]
    [string] $ContainerImportDirectory = "/tmp/life-service-public-import",

    [switch] $UseHostMysql,

    [switch] $ClearImportedRange,

    [switch] $ValidateAfter,

    [switch] $DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDirectory = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ScriptDirectory)) {
    $ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
}

if ([string]::IsNullOrWhiteSpace($MerchantCsv)) {
    $MerchantCsv = Join-Path $ScriptDirectory "..\generated\googlelocal-vt-merchants.csv"
}
if ([string]::IsNullOrWhiteSpace($NoteCsv)) {
    $NoteCsv = Join-Path $ScriptDirectory "..\generated\googlelocal-vt-notes-100k.csv"
}

function Resolve-ExistingPath {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "File was not found: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).ProviderPath
}

function ConvertTo-MySqlLiteral {
    param([Parameter(Mandatory = $true)][string] $Value)

    return "'" + ($Value -replace "\\", "\\" -replace "'", "''") + "'"
}

function Get-MySqlArguments {
    if ($UseHostMysql) {
        $args = @(
            "--local-infile=1",
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
        "--local-infile=1",
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

function Invoke-MySql {
    param([Parameter(Mandatory = $true)][string] $Sql)

    if ($DryRun) {
        Write-Host "DRY RUN: would execute SQL:"
        Write-Host $Sql
        return
    }

    $command = Get-MySqlArguments
    $Sql | & $command.FilePath @($command.Arguments)
    if ($LASTEXITCODE -ne 0) {
        throw "mysql command failed with exit code $LASTEXITCODE."
    }
}

function Copy-FileToMysqlClient {
    param(
        [Parameter(Mandatory = $true)][string] $SourcePath,
        [Parameter(Mandatory = $true)][string] $FileName
    )

    if ($UseHostMysql) {
        return ($SourcePath -replace "\\", "/")
    }

    $containerPath = "$ContainerImportDirectory/$FileName"

    if ($DryRun) {
        Write-Host "DRY RUN: would copy $SourcePath to ${MysqlContainer}:$containerPath"
        return $containerPath
    }

    & docker exec $MysqlContainer sh -c "mkdir -p '$ContainerImportDirectory'"
    if ($LASTEXITCODE -ne 0) {
        throw "creating import directory in MySQL container failed with exit code $LASTEXITCODE."
    }

    & docker cp $SourcePath "${MysqlContainer}:$containerPath"
    if ($LASTEXITCODE -ne 0) {
        throw "copying $SourcePath to MySQL container failed with exit code $LASTEXITCODE."
    }

    return $containerPath
}

$resolvedMerchantCsv = Resolve-ExistingPath $MerchantCsv
$resolvedNoteCsv = Resolve-ExistingPath $NoteCsv
$resolvedFavoriteCsv = ""
if (-not [string]::IsNullOrWhiteSpace($FavoriteCsv)) {
    $resolvedFavoriteCsv = Resolve-ExistingPath $FavoriteCsv
}

Write-Host "Merchant CSV: $resolvedMerchantCsv"
Write-Host "Note CSV:     $resolvedNoteCsv"
if (-not [string]::IsNullOrWhiteSpace($resolvedFavoriteCsv)) {
    Write-Host "Favorite CSV: $resolvedFavoriteCsv"
}
Write-Host "Target DB:    $MysqlDatabase"
if ($UseHostMysql) {
    Write-Host "MySQL client: host ${MysqlHost}:$MysqlPort"
}
else {
    Write-Host "MySQL client: container $MysqlContainer"
}

$merchantLoadPath = Copy-FileToMysqlClient -SourcePath $resolvedMerchantCsv -FileName "merchants.csv"
$noteLoadPath = Copy-FileToMysqlClient -SourcePath $resolvedNoteCsv -FileName "merchant-notes.csv"
$favoriteLoadPath = ""
if (-not [string]::IsNullOrWhiteSpace($resolvedFavoriteCsv)) {
    $favoriteLoadPath = Copy-FileToMysqlClient -SourcePath $resolvedFavoriteCsv -FileName "note-favorites.csv"
}

$merchantPathSql = ConvertTo-MySqlLiteral $merchantLoadPath
$notePathSql = ConvertTo-MySqlLiteral $noteLoadPath
$favoriteLoadSql = ""
if (-not [string]::IsNullOrWhiteSpace($favoriteLoadPath)) {
    $favoritePathSql = ConvertTo-MySqlLiteral $favoriteLoadPath
    $favoriteLoadSql = @"

load data local infile $favoritePathSql
replace into table ls_note_favorite
character set utf8mb4
fields terminated by ',' optionally enclosed by '"'
lines terminated by X'0D0A'
ignore 1 lines
(
    id,
    user_id,
    note_id,
    status,
    created_at,
    updated_at
);

update ls_merchant_note note
left join (
    select note_id, count(*) as favorite_count
    from ls_note_favorite
    where note_id >= $ImportedNoteIdMin
      and status = 1
    group by note_id
) favorite on favorite.note_id = note.id
set note.favorite_count = coalesce(favorite.favorite_count, 0)
where note.id >= $ImportedNoteIdMin;
"@
}

$clearSql = ""
if ($ClearImportedRange) {
    $clearSql = @"
delete from ls_note_favorite where note_id >= $ImportedNoteIdMin;
delete from ls_note_comment where note_id >= $ImportedNoteIdMin;
delete from ls_merchant_note where id >= $ImportedNoteIdMin;
delete from ls_merchant where id >= $ImportedMerchantIdMin;
"@
}

$sql = @"
set global local_infile = 1;
set session foreign_key_checks = 0;
$clearSql
load data local infile $merchantPathSql
replace into table ls_merchant
character set utf8mb4
fields terminated by ',' optionally enclosed by '"'
lines terminated by X'0D0A'
ignore 1 lines
(
    id,
    category_id,
    name,
    @images,
    @area,
    address,
    @longitude,
    @latitude,
    @avg_price_cent,
    sold_count,
    comment_count,
    score,
    @open_hours,
    status,
    created_at,
    updated_at
)
set
    images = nullif(@images, ''),
    area = nullif(@area, ''),
    longitude = nullif(@longitude, ''),
    latitude = nullif(@latitude, ''),
    avg_price_cent = nullif(@avg_price_cent, ''),
    open_hours = nullif(@open_hours, '');

load data local infile $notePathSql
replace into table ls_merchant_note
character set utf8mb4
fields terminated by ',' optionally enclosed by '"'
lines terminated by X'0D0A'
ignore 1 lines
(
    id,
    user_id,
    merchant_id,
    @order_id,
    title,
    content,
    @rating,
    @images,
    like_count,
    comment_count,
    favorite_count,
    status,
    created_at,
    updated_at
)
set
    order_id = nullif(@order_id, ''),
    rating = nullif(@rating, ''),
    images = nullif(@images, '');
$favoriteLoadSql

set session foreign_key_checks = 1;

select 'imported_merchants' as metric, count(*) as value
from ls_merchant
where id >= $ImportedMerchantIdMin;

select 'imported_notes' as metric, count(*) as value
from ls_merchant_note
where id >= $ImportedNoteIdMin;

select 'imported_favorites' as metric, count(*) as value
from ls_note_favorite
where note_id >= $ImportedNoteIdMin;
"@

Invoke-MySql -Sql $sql

if ($ValidateAfter) {
    $validationScript = Join-Path $ScriptDirectory "Test-PublicDatasetMysql.ps1"
    $validationArgs = @{
        ImportedMerchantIdMin = $ImportedMerchantIdMin
        ImportedNoteIdMin = $ImportedNoteIdMin
        ImportedFavoriteIdMin = $ImportedFavoriteIdMin
        ExpectedMerchantCount = $ExpectedMerchantCount
        ExpectedNoteCount = $ExpectedNoteCount
        ExpectedFavoriteCount = $ExpectedFavoriteCount
        MysqlContainer = $MysqlContainer
        MysqlHost = $MysqlHost
        MysqlPort = $MysqlPort
        MysqlDatabase = $MysqlDatabase
        MysqlUser = $MysqlUser
        MysqlPassword = $MysqlPassword
    }
    if ($UseHostMysql) {
        $validationArgs.UseHostMysql = $true
    }
    if ($DryRun) {
        $validationArgs.DryRun = $true
    }
    if (-not [string]::IsNullOrWhiteSpace($ValidationSqlPath)) {
        $validationArgs.SqlPath = $ValidationSqlPath
    }
    & $validationScript @validationArgs
}
