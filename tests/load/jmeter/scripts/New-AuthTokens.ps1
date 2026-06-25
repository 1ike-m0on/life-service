<#
.SYNOPSIS
Logs in generated load-test users and writes a JMeter token CSV.

.DESCRIPTION
Reads the user CSV produced by New-LoadUsers.ps1, calls the auth login endpoint,
and writes email, token, and clientIp columns. Output is written through a single
StreamWriter to a temporary file and then replaces the target CSV.

.PARAMETER BaseUrl
Backend base URL, for example http://localhost:8081.

.PARAMETER UserCsv
Input CSV containing an email column.

.PARAMETER OutputPath
CSV path to write. Defaults to tests/load/jmeter/data/tokens-12000.csv.

.PARAMETER ProgressEvery
How often to print token preparation progress. Must be greater than zero.

.PARAMETER ValidateOnly
Validate parameters and the input CSV without calling the backend or writing the
token CSV.
#>
[CmdletBinding()]
param(
    [ValidateNotNullOrEmpty()]
    [string] $BaseUrl = "http://localhost:8081",

    [ValidateNotNullOrEmpty()]
    [string] $UserCsv = "$PSScriptRoot\..\data\users-12000.csv",

    [ValidateNotNullOrEmpty()]
    [string] $OutputPath = "$PSScriptRoot\..\data\tokens-12000.csv",

    [ValidateRange(1, [int]::MaxValue)]
    [int] $ProgressEvery = 100,

    [switch] $ValidateOnly
)

$ErrorActionPreference = "Stop"

function ConvertTo-CsvField {
    param(
        [AllowNull()]
        [string] $Value
    )

    if ($null -eq $Value) {
        return ""
    }

    if ($Value -match '[,"\r\n]') {
        return '"' + $Value.Replace('"', '""') + '"'
    }

    return $Value
}

function Move-GeneratedFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $TempPath,

        [Parameter(Mandatory = $true)]
        [string] $TargetPath
    )

    if (Test-Path -LiteralPath $TargetPath) {
        [System.IO.File]::Replace($TempPath, $TargetPath, $null, $true)
    }
    else {
        [System.IO.File]::Move($TempPath, $TargetPath)
    }
}

$parsedBaseUrl = $null
if (-not [System.Uri]::TryCreate($BaseUrl, [System.UriKind]::Absolute, [ref] $parsedBaseUrl)) {
    throw "BaseUrl must be an absolute HTTP or HTTPS URL. Received: $BaseUrl"
}

if ($parsedBaseUrl.Scheme -notin @("http", "https")) {
    throw "BaseUrl must use http or https. Received scheme: $($parsedBaseUrl.Scheme)"
}

$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$loginUrl = "$normalizedBaseUrl/api/v1/auth/login"

$resolvedUserCsv = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($UserCsv)
if (-not (Test-Path -LiteralPath $resolvedUserCsv)) {
    throw "UserCsv was not found: $resolvedUserCsv. Generate it with New-LoadUsers.ps1 first."
}

$users = @(Import-Csv -LiteralPath $resolvedUserCsv)
if ($users.Count -eq 0) {
    throw "No users found in $resolvedUserCsv. The CSV must contain a header and at least one data row."
}

$columns = @($users[0].PSObject.Properties.Name)
if ($columns -notcontains "email") {
    throw "UserCsv must contain an 'email' column. Found columns: $($columns -join ', ')"
}

for ($i = 0; $i -lt $users.Count; $i++) {
    if ([string]::IsNullOrWhiteSpace($users[$i].email)) {
        throw "UserCsv row $($i + 2) has an empty email value."
    }
}

$maxClientIpRows = 255 * 254
if ($users.Count -gt $maxClientIpRows) {
    throw "UserCsv contains $($users.Count) users, but clientIp generation supports up to $maxClientIpRows rows."
}

$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
$outputDir = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

if ($ValidateOnly) {
    Write-Host "Validated $($users.Count) users from $resolvedUserCsv."
    Write-Host "Backend login was not attempted because -ValidateOnly was supplied."
    Write-Host "Token CSV target would be $resolvedOutput."
    return
}

$tempName = ".{0}.{1}.tmp" -f ([System.IO.Path]::GetFileName($resolvedOutput)), ([guid]::NewGuid().ToString("N"))
$tempPath = Join-Path $outputDir $tempName
$writer = $null
$completed = $false
$index = 0

try {
    $encoding = New-Object System.Text.UTF8Encoding -ArgumentList $false
    $writer = New-Object System.IO.StreamWriter -ArgumentList @($tempPath, $false, $encoding)
    $writer.WriteLine("email,token,clientIp")

    foreach ($user in $users) {
        $index++
        $email = [string] $user.email
        $body = @{ email = $email } | ConvertTo-Json -Compress

        try {
            $response = Invoke-RestMethod `
                -Method Post `
                -Uri $loginUrl `
                -ContentType "application/json" `
                -Body $body
        }
        catch {
            throw "Login request failed for '$email' at '$loginUrl': $($_.Exception.Message)"
        }

        if (-not $response.success -or -not $response.data.token) {
            throw "Login failed for '$email': response did not include a successful token payload."
        }

        $ipIndex = $index - 1
        $thirdOctet = [int] [Math]::Floor($ipIndex / 254) + 1
        $fourthOctet = ($ipIndex % 254) + 1
        $clientIp = "10.20.$thirdOctet.$fourthOctet"
        $line = "{0},{1},{2}" -f (ConvertTo-CsvField $email), (ConvertTo-CsvField ([string] $response.data.token)), $clientIp
        $writer.WriteLine($line)

        if ($index % $ProgressEvery -eq 0) {
            Write-Host "Prepared $index / $($users.Count) tokens"
        }
    }

    $writer.Dispose()
    $writer = $null

    Move-GeneratedFile -TempPath $tempPath -TargetPath $resolvedOutput
    $completed = $true
}
catch {
    throw "Failed to prepare token CSV '$resolvedOutput' after $index users: $($_.Exception.Message)"
}
finally {
    if ($null -ne $writer) {
        $writer.Dispose()
    }

    if (-not $completed -and (Test-Path -LiteralPath $tempPath)) {
        Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Prepared $index tokens at $resolvedOutput"
