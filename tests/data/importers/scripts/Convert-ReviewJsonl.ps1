param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [string] $MerchantMapPath = "",

    [string] $OutputPath = "",

    [string] $UserMapPath = "",

    [long] $StartNoteId = 200000,

    [long] $SeedUserIdStart = 2001,

    [int] $SeedUserCount = 20,

    [string] $DefaultCreatedAt = "2026-01-01 00:00:00",

    [int] $MaxRows = 0,

    [switch] $ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDirectory = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ScriptDirectory)) {
    $ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if ([string]::IsNullOrWhiteSpace($MerchantMapPath)) {
    $MerchantMapPath = Join-Path $ScriptDirectory "..\generated\merchant-id-map.csv"
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $ScriptDirectory "..\generated\merchant-notes.csv"
}
if ([string]::IsNullOrWhiteSpace($UserMapPath)) {
    $UserMapPath = Join-Path $ScriptDirectory "..\generated\source-user-id-map.csv"
}

function ConvertTo-NormalizedName {
    param([string] $Name)
    if ($null -eq $Name) {
        return ""
    }
    return (($Name -replace "[^A-Za-z0-9]", "").ToLowerInvariant())
}

function Get-JsonField {
    param(
        [Parameter(Mandatory = $true)] $Record,
        [Parameter(Mandatory = $true)] [string[]] $Names
    )

    $lookup = @{}
    foreach ($property in $Record.PSObject.Properties) {
        $lookup[(ConvertTo-NormalizedName $property.Name)] = $property.Value
    }

    foreach ($name in $Names) {
        $key = ConvertTo-NormalizedName $name
        if ($lookup.ContainsKey($key)) {
            $value = $lookup[$key]
            if ($null -ne $value) {
                if ($value -is [array]) {
                    $joined = ($value | ForEach-Object { "$_".Trim() } | Where-Object { $_.Length -gt 0 }) -join ","
                    if ($joined.Length -gt 0) {
                        return $joined
                    }
                }
                else {
                    $text = "$value".Trim()
                    if ($text.Length -gt 0) {
                        return $text
                    }
                }
            }
        }
    }

    return ""
}

function Limit-Text {
    param(
        [string] $Value,
        [int] $MaxLength
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }

    $clean = ($Value -replace "\s+", " ").Trim()
    if ($clean.Length -le $MaxLength) {
        return $clean
    }

    return $clean.Substring(0, $MaxLength)
}

function ConvertTo-Count {
    param([string] $Value)

    $parsed = 0
    if ([int]::TryParse($Value, [ref] $parsed) -and $parsed -gt 0) {
        return $parsed
    }
    return 0
}

function ConvertTo-Rating {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }

    $parsed = 0.0
    if (-not [double]::TryParse($Value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $parsed)) {
        return ""
    }

    $rating = [int] [Math]::Round($parsed, 0)
    if ($rating -lt 1) {
        return 1
    }
    if ($rating -gt 5) {
        return 5
    }
    return $rating
}

function ConvertTo-DateTimeText {
    param(
        [string] $Value,
        [string] $DefaultValue
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }

    $seconds = 0L
    if ([long]::TryParse($Value, [ref] $seconds) -and $seconds -gt 0) {
        try {
            return ([DateTimeOffset]::FromUnixTimeSeconds($seconds).UtcDateTime).ToString("yyyy-MM-dd HH:mm:ss")
        }
        catch {
            return $DefaultValue
        }
    }

    $parsed = [datetime]::MinValue
    if ([datetime]::TryParse($Value, [Globalization.CultureInfo]::InvariantCulture, [Globalization.DateTimeStyles]::AssumeLocal, [ref] $parsed)) {
        return $parsed.ToString("yyyy-MM-dd HH:mm:ss")
    }

    return $DefaultValue
}

function New-TitleFromContent {
    param([string] $Content)

    if ([string]::IsNullOrWhiteSpace($Content)) {
        return "Imported review"
    }

    $title = Limit-Text $Content 48
    if ($title.Length -lt $Content.Length) {
        return "$title..."
    }
    return $title
}

$resolvedInput = Resolve-Path -LiteralPath $InputPath
$resolvedMerchantMap = Resolve-Path -LiteralPath $MerchantMapPath
$merchantMapRows = Import-Csv -LiteralPath $resolvedMerchantMap
$merchantIdBySource = @{}

foreach ($mapRow in $merchantMapRows) {
    $source = "$($mapRow.source_merchant_id)".Trim()
    $merchantId = "$($mapRow.merchant_id)".Trim()
    if ($source.Length -gt 0 -and $merchantId.Length -gt 0) {
        $merchantIdBySource[$source] = $merchantId
    }
}

if ($merchantIdBySource.Count -eq 0) {
    throw "Merchant map '$MerchantMapPath' does not contain usable mappings."
}

if ($SeedUserCount -lt 1) {
    throw "SeedUserCount must be at least 1."
}

$notes = New-Object System.Collections.Generic.List[object]
$userMaps = New-Object System.Collections.Generic.List[object]
$seenReviewIds = @{}
$userIdBySource = @{}
$warnings = New-Object System.Collections.Generic.List[string]
$lineNumber = 0
$readCount = 0
$skippedCount = 0
$nextNoteId = $StartNoteId
$nextUserSlot = 0

foreach ($line in [System.IO.File]::ReadLines($resolvedInput)) {
    $lineNumber++
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }

    if ($MaxRows -gt 0 -and $readCount -ge $MaxRows) {
        break
    }

    $readCount++
    try {
        $record = $line | ConvertFrom-Json
    }
    catch {
        $warnings.Add("Line $lineNumber skipped because it is not valid JSON.")
        $skippedCount++
        continue
    }

    $reviewId = Get-JsonField $record @("review_id", "reviewid", "note_id", "id")
    if ([string]::IsNullOrWhiteSpace($reviewId)) {
        $reviewId = "line-$lineNumber"
    }

    if ($seenReviewIds.ContainsKey($reviewId)) {
        $warnings.Add("Duplicate review id '$reviewId' skipped at line $lineNumber.")
        $skippedCount++
        continue
    }
    $seenReviewIds[$reviewId] = $true

    $sourceMerchantId = Get-JsonField $record @("source_merchant_id", "business_id", "gmap_id", "merchant_id", "businessId")
    if ([string]::IsNullOrWhiteSpace($sourceMerchantId) -or -not $merchantIdBySource.ContainsKey($sourceMerchantId)) {
        $warnings.Add("Review '$reviewId' skipped because merchant '$sourceMerchantId' is not in the merchant map.")
        $skippedCount++
        continue
    }

    $content = Limit-Text (Get-JsonField $record @("content", "text", "review_text", "body", "comment")) 2000
    if ([string]::IsNullOrWhiteSpace($content)) {
        $warnings.Add("Review '$reviewId' skipped because content is missing.")
        $skippedCount++
        continue
    }

    $sourceUserId = Get-JsonField $record @("user_id", "userid", "author_id", "author", "profile_id")
    if ([string]::IsNullOrWhiteSpace($sourceUserId)) {
        $sourceUserId = "anonymous-$lineNumber"
    }

    if (-not $userIdBySource.ContainsKey($sourceUserId)) {
        $assignedUserId = $SeedUserIdStart + ($nextUserSlot % $SeedUserCount)
        $nextUserSlot++
        $userIdBySource[$sourceUserId] = $assignedUserId
        $userMaps.Add([pscustomobject]@{
                source_user_id = $sourceUserId
                user_id = $assignedUserId
            })
    }

    $title = Limit-Text (Get-JsonField $record @("title", "summary", "headline")) 128
    if ([string]::IsNullOrWhiteSpace($title)) {
        $title = Limit-Text (New-TitleFromContent $content) 128
    }

    $createdAt = ConvertTo-DateTimeText `
        (Get-JsonField $record @("created_at", "date", "time", "timestamp")) `
        $DefaultCreatedAt

    $notes.Add([pscustomobject]@{
            id = $nextNoteId
            user_id = $userIdBySource[$sourceUserId]
            merchant_id = $merchantIdBySource[$sourceMerchantId]
            order_id = ""
            title = $title
            content = $content
            rating = ConvertTo-Rating (Get-JsonField $record @("rating", "stars", "score"))
            images = Limit-Text (Get-JsonField $record @("images", "image_urls", "imageUrls", "photos", "pics")) 2048
            like_count = ConvertTo-Count (Get-JsonField $record @("like_count", "likes", "useful"))
            comment_count = ConvertTo-Count (Get-JsonField $record @("comment_count", "comments"))
            favorite_count = ConvertTo-Count (Get-JsonField $record @("favorite_count", "favorites"))
            status = 1
            created_at = $createdAt
            updated_at = $createdAt
        })

    $nextNoteId++
}

if ($notes.Count -eq 0) {
    throw "No note rows were produced from '$InputPath'."
}

Write-Host "Read $readCount source review rows."
Write-Host "Prepared $($notes.Count) normalized note rows."
Write-Host "Skipped $skippedCount source review rows."
if ($warnings.Count -gt 0) {
    Write-Host "Warnings:"
    $warnings | Select-Object -First 20 | ForEach-Object { Write-Host " - $_" }
    if ($warnings.Count -gt 20) {
        Write-Host " - ... $($warnings.Count - 20) more"
    }
}

if ($ValidateOnly) {
    Write-Host "Validation only; no files were written."
    return
}

$outputDirectory = Split-Path -Parent $OutputPath
$userMapDirectory = Split-Path -Parent $UserMapPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
if (-not [string]::IsNullOrWhiteSpace($userMapDirectory)) {
    New-Item -ItemType Directory -Force -Path $userMapDirectory | Out-Null
}

$notes | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
$userMaps | Export-Csv -LiteralPath $UserMapPath -NoTypeInformation -Encoding UTF8

Write-Host "Wrote note CSV: $OutputPath"
Write-Host "Wrote user map: $UserMapPath"
