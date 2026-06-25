<#
.SYNOPSIS
Builds repeatable S2 public-dataset CSVs from a local Google Local metadata file.

.DESCRIPTION
The S2 generator does not download data. It reads a local JSONL or JSONL.GZ
merchant metadata file, repeats and lightly mutates those source merchants, and
then writes synthetic note and favorite rows that preserve local-life read-path
shape at a larger scale.
#>
[CmdletBinding()]
param(
    [string] $InputPath = "",

    [string] $OutputDirectory = "",

    [string] $MerchantCsv = "",

    [string] $MerchantMapPath = "",

    [string] $NoteCsv = "",

    [string] $FavoriteCsv = "",

    [string] $SummaryPath = "",

    [ValidateRange(1, [int]::MaxValue)]
    [int] $MerchantCount = 50000,

    [ValidateRange(1, [int]::MaxValue)]
    [int] $NoteCount = 300000,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $FavoriteCount = 120000,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $SourceMaxRows = 0,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $StartMerchantId = 100000,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $StartNoteId = 200000,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $StartFavoriteId = 300000,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $SeedUserIdStart = 2001,

    [ValidateRange(1, 100000)]
    [int] $SeedUserCount = 20,

    [ValidateNotNullOrEmpty()]
    [string] $DefaultArea = "Vermont S2",

    [ValidateNotNullOrEmpty()]
    [string] $DefaultCreatedAt = "2026-06-01 00:00:00",

    [switch] $ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDirectory = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ScriptDirectory)) {
    $ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
}

if ([string]::IsNullOrWhiteSpace($InputPath)) {
    $InputPath = Join-Path $ScriptDirectory "..\..\raw\googlelocal\meta-Vermont.json.gz"
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $ScriptDirectory "..\generated"
}
if ([string]::IsNullOrWhiteSpace($MerchantCsv)) {
    $MerchantCsv = Join-Path $OutputDirectory "googlelocal-vt-s2-merchants.csv"
}
if ([string]::IsNullOrWhiteSpace($MerchantMapPath)) {
    $MerchantMapPath = Join-Path $OutputDirectory "googlelocal-vt-s2-merchant-id-map.csv"
}
if ([string]::IsNullOrWhiteSpace($NoteCsv)) {
    $NoteCsv = Join-Path $OutputDirectory "googlelocal-vt-s2-notes.csv"
}
if ([string]::IsNullOrWhiteSpace($FavoriteCsv)) {
    $FavoriteCsv = Join-Path $OutputDirectory "googlelocal-vt-s2-favorites.csv"
}
if ([string]::IsNullOrWhiteSpace($SummaryPath)) {
    $SummaryPath = Join-Path $OutputDirectory "googlelocal-vt-s2-summary.json"
}
if ($ValidateOnly -and $SourceMaxRows -eq 0) {
    $SourceMaxRows = 1000
}

$ImagePairs = @(
    "/assets/merchants/coffee/moonlight-cover.jpg,/assets/merchants/coffee/moonlight-01.jpg",
    "/assets/merchants/coffee/riverbank-cover.jpg,/assets/merchants/coffee/moonlight-01.jpg",
    "/assets/merchants/hotpot/red-flame-cover.jpg,/assets/merchants/hotpot/red-flame-01.jpg",
    "/assets/merchants/hotpot/shanhai-cover.jpg,/assets/merchants/hotpot/red-flame-01.jpg",
    "/assets/merchants/bakery/morning-wheat-cover.jpg,/assets/merchants/bakery/morning-wheat-01.jpg",
    "/assets/merchants/bakery/sweet-oven-cover.jpg,/assets/merchants/bakery/morning-wheat-01.jpg",
    "/assets/merchants/japanese/sora-sushi-cover.jpg,/assets/merchants/japanese/sora-sushi-01.jpg",
    "/assets/merchants/japanese/kyoto-bento-cover.jpg,/assets/merchants/japanese/sora-sushi-01.jpg",
    "/assets/merchants/lifestyle/urban-fit-cover.jpg,/assets/merchants/lifestyle/urban-fit-01.jpg",
    "/assets/merchants/lifestyle/starlight-cinema-cover.jpg,/assets/merchants/lifestyle/starlight-cinema-01.jpg"
)

function Resolve-LocalPath {
    param([Parameter(Mandatory = $true)][string] $Path)

    if ($Path -match "^https?://") {
        throw "S2 generation is local-only. Download raw data outside this script, then pass a local InputPath."
    }
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Input file was not found: $Path"
    }
    return (Resolve-Path -LiteralPath $Path).ProviderPath
}

function New-InputReader {
    param([Parameter(Mandatory = $true)][string] $Path)

    $resolved = Resolve-LocalPath $Path
    $stream = [System.IO.File]::OpenRead($resolved)
    if ($Path.EndsWith(".gz", [System.StringComparison]::OrdinalIgnoreCase)) {
        $gzip = [System.IO.Compression.GzipStream]::new($stream, [System.IO.Compression.CompressionMode]::Decompress)
        return [System.IO.StreamReader]::new($gzip)
    }

    return [System.IO.StreamReader]::new($stream)
}

function ConvertTo-NormalizedName {
    param([AllowNull()][string] $Name)

    if ($null -eq $Name) {
        return ""
    }
    return (($Name -replace "[^A-Za-z0-9]", "").ToLowerInvariant())
}

function ConvertTo-FlatText {
    param([AllowNull()] $Value)

    if ($null -eq $Value) {
        return ""
    }

    if ($Value -is [System.Collections.IEnumerable] -and $Value -isnot [string]) {
        $parts = New-Object System.Collections.Generic.List[string]
        foreach ($item in $Value) {
            $text = ConvertTo-FlatText $item
            if (-not [string]::IsNullOrWhiteSpace($text)) {
                $parts.Add($text)
            }
        }
        return ($parts -join ", ")
    }

    if ($Value.PSObject.Properties["url"]) {
        return ConvertTo-FlatText $Value.url
    }

    return "$Value".Trim()
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
            $text = ConvertTo-FlatText $lookup[$key]
            if (-not [string]::IsNullOrWhiteSpace($text)) {
                return $text
            }
        }
    }

    return ""
}

function Limit-Text {
    param(
        [AllowNull()][string] $Value,
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

function ConvertTo-IntegerOrDefault {
    param(
        [string] $Value,
        [long] $DefaultValue,
        [long] $MinValue,
        [long] $MaxValue
    )

    $parsed = 0L
    if ([long]::TryParse($Value, [ref] $parsed)) {
        if ($parsed -lt $MinValue) {
            return $MinValue
        }
        if ($parsed -gt $MaxValue) {
            return $MaxValue
        }
        return $parsed
    }

    return $DefaultValue
}

function ConvertTo-DecimalText {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }

    $parsed = 0.0
    if ([double]::TryParse($Value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $parsed)) {
        return $parsed.ToString("0.######", [Globalization.CultureInfo]::InvariantCulture)
    }

    return ""
}

function ConvertTo-Score {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return 42
    }

    $parsed = 0.0
    if (-not [double]::TryParse($Value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $parsed)) {
        return 42
    }

    if ($parsed -le 5.0) {
        $parsed = $parsed * 10.0
    }

    $score = [int] [Math]::Round($parsed, 0)
    if ($score -lt 0) {
        return 0
    }
    if ($score -gt 50) {
        return 50
    }
    return $score
}

function ConvertTo-PriceCent {
    param(
        [string] $Value,
        [long] $DefaultValue
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $DefaultValue
    }

    $trimmed = $Value.Trim()
    if ($trimmed -match "^\$+$") {
        switch ($trimmed.Length) {
            1 { return 3000 }
            2 { return 6000 }
            3 { return 12000 }
            default { return 20000 }
        }
    }

    $normalized = $trimmed -replace "[^0-9\.\-]", ""
    $amount = 0.0
    if ([double]::TryParse($normalized, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $amount)) {
        if ($amount -lt 0) {
            return $DefaultValue
        }
        if ($amount -gt 10000) {
            return [long] [Math]::Round($amount, 0)
        }
        return [long] [Math]::Round($amount * 100.0, 0)
    }

    return $DefaultValue
}

function Resolve-CategoryId {
    param(
        [string] $CategoryText,
        [long] $DefaultValue
    )

    $text = $CategoryText.ToLowerInvariant()
    if ($text -match "coffee|cafe|tea") {
        return 1
    }
    if ($text -match "hotpot|hot pot|sichuan|bbq|barbecue|restaurant|food") {
        return 2
    }
    if ($text -match "bakery|bread|cake|dessert") {
        return 3
    }
    if ($text -match "japanese|sushi|bento|ramen") {
        return 4
    }
    if ($text -match "fitness|gym|yoga|sport") {
        return 5
    }
    if ($text -match "cinema|movie|theater|entertainment") {
        return 6
    }

    return $DefaultValue
}

function Get-AssetImages {
    param([long] $Offset)

    return $script:ImagePairs[[int]($Offset % $script:ImagePairs.Count)]
}

function ConvertTo-CsvValue {
    param([AllowNull()] $Value)

    if ($null -eq $Value) {
        return '""'
    }

    $text = "$Value" -replace "[\r\n]+", " "
    $text = $text -replace '"', '""'
    return '"' + $text + '"'
}

function Write-CsvRow {
    param(
        [Parameter(Mandatory = $true)] [System.IO.StreamWriter] $Writer,
        [Parameter(Mandatory = $true)] [object[]] $Values
    )

    $escaped = foreach ($value in $Values) {
        ConvertTo-CsvValue $value
    }
    $Writer.WriteLine(($escaped -join ","))
}

function Get-NoteMerchantOffset {
    param([long] $Index)

    $hotMerchantCount = [Math]::Max(1, [int][Math]::Floor($MerchantCount * 0.2))
    $coldMerchantCount = $MerchantCount - $hotMerchantCount
    $hotNoteCount = [long][Math]::Floor($NoteCount * 0.8)

    if ($Index -lt $hotNoteCount -or $coldMerchantCount -eq 0) {
        return [int](($Index * 9973L) % $hotMerchantCount)
    }

    return [int]($hotMerchantCount + ((($Index - $hotNoteCount) * 7919L) % $coldMerchantCount))
}

function Get-GeneratedDateTime {
    param([long] $Offset)

    return $script:BaseDate.AddMinutes(-($Offset % 259200L)).ToString("yyyy-MM-dd HH:mm:ss", [Globalization.CultureInfo]::InvariantCulture)
}

$resolvedInputPath = Resolve-LocalPath $InputPath
$BaseDate = [datetime]::ParseExact($DefaultCreatedAt, "yyyy-MM-dd HH:mm:ss", [Globalization.CultureInfo]::InvariantCulture)

$favoriteCapacity = [long]$SeedUserCount * [long]$NoteCount
if ([long]$FavoriteCount -gt $favoriteCapacity) {
    throw "FavoriteCount exceeds unique user-note capacity. Max for this run is $favoriteCapacity."
}

$sourceMerchants = New-Object System.Collections.Generic.List[object]
$reader = New-InputReader $resolvedInputPath
$lineNumber = 0
$skippedCount = 0

try {
    while (($line = $reader.ReadLine()) -ne $null) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }

        try {
            $record = $line | ConvertFrom-Json
        }
        catch {
            $skippedCount++
            continue
        }

        $name = Limit-Text (Get-JsonField $record @("name", "business_name", "merchant_name", "title")) 118
        if ([string]::IsNullOrWhiteSpace($name)) {
            $skippedCount++
            continue
        }

        $address = Limit-Text (Get-JsonField $record @("address", "address1", "full_address", "street_address")) 230
        if ([string]::IsNullOrWhiteSpace($address)) {
            $address = Limit-Text ("Imported address for $name") 230
        }

        $categoryText = Get-JsonField $record @("categories", "category", "type", "primary_category")
        $reviewCount = ConvertTo-IntegerOrDefault `
            (Get-JsonField $record @("review_count", "reviews", "comment_count", "comments", "num_of_reviews", "numofreviews")) `
            0 0 2147483647

        $sourceId = Get-JsonField $record @("source_id", "business_id", "gmap_id", "merchant_id", "id")
        if ([string]::IsNullOrWhiteSpace($sourceId)) {
            $sourceId = "line-$lineNumber"
        }

        $sourceMerchants.Add([pscustomobject]@{
                source_id = $sourceId
                name = $name
                address = $address
                longitude = ConvertTo-DecimalText (Get-JsonField $record @("longitude", "lng", "lon"))
                latitude = ConvertTo-DecimalText (Get-JsonField $record @("latitude", "lat"))
                category_id = Resolve-CategoryId $categoryText 1
                avg_price_cent = ConvertTo-PriceCent (Get-JsonField $record @("avg_price_cent", "avg_price", "price_per_person", "price", "price_range")) 5000
                source_review_count = $reviewCount
                score = ConvertTo-Score (Get-JsonField $record @("score", "rating", "stars", "avg_rating", "avgrating"))
                open_hours = Limit-Text (Get-JsonField $record @("open_hours", "hours", "business_hours")) 64
            })

        if ($SourceMaxRows -gt 0 -and $sourceMerchants.Count -ge $SourceMaxRows) {
            break
        }
    }
}
finally {
    $reader.Dispose()
}

if ($sourceMerchants.Count -eq 0) {
    throw "No usable merchant metadata was read from '$resolvedInputPath'."
}

$noteMerchantOffsets = [int[]]::new($NoteCount)
$noteCountByMerchant = [int[]]::new($MerchantCount)
for ($i = 0; $i -lt $NoteCount; $i++) {
    $merchantOffset = Get-NoteMerchantOffset $i
    $noteMerchantOffsets[$i] = $merchantOffset
    $noteCountByMerchant[$merchantOffset]++
}

$favoriteCountByNote = [int[]]::new($NoteCount)
for ($i = 0; $i -lt $FavoriteCount; $i++) {
    $round = [long][Math]::Floor($i / $SeedUserCount)
    $userIndex = $i % $SeedUserCount
    $noteOffset = [int]((($round * 7919L) + ($userIndex * 104729L)) % $NoteCount)
    $favoriteCountByNote[$noteOffset]++
}

Write-Host "Input metadata: $resolvedInputPath"
Write-Host "Read $($sourceMerchants.Count) usable source merchants; skipped $skippedCount rows."
Write-Host "S2 target rows: $MerchantCount merchants, $NoteCount notes, $FavoriteCount favorites."
Write-Host "Hot distribution: 20% merchants receive about 80% of notes."

if ($ValidateOnly) {
    Write-Host "Validation only; no files were written."
    return
}

foreach ($path in @($MerchantCsv, $MerchantMapPath, $NoteCsv, $FavoriteCsv, $SummaryPath)) {
    $directory = Split-Path -Parent $path
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$merchantWriter = [System.IO.StreamWriter]::new($MerchantCsv, $false, $utf8NoBom)
$mapWriter = [System.IO.StreamWriter]::new($MerchantMapPath, $false, $utf8NoBom)
$noteWriter = [System.IO.StreamWriter]::new($NoteCsv, $false, $utf8NoBom)
$favoriteWriter = [System.IO.StreamWriter]::new($FavoriteCsv, $false, $utf8NoBom)
$noteRowsWithImages = 0

try {
    Write-CsvRow $merchantWriter @(
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
    )
    Write-CsvRow $mapWriter @("source_merchant_id", "merchant_id", "name")
    Write-CsvRow $noteWriter @(
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
    )
    Write-CsvRow $favoriteWriter @("id", "user_id", "note_id", "status", "created_at", "updated_at")

    for ($i = 0; $i -lt $MerchantCount; $i++) {
        $source = $sourceMerchants[$i % $sourceMerchants.Count]
        $cycle = [int][Math]::Floor($i / $sourceMerchants.Count)
        $merchantId = $StartMerchantId + $i
        $nameSuffix = if ($cycle -eq 0) { "" } else { " S2-$cycle" }
        $merchantName = Limit-Text "$($source.name)$nameSuffix" 128
        $createdAt = Get-GeneratedDateTime ($i * 13L)
        $merchantNoteCount = $noteCountByMerchant[$i]
        $soldCount = [Math]::Max(($source.source_review_count * 3) + ($i % 700), $merchantNoteCount * 3)
        $score = [Math]::Max(30, [Math]::Min(50, $source.score + ($i % 5) - 2))
        $status = if (($i % 37) -eq 0) { 2 } else { 1 }
        $openHours = if ([string]::IsNullOrWhiteSpace($source.open_hours)) { "09:00-21:00" } else { $source.open_hours }
        $sourceMapId = "$($source.source_id)#s2-$cycle"

        Write-CsvRow $merchantWriter @(
            $merchantId,
            $source.category_id,
            $merchantName,
            (Get-AssetImages $i),
            $DefaultArea,
            (Limit-Text "$($source.address) #$cycle" 255),
            $source.longitude,
            $source.latitude,
            $source.avg_price_cent,
            $soldCount,
            $merchantNoteCount,
            $score,
            $openHours,
            $status,
            $createdAt,
            $createdAt
        )

        Write-CsvRow $mapWriter @($sourceMapId, $merchantId, $merchantName)
    }

    for ($i = 0; $i -lt $NoteCount; $i++) {
        $merchantOffset = $noteMerchantOffsets[$i]
        $source = $sourceMerchants[$merchantOffset % $sourceMerchants.Count]
        $noteId = $StartNoteId + $i
        $merchantId = $StartMerchantId + $merchantOffset
        $userId = $SeedUserIdStart + (($i * 7) % $SeedUserCount)
        $rating = 3 + ($i % 3)
        $createdAt = Get-GeneratedDateTime ($i * 3L)
        $images = ""
        if (($i % 5) -ne 0) {
            $images = Get-AssetImages ($i + $merchantOffset)
            $noteRowsWithImages++
        }
        $title = Limit-Text "S2 local-life note $noteId for $($source.name)" 128
        $content = Limit-Text "Synthetic S2 note generated from local Google Local metadata for read-path validation. Source merchant $($source.name) is represented in the expanded sample with deterministic user, time, rating, image, like, comment, and favorite distribution. Sequence $i." 2000

        Write-CsvRow $noteWriter @(
            $noteId,
            $userId,
            $merchantId,
            "",
            $title,
            $content,
            $rating,
            $images,
            (($i * 17) % 5000),
            (($i * 3) % 25),
            $favoriteCountByNote[$i],
            1,
            $createdAt,
            $createdAt
        )
    }

    for ($i = 0; $i -lt $FavoriteCount; $i++) {
        $favoriteId = $StartFavoriteId + $i
        $round = [long][Math]::Floor($i / $SeedUserCount)
        $userIndex = $i % $SeedUserCount
        $noteOffset = [int]((($round * 7919L) + ($userIndex * 104729L)) % $NoteCount)
        $createdAt = Get-GeneratedDateTime ($i * 5L)

        Write-CsvRow $favoriteWriter @(
            $favoriteId,
            ($SeedUserIdStart + $userIndex),
            ($StartNoteId + $noteOffset),
            1,
            $createdAt,
            $createdAt
        )
    }
}
finally {
    $merchantWriter.Dispose()
    $mapWriter.Dispose()
    $noteWriter.Dispose()
    $favoriteWriter.Dispose()
}

$summary = [pscustomobject]@{
    input_path = $resolvedInputPath
    merchant_csv = $MerchantCsv
    merchant_map_path = $MerchantMapPath
    note_csv = $NoteCsv
    favorite_csv = $FavoriteCsv
    merchant_count = $MerchantCount
    note_count = $NoteCount
    favorite_count = $FavoriteCount
    merchant_id_min = $StartMerchantId
    note_id_min = $StartNoteId
    favorite_id_min = $StartFavoriteId
    seed_user_id_start = $SeedUserIdStart
    seed_user_count = $SeedUserCount
    merchant_image_references = $MerchantCount * 2
    note_rows_with_images = $noteRowsWithImages
    note_image_references = $noteRowsWithImages * 2
    hot_merchant_percent = 20
    hot_note_percent = 80
    generated_at_utc = [DateTime]::UtcNow.ToString("o")
}

$summary | ConvertTo-Json | Set-Content -LiteralPath $SummaryPath -Encoding UTF8

Write-Host "Wrote merchant CSV: $MerchantCsv"
Write-Host "Wrote merchant map: $MerchantMapPath"
Write-Host "Wrote note CSV: $NoteCsv"
Write-Host "Wrote favorite CSV: $FavoriteCsv"
Write-Host "Wrote summary: $SummaryPath"
