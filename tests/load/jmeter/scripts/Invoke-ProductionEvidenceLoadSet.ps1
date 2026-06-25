<#
.SYNOPSIS
Runs the planned V2.5 production-evidence load-test scenario set.

.DESCRIPTION
Runs the selected scenarios in order through Invoke-LoadScenario.ps1. Each
scenario can reset the flash-sale voucher before it runs, so evidence runs are
repeatable and do not depend on the previous scenario's consumed stock.

.PARAMETER Scenario
Subset of scenarios to run. Defaults to the complete planned set.

.PARAMETER NoPrepareData
Skip MySQL/Redis reset and warmup before each scenario.

.PARAMETER Smoke
Run each scenario with a small verification shape instead of the full defaults.

.PARAMETER DryRun
Print the reset and JMeter commands without changing data or running JMeter.

.PARAMETER AllowSampleErrors
Do not fail a scenario wrapper when its JTL contains failed samples.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Alias("Only", "OnlyScenario")]
    [string[]] $Scenario = @(
        "success",
        "stock-competition",
        "sold-out-fast-failure",
        "fail-closed",
        "rate-limit",
        "mixed-user-behavior"
    ),

    [Alias("Skip")]
    [string[]] $SkipScenario = @(),

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

    [switch] $NoPrepareData,

    [switch] $Smoke,

    [switch] $NoHtmlReport,

    [switch] $AllowSampleErrors,

    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $scriptRoot "JMeterEvidenceSupport.ps1")
$previewOnly = $DryRun -or $WhatIfPreference

function Resolve-EvidenceScenarioList {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [string[]] $Values,

        [Parameter(Mandatory = $true)]
        [string] $ParameterName,

        [Parameter(Mandatory = $true)]
        [string[]] $AllowedScenarios
    )

    $resolved = [System.Collections.Generic.List[string]]::new()
    foreach ($value in $Values) {
        foreach ($part in ([string] $value -split ",")) {
            $name = $part.Trim()
            if ($name.Length -eq 0) {
                continue
            }
            if ($AllowedScenarios -notcontains $name) {
                throw "$ParameterName contains unsupported scenario '$name'. Allowed values: $($AllowedScenarios -join ', ')"
            }
            if (-not $resolved.Contains($name)) {
                $resolved.Add($name)
            }
        }
    }

    return [string[]] $resolved.ToArray()
}

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

$resolvedTokenCsv = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($TokenCsv)
$normalizedBaseUrl = $BaseUrl.TrimEnd("/")

$allScenarios = @("success", "stock-competition", "sold-out-fast-failure", "fail-closed", "rate-limit", "mixed-user-behavior")
$scenarioNames = Resolve-EvidenceScenarioList -Values $Scenario -ParameterName "Scenario" -AllowedScenarios $allScenarios
$skipScenarioNames = Resolve-EvidenceScenarioList -Values $SkipScenario -ParameterName "SkipScenario" -AllowedScenarios $allScenarios

if ($skipScenarioNames.Count -gt 0) {
    $scenarioNames = @($scenarioNames | Where-Object { $skipScenarioNames -notcontains $_ })
}

if ($scenarioNames.Count -eq 0) {
    throw "No scenarios selected. Adjust Scenario/OnlyScenario or SkipScenario."
}

Write-Host "RunId: $RunId"
Write-Host "OutputDir: $resolvedResultsDir"
Write-Host "Scenarios: $($scenarioNames -join ', ')"

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

if (-not $previewOnly) {
    New-Item -ItemType Directory -Force -Path $resolvedResultsDir | Out-Null
    if (-not (Test-Path -LiteralPath $resolvedTokenCsv)) {
        throw "TokenCsv was not found: $resolvedTokenCsv. Prepare tokens first or pass UsersCsv."
    }
}

$runner = Join-Path $PSScriptRoot "Invoke-LoadScenario.ps1"
$scenarioSummaryPaths = [System.Collections.Generic.List[string]]::new()

foreach ($scenarioName in $scenarioNames) {
    $arguments = @{
        Scenario = $scenarioName
        BaseUrl = $normalizedBaseUrl
        VoucherId = $VoucherId
        TokenCsv = $resolvedTokenCsv
        ResultsDir = $resolvedResultsDir
        JMeterPath = $JMeterPath
        RunId = $RunId
    }

    if (-not $NoPrepareData) {
        $arguments.PrepareData = $true
    }

    if ($Smoke) {
        $arguments.Smoke = $true
    }

    if ($NoHtmlReport) {
        $arguments.NoHtmlReport = $true
    }

    if ($AllowSampleErrors) {
        $arguments.AllowSampleErrors = $true
    }

    if ($previewOnly) {
        $arguments.DryRun = $true
    }

    Write-Host ""
    Write-Host "=== Running load scenario: $scenarioName ==="
    & $runner @arguments

    if (-not $previewOnly) {
        $summaryLabel = switch ($scenarioName) {
            "success" { "flash-sale-success" }
            "stock-competition" { "flash-sale-stock-competition" }
            "sold-out-fast-failure" { "flash-sale-sold-out-fast-failure" }
            "fail-closed" { "flash-sale-fail-closed" }
            "rate-limit" { "flash-sale-rate-limit" }
            "mixed-user-behavior" { "mixed-user-behavior" }
        }

        if ($Smoke) {
            $summaryLabel = "$summaryLabel-smoke"
        }

        $summaryPath = Join-Path $resolvedResultsDir "$summaryLabel-$RunId-summary.json"
        if (-not (Test-Path -LiteralPath $summaryPath)) {
            throw "Expected scenario summary was not created: $summaryPath"
        }
        $scenarioSummaryPaths.Add($summaryPath)
    }
}

$runSummaryJson = Join-Path $resolvedResultsDir "summary.json"
$runSummaryMarkdown = Join-Path $resolvedResultsDir "summary.md"

if ($previewOnly) {
    Write-Host ""
    Write-Host "DRY RUN: aggregate summary would be written to $runSummaryMarkdown"
    return
}

$runSummary = Write-JMeterRunSummary `
    -RunId $RunId `
    -OutputDir $resolvedResultsDir `
    -ScenarioSummaryPaths ([string[]] $scenarioSummaryPaths.ToArray()) `
    -SummaryJsonPath $runSummaryJson `
    -SummaryMarkdownPath $runSummaryMarkdown

Write-Host ""
Write-Host "Evidence summary written: $runSummaryMarkdown"
