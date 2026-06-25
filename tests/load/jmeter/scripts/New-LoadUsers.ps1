<#
.SYNOPSIS
Generates a deterministic load-test user CSV.

.DESCRIPTION
Writes a CSV with one email column for JMeter data preparation. The script writes
to a temporary file in the output directory and then replaces the target file, so
large runs do not append concurrently to the same CSV.

.PARAMETER Count
Number of users to generate. Must be greater than zero.

.PARAMETER StartUserId
First numeric suffix to use in generated emails. The legacy -StartIndex alias is
still accepted.

.PARAMETER EmailPrefix
Email local-part prefix used before the numeric suffix.

.PARAMETER EmailDomain
Email domain used after the @ sign. The legacy -Domain alias is still accepted.

.PARAMETER OutputPath
CSV path to write. Defaults to tests/load/jmeter/data/users-12000.csv.
#>
[CmdletBinding()]
param(
    [ValidateRange(1, [int]::MaxValue)]
    [int] $Count = 12000,

    [Alias("StartIndex")]
    [ValidateRange(1, [int]::MaxValue)]
    [int] $StartUserId = 1,

    [ValidateNotNullOrEmpty()]
    [string] $EmailPrefix = "load-user",

    [Alias("Domain")]
    [ValidateNotNullOrEmpty()]
    [string] $EmailDomain = "life.local",

    [ValidateNotNullOrEmpty()]
    [string] $OutputPath = "$PSScriptRoot\..\data\users-12000.csv"
)

$ErrorActionPreference = "Stop"

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

if ($EmailPrefix -match '[,\r\n@]') {
    throw "EmailPrefix cannot contain comma, newline, or @."
}

if ($EmailDomain -match '[,\r\n@]') {
    throw "EmailDomain cannot contain comma, newline, or @."
}

$lastUserId = [int64] $StartUserId + [int64] $Count - 1
if ($lastUserId -gt [int]::MaxValue) {
    throw "StartUserId + Count - 1 cannot exceed $([int]::MaxValue)."
}

$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
$outputDir = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$tempName = ".{0}.{1}.tmp" -f ([System.IO.Path]::GetFileName($resolvedOutput)), ([guid]::NewGuid().ToString("N"))
$tempPath = Join-Path $outputDir $tempName
$writer = $null
$completed = $false

Write-Host "Generating $Count users starting at user id $StartUserId..."

try {
    $encoding = New-Object System.Text.UTF8Encoding -ArgumentList $false
    $writer = New-Object System.IO.StreamWriter -ArgumentList @($tempPath, $false, $encoding)
    $writer.WriteLine("email")

    for ($i = 0; $i -lt $Count; $i++) {
        $seq = $StartUserId + $i
        $email = "{0}{1}@{2}" -f $EmailPrefix, $seq, $EmailDomain
        $writer.WriteLine($email)
    }

    $writer.Dispose()
    $writer = $null

    Move-GeneratedFile -TempPath $tempPath -TargetPath $resolvedOutput
    $completed = $true
}
catch {
    throw "Failed to generate user CSV '$resolvedOutput': $($_.Exception.Message)"
}
finally {
    if ($null -ne $writer) {
        $writer.Dispose()
    }

    if (-not $completed -and (Test-Path -LiteralPath $tempPath)) {
        Remove-Item -LiteralPath $tempPath -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Generated $Count users at $resolvedOutput"
