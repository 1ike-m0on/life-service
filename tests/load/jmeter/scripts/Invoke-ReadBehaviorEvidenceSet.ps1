<#
.SYNOPSIS
Runs the mixed-user and public-dataset read evidence scenarios in one command.

.DESCRIPTION
Orchestrates mixed-user-behavior.jmx and public-dataset-query-read.jmx through
the existing single-scenario wrappers. Results are archived by RunId under
tests/load/jmeter/results/evidence-<RunId> by default, including JTL, optional
HTML dashboards, per-scenario summaries, and an aggregate summary.

.PARAMETER OnlyScenario
Run only the listed scenarios. Accepted values are mixed-user-behavior and
public-dataset-query-read.

.PARAMETER SkipScenario
Skip the listed scenarios after OnlyScenario has been applied.

.PARAMETER UsersCsv
When supplied, tokens are prepared once before the timed scenarios. If TokenCsv
is omitted, the generated token CSV is written into the evidence output folder.

.PARAMETER DryRun
Print token-preparation and JMeter commands without writing files, changing
flash-sale data, calling the backend, or running JMeter. -WhatIf behaves the
same way.
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [Alias("Only")]
    [string[]] $OnlyScenario = @(),

    [Alias("Skip")]
    [string[]] $SkipScenario = @(),

    [ValidateNotNullOrEmpty()]
    [string] $BaseUrl = "http://localhost:8081",

    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [string] $TokenCsv = "",

    [string] $UsersCsv = "",

    [Alias("ResultsDir")]
    [string] $OutputDir = "",

    [Alias("JMeterPath")]
    [ValidateNotNullOrEmpty()]
    [string] $JMeterBin = "jmeter",

    [ValidateNotNullOrEmpty()]
    [string] $RunId = (Get-Date -Format "yyyyMMdd-HHmmss"),

    [ValidateRange(0, [int]::MaxValue)]
    [int] $Threads = 0,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $RampUp = 0,

    [ValidateRange(0, [int]::MaxValue)]
    [int] $Loops = 0,

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

if ([string]::IsNullOrWhiteSpace($OutputDir)) {
    $OutputDir = Get-JMeterEvidenceDefaultResultsDir -ScriptRoot $scriptRoot -RunId $RunId
}

$resolvedOutputDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)

if ([string]::IsNullOrWhiteSpace($TokenCsv)) {
    if ([string]::IsNullOrWhiteSpace($UsersCsv)) {
        $TokenCsv = Join-Path $PSScriptRoot "..\data\tokens-12000.csv"
    }
    else {
        $TokenCsv = Join-Path $resolvedOutputDir "tokens-$RunId.csv"
    }
}

$resolvedTokenCsv = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($TokenCsv)
$normalizedBaseUrl = $BaseUrl.TrimEnd("/")

$allScenarios = @("mixed-user-behavior", "public-dataset-query-read")
$onlyScenarioNames = Resolve-EvidenceScenarioList -Values $OnlyScenario -ParameterName "OnlyScenario" -AllowedScenarios $allScenarios
$skipScenarioNames = Resolve-EvidenceScenarioList -Values $SkipScenario -ParameterName "SkipScenario" -AllowedScenarios $allScenarios

$selectedScenarios = if ($onlyScenarioNames.Count -gt 0) {
    @($allScenarios | Where-Object { $onlyScenarioNames -contains $_ })
}
else {
    @($allScenarios)
}

if ($skipScenarioNames.Count -gt 0) {
    $selectedScenarios = @($selectedScenarios | Where-Object { $skipScenarioNames -notcontains $_ })
}

if ($selectedScenarios.Count -eq 0) {
    throw "No scenarios selected. Adjust OnlyScenario or SkipScenario."
}

Write-Host "RunId: $RunId"
Write-Host "OutputDir: $resolvedOutputDir"
Write-Host "Scenarios: $($selectedScenarios -join ', ')"

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
    New-Item -ItemType Directory -Force -Path $resolvedOutputDir | Out-Null
    if (-not (Test-Path -LiteralPath $resolvedTokenCsv)) {
        throw "TokenCsv was not found: $resolvedTokenCsv. Prepare tokens first or pass UsersCsv."
    }
}

$loadRunner = Join-Path $scriptRoot "Invoke-LoadScenario.ps1"
$publicDatasetRunner = Join-Path $scriptRoot "Invoke-PublicDatasetQueryScenario.ps1"
$scenarioSummaryPaths = [System.Collections.Generic.List[string]]::new()

foreach ($scenarioName in $selectedScenarios) {
    Write-Host ""
    Write-Host "=== Running evidence scenario: $scenarioName ==="

    if ($scenarioName -eq "mixed-user-behavior") {
        $arguments = @{
            Scenario = "mixed-user-behavior"
            BaseUrl = $normalizedBaseUrl
            VoucherId = $VoucherId
            TokenCsv = $resolvedTokenCsv
            ResultsDir = $resolvedOutputDir
            JMeterPath = $JMeterBin
            RunId = $RunId
        }

        if (-not $NoPrepareData) {
            $arguments.PrepareData = $true
        }
        if ($Threads -gt 0) {
            $arguments.Threads = $Threads
        }
        if ($RampUp -gt 0) {
            $arguments.RampUp = $RampUp
        }
        if ($Loops -gt 0) {
            $arguments.Loops = $Loops
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

        & $loadRunner @arguments

        $summaryPrefix = if ($Smoke) { "mixed-user-behavior-smoke-$RunId" } else { "mixed-user-behavior-$RunId" }
        $summaryPath = Join-Path $resolvedOutputDir "$summaryPrefix-summary.json"
    }
    elseif ($scenarioName -eq "public-dataset-query-read") {
        $arguments = @{
            BaseUrl = $normalizedBaseUrl
            TokenCsv = $resolvedTokenCsv
            ResultsDir = $resolvedOutputDir
            JMeterPath = $JMeterBin
            RunId = $RunId
        }

        if ($Threads -gt 0) {
            $arguments.Threads = $Threads
        }
        if ($RampUp -gt 0) {
            $arguments.RampUp = $RampUp
        }
        if ($Loops -gt 0) {
            $arguments.Loops = $Loops
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

        & $publicDatasetRunner @arguments

        $summaryPath = Join-Path $resolvedOutputDir "public-dataset-query-read-$RunId-summary.json"
    }
    else {
        throw "Unsupported scenario: $scenarioName"
    }

    if (-not $previewOnly) {
        if (-not (Test-Path -LiteralPath $summaryPath)) {
            throw "Expected scenario summary was not created: $summaryPath"
        }
        $scenarioSummaryPaths.Add($summaryPath)
    }
}

$runSummaryJson = Join-Path $resolvedOutputDir "summary.json"
$runSummaryMarkdown = Join-Path $resolvedOutputDir "summary.md"

if ($previewOnly) {
    Write-Host ""
    Write-Host "DRY RUN: aggregate summary would be written to $runSummaryMarkdown"
    return
}

$runSummary = Write-JMeterRunSummary `
    -RunId $RunId `
    -OutputDir $resolvedOutputDir `
    -ScenarioSummaryPaths ([string[]] $scenarioSummaryPaths.ToArray()) `
    -SummaryJsonPath $runSummaryJson `
    -SummaryMarkdownPath $runSummaryMarkdown

Write-Host ""
Write-Host "Evidence summary written: $runSummaryMarkdown"
