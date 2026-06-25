param(
    [string] $MerchantCsv = "",

    [string] $NoteCsv = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDirectory = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ScriptDirectory)) {
    $ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if ([string]::IsNullOrWhiteSpace($MerchantCsv)) {
    $MerchantCsv = Join-Path $ScriptDirectory "..\generated\merchants.csv"
}
if ([string]::IsNullOrWhiteSpace($NoteCsv)) {
    $NoteCsv = Join-Path $ScriptDirectory "..\generated\merchant-notes.csv"
}

function Assert-FileExists {
    param([string] $Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Expected file was not found: $Path"
    }
}

function Assert-Columns {
    param(
        [object[]] $Rows,
        [string[]] $Columns,
        [string] $FileName
    )

    if ($Rows.Count -eq 0) {
        throw "$FileName has no rows."
    }

    $available = @{}
    foreach ($property in $Rows[0].PSObject.Properties) {
        $available[$property.Name] = $true
    }

    foreach ($column in $Columns) {
        if (-not $available.ContainsKey($column)) {
            throw "$FileName is missing required column '$column'."
        }
    }
}

function Assert-IntegerRange {
    param(
        [object[]] $Rows,
        [string] $Column,
        [int] $Min,
        [int] $Max,
        [string] $FileName
    )

    $line = 1
    foreach ($row in $Rows) {
        $line++
        $value = "$($row.$Column)".Trim()
        if ($value.Length -eq 0) {
            continue
        }

        $parsed = 0
        if (-not [int]::TryParse($value, [ref] $parsed)) {
            throw "$FileName line $line has non-integer '$Column': $value"
        }
        if ($parsed -lt $Min -or $parsed -gt $Max) {
            throw "$FileName line $line has '$Column' outside ${Min}..${Max}: $value"
        }
    }
}

Assert-FileExists $MerchantCsv
Assert-FileExists $NoteCsv

$merchantRows = Import-Csv -LiteralPath $MerchantCsv
$noteRows = Import-Csv -LiteralPath $NoteCsv

Assert-Columns $merchantRows @(
    "id",
    "category_id",
    "name",
    "images",
    "area",
    "address",
    "longitude",
    "latitude",
    "avg_price_cent",
    "sold_count",
    "comment_count",
    "score",
    "open_hours",
    "status",
    "created_at",
    "updated_at"
) $MerchantCsv

Assert-Columns $noteRows @(
    "id",
    "user_id",
    "merchant_id",
    "order_id",
    "title",
    "content",
    "rating",
    "images",
    "like_count",
    "comment_count",
    "favorite_count",
    "status",
    "created_at",
    "updated_at"
) $NoteCsv

$merchantIds = @{}
$line = 1
foreach ($merchant in $merchantRows) {
    $line++
    $id = "$($merchant.id)".Trim()
    if ($id.Length -eq 0) {
        throw "$MerchantCsv line $line has an empty id."
    }
    if ($merchantIds.ContainsKey($id)) {
        throw "$MerchantCsv line $line duplicates merchant id $id."
    }
    $merchantIds[$id] = $true
    if ([string]::IsNullOrWhiteSpace("$($merchant.name)") -or [string]::IsNullOrWhiteSpace("$($merchant.address)")) {
        throw "$MerchantCsv line $line must have name and address."
    }
}

Assert-IntegerRange $merchantRows "score" 0 50 $MerchantCsv
Assert-IntegerRange $merchantRows "status" 0 2 $MerchantCsv
Assert-IntegerRange $noteRows "rating" 1 5 $NoteCsv
Assert-IntegerRange $noteRows "status" 0 1 $NoteCsv

$line = 1
foreach ($note in $noteRows) {
    $line++
    $merchantId = "$($note.merchant_id)".Trim()
    if (-not $merchantIds.ContainsKey($merchantId)) {
        throw "$NoteCsv line $line references unknown merchant id $merchantId."
    }
    if ([string]::IsNullOrWhiteSpace("$($note.title)") -or [string]::IsNullOrWhiteSpace("$($note.content)")) {
        throw "$NoteCsv line $line must have title and content."
    }
}

Write-Host "Validated $($merchantRows.Count) merchants and $($noteRows.Count) notes."
