param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [string] $OutputPath = "",

    [string] $MapPath = "",

    [long] $StartMerchantId = 100000,

    [long] $DefaultCategoryId = 1,

    [long] $DefaultAvgPriceCent = 5000,

    [string] $DefaultArea = "Imported",

    [string] $DefaultOpenHours = "09:00-21:00",

    [string] $DefaultCreatedAt = "2026-01-01 00:00:00",

    [int] $MaxRows = 0,

    [switch] $ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Net.Http

$ScriptDirectory = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($ScriptDirectory)) {
    $ScriptDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $ScriptDirectory "..\generated\merchants.csv"
}
if ([string]::IsNullOrWhiteSpace($MapPath)) {
    $MapPath = Join-Path $ScriptDirectory "..\generated\merchant-id-map.csv"
}

function New-InputReader {
    param([string] $Path)

    if ($Path -match "^https?://") {
        $httpClient = [System.Net.Http.HttpClient]::new()
        $stream = $httpClient.GetStreamAsync($Path).GetAwaiter().GetResult()
    }
    else {
        $resolved = Resolve-Path -LiteralPath $Path
        $stream = [System.IO.File]::OpenRead($resolved.ProviderPath)
    }

    if ($Path.EndsWith(".gz", [System.StringComparison]::OrdinalIgnoreCase)) {
        $gzip = [System.IO.Compression.GzipStream]::new($stream, [System.IO.Compression.CompressionMode]::Decompress)
        return [System.IO.StreamReader]::new($gzip)
    }

    return [System.IO.StreamReader]::new($stream)
}

function ConvertTo-NormalizedName {
    param([string] $Name)
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
        return 0
    }

    $parsed = 0.0
    if (-not [double]::TryParse($Value, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref] $parsed)) {
        return 0
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
        [string] $CategoryId,
        [string] $CategoryText,
        [long] $DefaultValue
    )

    $parsed = 0L
    if ([long]::TryParse($CategoryId, [ref] $parsed) -and $parsed -gt 0) {
        return $parsed
    }

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

function Resolve-Status {
    param([string] $Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return 1
    }

    $text = $Value.Trim().ToLowerInvariant()
    if ($text -match "permanently closed" -or $text -in @("0", "closed", "disabled", "inactive", "false")) {
        return 0
    }
    if ($text -match "temporarily closed" -or $text -in @("2", "resting", "paused")) {
        return 2
    }
    return 1
}

$merchants = New-Object System.Collections.Generic.List[object]
$maps = New-Object System.Collections.Generic.List[object]
$seenSourceIds = @{}
$warnings = New-Object System.Collections.Generic.List[string]
$lineNumber = 0
$readCount = 0
$skippedCount = 0
$nextMerchantId = $StartMerchantId
$reader = New-InputReader $InputPath

try {
    while (($line = $reader.ReadLine()) -ne $null) {
        $lineNumber++
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }

        if ($MaxRows -gt 0 -and $readCount -ge $MaxRows) {
            break
        }

        $readCount++
        try {
            $row = $line | ConvertFrom-Json
        }
        catch {
            $warnings.Add("Line $lineNumber skipped because it is not valid JSON.")
            $skippedCount++
            continue
        }

        $sourceId = Get-JsonField $row @("source_id", "business_id", "gmap_id", "merchant_id", "id")
        if ([string]::IsNullOrWhiteSpace($sourceId)) {
            $sourceId = "line-$lineNumber"
            $warnings.Add("Line $lineNumber has no source id; using $sourceId.")
        }

        if ($seenSourceIds.ContainsKey($sourceId)) {
            $warnings.Add("Duplicate source id '$sourceId' skipped at line $lineNumber.")
            $skippedCount++
            continue
        }

        $name = Limit-Text (Get-JsonField $row @("name", "business_name", "merchant_name", "title")) 128
        if ([string]::IsNullOrWhiteSpace($name)) {
            $warnings.Add("Line $lineNumber skipped because name is missing.")
            $skippedCount++
            continue
        }

        $address = Limit-Text (Get-JsonField $row @("address", "address1", "full_address", "street_address")) 255
        if ([string]::IsNullOrWhiteSpace($address)) {
            $address = Limit-Text ("Imported address for $name") 255
            $warnings.Add("Line $lineNumber has no address; using a placeholder.")
        }

        $area = Limit-Text (Get-JsonField $row @("area", "district", "neighborhood", "neighbourhood", "city")) 128
        if ([string]::IsNullOrWhiteSpace($area)) {
            $area = $DefaultArea
        }

        $categoryText = Get-JsonField $row @("categories", "category", "type", "primary_category")
        $categoryId = Resolve-CategoryId `
            (Get-JsonField $row @("category_id", "categoryid", "type_id")) `
            $categoryText `
            $DefaultCategoryId

        $reviewCount = ConvertTo-IntegerOrDefault `
            (Get-JsonField $row @("review_count", "reviews", "comment_count", "comments", "num_of_reviews", "numofreviews")) `
            0 0 2147483647

        $soldCount = ConvertTo-IntegerOrDefault `
            (Get-JsonField $row @("sold_count", "orders", "checkins", "checkin_count")) `
            ([Math]::Max($reviewCount * 3, $reviewCount)) 0 2147483647

        $merchantId = $nextMerchantId
        $nextMerchantId++
        $seenSourceIds[$sourceId] = $merchantId

        $imageValue = Limit-Text (Get-JsonField $row @("images", "image_urls", "cover_image", "coverImage", "image_url", "photo_url", "photos", "pics")) 2048
        $openHours = Limit-Text (Get-JsonField $row @("open_hours", "hours", "business_hours")) 64
        if ([string]::IsNullOrWhiteSpace($openHours)) {
            $openHours = $DefaultOpenHours
        }

        $merchants.Add([pscustomobject]@{
                id = $merchantId
                category_id = $categoryId
                name = $name
                images = $imageValue
                area = $area
                address = $address
                longitude = ConvertTo-DecimalText (Get-JsonField $row @("longitude", "lng", "lon"))
                latitude = ConvertTo-DecimalText (Get-JsonField $row @("latitude", "lat"))
                avg_price_cent = ConvertTo-PriceCent (Get-JsonField $row @("avg_price_cent", "avg_price", "price_per_person", "price", "price_range")) $DefaultAvgPriceCent
                sold_count = $soldCount
                comment_count = $reviewCount
                score = ConvertTo-Score (Get-JsonField $row @("score", "rating", "stars", "avg_rating", "avgrating"))
                open_hours = $openHours
                status = Resolve-Status (Get-JsonField $row @("status", "is_open", "state"))
                created_at = $DefaultCreatedAt
                updated_at = $DefaultCreatedAt
            })

        $maps.Add([pscustomobject]@{
                source_merchant_id = $sourceId
                merchant_id = $merchantId
                name = $name
            })
    }
}
finally {
    $reader.Dispose()
}

if ($merchants.Count -eq 0) {
    throw "No merchant rows were produced from '$InputPath'."
}

Write-Host "Read $readCount source merchant rows."
Write-Host "Prepared $($merchants.Count) normalized merchant rows."
Write-Host "Skipped $skippedCount source merchant rows."
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
$mapDirectory = Split-Path -Parent $MapPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory)) {
    New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
}
if (-not [string]::IsNullOrWhiteSpace($mapDirectory)) {
    New-Item -ItemType Directory -Force -Path $mapDirectory | Out-Null
}

$merchants | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding UTF8
$maps | Export-Csv -LiteralPath $MapPath -NoTypeInformation -Encoding UTF8

Write-Host "Wrote merchant CSV: $OutputPath"
Write-Host "Wrote merchant map: $MapPath"
