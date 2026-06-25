<#
.SYNOPSIS
Runs the public-dataset read-path JMeter scenario.

.DESCRIPTION
Measures read-heavy endpoints after the S1 public dataset has been imported
into MySQL. The default shape runs 50 threads x 20 loops, so each core endpoint
is sampled about 1,000 times.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [ValidateNotNullOrEmpty()]
    [string] $BaseUrl = "http://localhost:8081",

    [string] $TokenCsv = "",

    [string] $UsersCsv = "",

    [Alias("OutputDir")]
    [string] $ResultsDir = "",

    [Alias("JMeterBin")]
    [ValidateNotNullOrEmpty()]
    [string] $JMeterPath = "jmeter",

    [ValidateNotNullOrEmpty()]
    [string] $RunId = (Get-Date -Format "yyyyMMdd-HHmmss"),

    [ValidateRange(1, [int]::MaxValue)]
    [int] $Threads = 50,

    [ValidateRange(1, [int]::MaxValue)]
    [int] $RampUp = 20,

    [ValidateRange(1, [int]::MaxValue)]
    [int] $Loops = 20,

    [ValidateRange(1, [int]::MaxValue)]
    [int] $MerchantPageMax = 500,

    [ValidateRange(1, [int]::MaxValue)]
    [int] $NotePageMax = 1000,

    [ValidateRange(1, 100)]
    [int] $MerchantPageSize = 20,

    [ValidateRange(1, 50)]
    [int] $NotePageSize = 20,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $DefaultMerchantId = 100000,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $DefaultNoteId = 200000,

    [switch] $NoHtmlReport,

    [switch] $AllowSampleErrors,

    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "JMeterEvidenceSupport.ps1")
$previewOnly = $DryRun -or $WhatIfPreference

if ([string]::IsNullOrWhiteSpace($ResultsDir)) {
    $ResultsDir = Get-JMeterEvidenceDefaultResultsDir -ScriptRoot $scriptRoot -RunId $RunId
}

$resolvedResultsDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ResultsDir)

if ([string]::IsNullOrWhiteSpace($TokenCsv)) {
    if ([string]::IsNullOrWhiteSpace($UsersCsv)) {
        $TokenCsv = Join-Path $PSScriptRoot "..\data\tokens-12000.csv"
    }
    else {
        $TokenCsv = Join-Path $resolvedResultsDir "tokens-$RunId.csv"
    }
}

function Assert-JtlHasNoSampleErrors {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "JMeter did not create the expected JTL file: $Path"
    }

    $reader = [System.IO.StreamReader]::new($Path)
    try {
        $headerLine = $reader.ReadLine()
    }
    finally {
        $reader.Dispose()
    }

    if ([string]::IsNullOrWhiteSpace($headerLine)) {
        throw "JTL file is empty: $Path"
    }

    $headers = @($headerLine.Split(","))
    if ($headers -notcontains "success") {
        throw "JTL file does not contain a success column: $Path"
    }

    $rowCount = 0
    $failedCount = 0
    $examples = [System.Collections.Generic.List[string]]::new()

    Import-Csv -LiteralPath $Path | ForEach-Object {
        $rowCount++
        if ([string] $_.success -ne "true") {
            $failedCount++
            if ($examples.Count -lt 3) {
                $examples.Add("$($_.label) [$($_.responseCode)] $($_.failureMessage)")
            }
        }
    }

    if ($rowCount -eq 0) {
        throw "JTL file contains no samples: $Path"
    }

    if ($failedCount -gt 0) {
        throw "Public dataset query scenario recorded $failedCount failed sample(s). Examples: $($examples -join '; ')"
    }

    Write-Host "JTL check passed: $rowCount sample(s), 0 failed."
}

$jmeterRoot = Split-Path -Parent $scriptRoot
$planPath = Join-Path $jmeterRoot "public-dataset-query-read.jmx"
if (-not (Test-Path -LiteralPath $planPath)) {
    throw "JMeter plan was not found: $planPath"
}

$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$resolvedTokenCsv = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($TokenCsv)
if (-not [string]::IsNullOrWhiteSpace($UsersCsv)) {
    $resolvedUsersCsv = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($UsersCsv)
    $authScript = Join-Path $scriptRoot "New-AuthTokens.ps1"
    $authArgs = @(
        "-BaseUrl", $normalizedBaseUrl,
        "-UserCsv", $resolvedUsersCsv,
        "-OutputPath", $resolvedTokenCsv
    )

    if ($previewOnly) {
        Write-Host "DRY RUN: $(Format-JMeterEvidenceCommand -Command $authScript -Arguments $authArgs)"
    }
    else {
        & $authScript -BaseUrl $normalizedBaseUrl -UserCsv $resolvedUsersCsv -OutputPath $resolvedTokenCsv
    }
}

if (-not $previewOnly -and -not (Test-Path -LiteralPath $resolvedTokenCsv)) {
    throw "TokenCsv was not found: $resolvedTokenCsv. Prepare tokens before running JMeter."
}

$resultPrefix = "public-dataset-query-read-$RunId"
$jtlPath = Join-Path $resolvedResultsDir "$resultPrefix.jtl"
$htmlPath = Join-Path $resolvedResultsDir "$resultPrefix-html"
$summaryJsonPath = Join-Path $resolvedResultsDir "$resultPrefix-summary.json"
$summaryMarkdownPath = Join-Path $resolvedResultsDir "$resultPrefix-summary.md"
$writeHtml = -not $NoHtmlReport

if (-not $previewOnly) {
    New-Item -ItemType Directory -Force -Path $resolvedResultsDir | Out-Null
    if ($writeHtml -and (Test-Path -LiteralPath $htmlPath)) {
        throw "HTML report path already exists: $htmlPath. Use a new RunId or OutputDir."
    }
}

$jmeterArgs = [System.Collections.Generic.List[string]]::new()
$jmeterArgs.Add("-n")
$jmeterArgs.Add("-t")
$jmeterArgs.Add($planPath)
$jmeterArgs.Add("-l")
$jmeterArgs.Add($jtlPath)
if ($writeHtml) {
    $jmeterArgs.Add("-e")
    $jmeterArgs.Add("-o")
    $jmeterArgs.Add($htmlPath)
}

$properties = @{
    baseUrl = $normalizedBaseUrl
    tokenCsv = $resolvedTokenCsv
    threads = $Threads
    rampUp = $RampUp
    loops = $Loops
    merchantPageMax = $MerchantPageMax
    notePageMax = $NotePageMax
    merchantPageSize = $MerchantPageSize
    notePageSize = $NotePageSize
    defaultMerchantId = $DefaultMerchantId
    defaultNoteId = $DefaultNoteId
}

foreach ($property in $properties.GetEnumerator()) {
    $jmeterArgs.Add("-J$($property.Key)=$($property.Value)")
}

Write-Host "Scenario: public-dataset-query-read"
Write-Host "JTL: $jtlPath"
if ($writeHtml) {
    Write-Host "HTML: $htmlPath"
}
Write-Host "Summary: $summaryMarkdownPath"

if ($previewOnly) {
    Write-Host "DRY RUN: $(Format-JMeterEvidenceCommand -Command $JMeterPath -Arguments ([string[]] $jmeterArgs.ToArray()))"
    return
}

& $JMeterPath @jmeterArgs
if ($LASTEXITCODE -ne 0) {
    throw "JMeter public dataset query scenario failed with exit code $LASTEXITCODE."
}

$summaryParameters = [ordered] @{
    baseUrl = $normalizedBaseUrl
    tokenCsv = $resolvedTokenCsv
    usersCsv = if ([string]::IsNullOrWhiteSpace($UsersCsv)) { "" } else { $resolvedUsersCsv }
    threads = $Threads
    rampUp = $RampUp
    loops = $Loops
    merchantPageMax = $MerchantPageMax
    notePageMax = $NotePageMax
    merchantPageSize = $MerchantPageSize
    notePageSize = $NotePageSize
    defaultMerchantId = $DefaultMerchantId
    defaultNoteId = $DefaultNoteId
}

$summary = Write-JMeterJtlSummary `
    -JtlPath $jtlPath `
    -ScenarioName "public-dataset-query-read" `
    -RunId $RunId `
    -PlanPath $planPath `
    -BaseUrl $normalizedBaseUrl `
    -HtmlReportPath $(if ($writeHtml) { $htmlPath } else { "" }) `
    -SummaryJsonPath $summaryJsonPath `
    -SummaryMarkdownPath $summaryMarkdownPath `
    -Parameters $summaryParameters

Write-Host "Summary written: $summaryMarkdownPath"

if (-not $AllowSampleErrors) {
    Assert-JtlHasNoSampleErrors -Path $jtlPath
}
