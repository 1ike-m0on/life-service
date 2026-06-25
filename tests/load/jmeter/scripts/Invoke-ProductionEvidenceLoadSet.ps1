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
[CmdletBinding()]
param(
    [ValidateSet("success", "stock-competition", "sold-out-fast-failure", "fail-closed", "rate-limit", "mixed-user-behavior")]
    [string[]] $Scenario = @(
        "success",
        "stock-competition",
        "sold-out-fast-failure",
        "fail-closed",
        "rate-limit",
        "mixed-user-behavior"
    ),

    [ValidateNotNullOrEmpty()]
    [string] $BaseUrl = "http://localhost:8081",

    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [string] $TokenCsv = "",

    [string] $ResultsDir = "",

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

if ([string]::IsNullOrWhiteSpace($TokenCsv)) {
    $TokenCsv = Join-Path $PSScriptRoot "..\data\tokens-12000.csv"
}

if ([string]::IsNullOrWhiteSpace($ResultsDir)) {
    $ResultsDir = Join-Path $PSScriptRoot "..\results"
}

$runner = Join-Path $PSScriptRoot "Invoke-LoadScenario.ps1"

foreach ($scenarioName in $Scenario) {
    $arguments = @{
        Scenario = $scenarioName
        BaseUrl = $BaseUrl
        VoucherId = $VoucherId
        TokenCsv = $TokenCsv
        ResultsDir = $ResultsDir
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

    if ($DryRun) {
        $arguments.DryRun = $true
    }

    Write-Host ""
    Write-Host "=== Running load scenario: $scenarioName ==="
    & $runner @arguments
}
