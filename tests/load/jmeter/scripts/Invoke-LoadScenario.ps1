<#
.SYNOPSIS
Runs one reusable JMeter load-test scenario.

.DESCRIPTION
Maps a named production-evidence scenario to the right JMeter plan, default
properties, result paths, and optional flash-sale reset/warmup step.

.PARAMETER Scenario
Scenario name: success, stock-competition, sold-out-fast-failure, fail-closed,
rate-limit, or mixed-user-behavior.

.PARAMETER PrepareData
Reset MySQL and Redis for the selected flash-sale scenario before JMeter runs.

.PARAMETER Smoke
Use a tiny run shape for quick local verification. The rate-limit smoke still
uses enough concurrent users to cross the configured IP threshold.

.PARAMETER DryRun
Print the reset and JMeter commands without changing data or running JMeter.

.PARAMETER AllowSampleErrors
Do not fail the wrapper when the JTL contains failed samples.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("success", "stock-competition", "sold-out-fast-failure", "fail-closed", "rate-limit", "mixed-user-behavior")]
    [string] $Scenario,

    [ValidateNotNullOrEmpty()]
    [string] $BaseUrl = "http://localhost:8081",

    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [string] $TokenCsv = "",

    [string] $UsersCsv = "",

    [Alias("OutputDir")]
    [string] $ResultsDir = "",

    [Alias("JMeterBin")]
    [ValidateNotNullOrEmpty()]
    [string] $JMeterPath = "jmeter",

    [ValidateNotNullOrEmpty()]
    [string] $RunId = (Get-Date -Format "yyyyMMdd-HHmmss"),

    [ValidateRange(0, [int]::MaxValue)]
    [int] $Threads = 0,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $RampUp = 0,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $Loops = 0,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $Stock = 0,

    [int] $OrderPercent = -1,

    [int] $PayPercent = -1,

    [int] $OrderWaitMs = -1,

    [AllowEmptyString()]
    [string] $ClientIpOverride = "",

    [string[]] $JMeterProperty = @(),

    [switch] $PrepareData,

    [switch] $Smoke,

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

if ($OrderPercent -lt -1 -or $OrderPercent -gt 100) {
    throw "OrderPercent must be between 0 and 100."
}

if ($PayPercent -lt -1 -or $PayPercent -gt 100) {
    throw "PayPercent must be between 0 and 100."
}

if ($OrderWaitMs -lt -1) {
    throw "OrderWaitMs must be greater than or equal to 0."
}

function Get-NormalizedBaseUri {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Value
    )

    $parsed = $null
    if (-not [System.Uri]::TryCreate($Value, [System.UriKind]::Absolute, [ref] $parsed)) {
        throw "BaseUrl must be an absolute HTTP or HTTPS URL. Received: $Value"
    }

    if ($parsed.Scheme -notin @("http", "https")) {
        throw "BaseUrl must use http or https. Received scheme: $($parsed.Scheme)"
    }

    return $parsed
}

function Add-JMeterProperty {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.Generic.List[string]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $Name,

        [AllowNull()]
        [object] $Value
    )

    if ($null -eq $Value) {
        return
    }

    $stringValue = [string] $Value
    if ($stringValue.Length -eq 0) {
        return
    }

    $Arguments.Add("-J$Name=$stringValue")
}

function Assert-JtlHasNoSampleErrors {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [string] $ScenarioName
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
                $label = [string] $_.label
                $code = [string] $_.responseCode
                $message = [string] $_.failureMessage
                $examples.Add("$label [$code] $message")
            }
        }
    }

    if ($rowCount -eq 0) {
        throw "JTL file contains no samples: $Path"
    }

    if ($failedCount -gt 0) {
        throw "JMeter scenario '$ScenarioName' recorded $failedCount failed sample(s). Examples: $($examples -join '; ')"
    }

    Write-Host "JTL check passed: $rowCount sample(s), 0 failed."
}

$scenarioDefaults = @{
    "success" = @{
        Plan = "flash-sale-orders.jmx"
        Label = "flash-sale-success"
        Threads = 200
        RampUp = 10
        Loops = 60
        Stock = 12000
        ExpectedStatusRegex = "200"
        ExpectedApiCodeRegex = "OK"
    }
    "stock-competition" = @{
        Plan = "flash-sale-orders.jmx"
        Label = "flash-sale-stock-competition"
        Threads = 500
        RampUp = 10
        Loops = 60
        Stock = 12000
        ExpectedStatusRegex = "200"
        ExpectedApiCodeRegex = "OK|FLASH_SALE_STOCK_NOT_ENOUGH"
    }
    "sold-out-fast-failure" = @{
        Plan = "flash-sale-orders.jmx"
        Label = "flash-sale-sold-out-fast-failure"
        Threads = 200
        RampUp = 10
        Loops = 60
        Stock = 0
        ExpectedStatusRegex = "200"
        ExpectedApiCodeRegex = "FLASH_SALE_STOCK_NOT_ENOUGH"
    }
    "fail-closed" = @{
        Plan = "flash-sale-orders.jmx"
        Label = "flash-sale-fail-closed"
        Threads = 50
        RampUp = 5
        Loops = 1
        Stock = 12000
        ExpectedStatusRegex = "200"
        ExpectedApiCodeRegex = "FLASH_SALE_NOT_READY"
        FailClosed = $true
    }
    "rate-limit" = @{
        Plan = "flash-sale-orders.jmx"
        Label = "flash-sale-rate-limit"
        Threads = 40
        RampUp = 1
        Loops = 2
        Stock = 12000
        ExpectedStatusRegex = "200|429"
        ExpectedApiCodeRegex = "OK|RATE_LIMITED"
        ClientIpOverride = "10.20.250.1"
    }
    "mixed-user-behavior" = @{
        Plan = "mixed-user-behavior.jmx"
        Label = "mixed-user-behavior"
        Threads = 50
        RampUp = 20
        Loops = 20
        Stock = 12000
        OrderPercent = 5
        PayPercent = 20
        OrderWaitMs = 1000
    }
}

$config = $scenarioDefaults[$Scenario]
$baseUri = Get-NormalizedBaseUri -Value $BaseUrl
$normalizedBaseUrl = $BaseUrl.TrimEnd("/")

$effectiveThreads = if ($Threads -gt 0) { $Threads } else { [int] $config.Threads }
$effectiveRampUp = if ($RampUp -gt 0) { $RampUp } else { [int] $config.RampUp }
$effectiveLoops = if ($Loops -gt 0) { $Loops } else { [int] $config.Loops }
$effectiveStock = if ($PSBoundParameters.ContainsKey("Stock")) { $Stock } else { [int] $config.Stock }
$effectiveOrderPercent = if ($OrderPercent -ge 0) { $OrderPercent } elseif ($config.ContainsKey("OrderPercent")) { [int] $config.OrderPercent } else { $null }
$effectivePayPercent = if ($PayPercent -ge 0) { $PayPercent } elseif ($config.ContainsKey("PayPercent")) { [int] $config.PayPercent } else { $null }
$effectiveOrderWaitMs = if ($OrderWaitMs -ge 0) { $OrderWaitMs } elseif ($config.ContainsKey("OrderWaitMs")) { [int] $config.OrderWaitMs } else { $null }
$effectiveClientIpOverride = if ($ClientIpOverride.Length -gt 0) { $ClientIpOverride } elseif ($config.ContainsKey("ClientIpOverride")) { [string] $config.ClientIpOverride } else { "" }

if ($Smoke) {
    if ($Scenario -eq "rate-limit") {
        $effectiveThreads = 35
        $effectiveRampUp = 1
        $effectiveLoops = 1
    }
    else {
        $effectiveThreads = 1
        $effectiveRampUp = 1
        $effectiveLoops = 1
    }

    if ($Scenario -eq "mixed-user-behavior") {
        $effectiveOrderPercent = 100
        $effectivePayPercent = 0
    }
}

$jmeterRoot = Split-Path -Parent $scriptRoot
$planPath = Join-Path $jmeterRoot ([string] $config.Plan)
if (-not (Test-Path -LiteralPath $planPath)) {
    throw "JMeter plan was not found: $planPath"
}

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

$label = [string] $config.Label
if ($Smoke) {
    $label = "$label-smoke"
}

$resultPrefix = "$label-$RunId"
$jtlPath = Join-Path $resolvedResultsDir "$resultPrefix.jtl"
$htmlPath = Join-Path $resolvedResultsDir "$resultPrefix-html"
$summaryJsonPath = Join-Path $resolvedResultsDir "$resultPrefix-summary.json"
$summaryMarkdownPath = Join-Path $resolvedResultsDir "$resultPrefix-summary.md"
$writeHtml = -not $NoHtmlReport -and -not $Smoke

if ($PrepareData) {
    $resetScript = Join-Path $scriptRoot "Reset-FlashSaleVoucher.ps1"
    $resetArgs = @{
        VoucherId = $VoucherId
        Stock = $effectiveStock
        BaseUrl = $normalizedBaseUrl
    }

    if ($config.ContainsKey("FailClosed") -and $config.FailClosed) {
        $resetArgs.FailClosed = $true
    }

    if ($previewOnly) {
        $resetArgs.DryRun = $true
    }

    & $resetScript @resetArgs
}

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

Add-JMeterProperty -Arguments $jmeterArgs -Name "baseUrl" -Value $normalizedBaseUrl
Add-JMeterProperty -Arguments $jmeterArgs -Name "tokenCsv" -Value $resolvedTokenCsv
Add-JMeterProperty -Arguments $jmeterArgs -Name "threads" -Value $effectiveThreads
Add-JMeterProperty -Arguments $jmeterArgs -Name "rampUp" -Value $effectiveRampUp
Add-JMeterProperty -Arguments $jmeterArgs -Name "loops" -Value $effectiveLoops
Add-JMeterProperty -Arguments $jmeterArgs -Name "voucherId" -Value $VoucherId

$planName = [string] $config.Plan
if ($planName -eq "flash-sale-orders.jmx") {
    Add-JMeterProperty -Arguments $jmeterArgs -Name "protocol" -Value $baseUri.Scheme
    Add-JMeterProperty -Arguments $jmeterArgs -Name "host" -Value $baseUri.Host
    $port = if ($baseUri.IsDefaultPort) { if ($baseUri.Scheme -eq "https") { 443 } else { 80 } } else { $baseUri.Port }
    Add-JMeterProperty -Arguments $jmeterArgs -Name "port" -Value $port
    Add-JMeterProperty -Arguments $jmeterArgs -Name "expectedStatusRegex" -Value ([string] $config.ExpectedStatusRegex)
    Add-JMeterProperty -Arguments $jmeterArgs -Name "expectedApiCodeRegex" -Value ([string] $config.ExpectedApiCodeRegex)
}

Add-JMeterProperty -Arguments $jmeterArgs -Name "clientIpOverride" -Value $effectiveClientIpOverride
Add-JMeterProperty -Arguments $jmeterArgs -Name "orderPercent" -Value $effectiveOrderPercent
Add-JMeterProperty -Arguments $jmeterArgs -Name "payPercent" -Value $effectivePayPercent
Add-JMeterProperty -Arguments $jmeterArgs -Name "orderWaitMs" -Value $effectiveOrderWaitMs

foreach ($property in $JMeterProperty) {
    if ($property -notmatch "^[^=]+=.*$") {
        throw "JMeterProperty values must use name=value format. Invalid value: $property"
    }
    $jmeterArgs.Add("-J$property")
}

Write-Host "Scenario: $Scenario"
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
    throw "JMeter scenario '$Scenario' failed with exit code $LASTEXITCODE."
}

$summaryParameters = [ordered] @{
    baseUrl = $normalizedBaseUrl
    tokenCsv = $resolvedTokenCsv
    usersCsv = if ([string]::IsNullOrWhiteSpace($UsersCsv)) { "" } else { $resolvedUsersCsv }
    threads = $effectiveThreads
    rampUp = $effectiveRampUp
    loops = $effectiveLoops
    voucherId = $VoucherId
    stock = $effectiveStock
    orderPercent = $effectiveOrderPercent
    payPercent = $effectivePayPercent
    orderWaitMs = $effectiveOrderWaitMs
    clientIpOverride = $effectiveClientIpOverride
    smoke = [bool] $Smoke
}

$summary = Write-JMeterJtlSummary `
    -JtlPath $jtlPath `
    -ScenarioName $Scenario `
    -RunId $RunId `
    -PlanPath $planPath `
    -BaseUrl $normalizedBaseUrl `
    -HtmlReportPath $(if ($writeHtml) { $htmlPath } else { "" }) `
    -SummaryJsonPath $summaryJsonPath `
    -SummaryMarkdownPath $summaryMarkdownPath `
    -Parameters $summaryParameters

Write-Host "Summary written: $summaryMarkdownPath"

if (-not $AllowSampleErrors) {
    Assert-JtlHasNoSampleErrors -Path $jtlPath -ScenarioName $Scenario
}
