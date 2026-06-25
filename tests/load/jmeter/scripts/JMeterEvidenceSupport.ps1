function Get-JMeterEvidenceDefaultResultsDir {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ScriptRoot,

        [Parameter(Mandatory = $true)]
        [string] $RunId
    )

    $jmeterRoot = Split-Path -Parent $ScriptRoot
    $resultsRoot = Join-Path $jmeterRoot "results"
    return Join-Path $resultsRoot "evidence-$RunId"
}

function Format-JMeterEvidenceCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Command,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    function Format-JMeterEvidenceCommandToken {
        param(
            [Parameter(Mandatory = $true)]
            [string] $Value
        )

        if ($Value -match "[\s|&;<>]") {
            return '"' + $Value.Replace('"', '\"') + '"'
        }

        return $Value
    }

    $printableCommand = Format-JMeterEvidenceCommandToken -Value $Command
    $printableArgs = $Arguments | ForEach-Object {
        Format-JMeterEvidenceCommandToken -Value ([string] $_)
    }

    return "$printableCommand $($printableArgs -join ' ')"
}

function Get-JMeterEvidenceNumber {
    param(
        [AllowNull()]
        [object] $Value
    )

    if ($null -eq $Value) {
        return $null
    }

    $text = ([string] $Value).Trim()
    if ($text.Length -eq 0) {
        return $null
    }

    $parsed = 0.0
    if ([double]::TryParse(
            $text,
            [System.Globalization.NumberStyles]::Float,
            [System.Globalization.CultureInfo]::InvariantCulture,
            [ref] $parsed)) {
        return $parsed
    }

    return $null
}

function Get-JMeterEvidenceRoundedNumber {
    param(
        [AllowNull()]
        [object] $Value
    )

    if ($null -eq $Value) {
        return $null
    }

    return [Math]::Round([double] $Value, 2)
}

function Get-JMeterEvidencePercentile {
    param(
        [Parameter(Mandatory = $true)]
        [object[]] $SortedValues,

        [Parameter(Mandatory = $true)]
        [double] $Percentile
    )

    if ($SortedValues.Count -eq 0) {
        return $null
    }

    $index = [int] [Math]::Ceiling(($Percentile / 100.0) * $SortedValues.Count) - 1
    if ($index -lt 0) {
        $index = 0
    }
    if ($index -ge $SortedValues.Count) {
        $index = $SortedValues.Count - 1
    }

    return Get-JMeterEvidenceRoundedNumber -Value $SortedValues[$index]
}

function New-JMeterEvidenceMetricBlock {
    param(
        [Parameter(Mandatory = $true)]
        [int] $SampleCount,

        [Parameter(Mandatory = $true)]
        [int] $SuccessCount,

        [Parameter(Mandatory = $true)]
        [int] $FailureCount,

        [Parameter(Mandatory = $true)]
        [object[]] $ElapsedValues,

        [AllowNull()]
        [object] $DurationSeconds
    )

    $sortedElapsed = @($ElapsedValues | Sort-Object)
    $elapsedTotal = 0.0
    foreach ($elapsed in $ElapsedValues) {
        $elapsedTotal += [double] $elapsed
    }

    $avgMs = if ($sortedElapsed.Count -gt 0) { $elapsedTotal / $sortedElapsed.Count } else { $null }
    $throughput = if ($null -ne $DurationSeconds -and [double] $DurationSeconds -gt 0) {
        $SampleCount / [double] $DurationSeconds
    }
    else {
        $null
    }

    return [ordered] @{
        sampleCount = $SampleCount
        successCount = $SuccessCount
        failureCount = $FailureCount
        errorRatePercent = if ($SampleCount -gt 0) { Get-JMeterEvidenceRoundedNumber -Value (($FailureCount * 100.0) / $SampleCount) } else { $null }
        avgMs = Get-JMeterEvidenceRoundedNumber -Value $avgMs
        minMs = if ($sortedElapsed.Count -gt 0) { Get-JMeterEvidenceRoundedNumber -Value $sortedElapsed[0] } else { $null }
        medianMs = Get-JMeterEvidencePercentile -SortedValues $sortedElapsed -Percentile 50
        p90Ms = Get-JMeterEvidencePercentile -SortedValues $sortedElapsed -Percentile 90
        p95Ms = Get-JMeterEvidencePercentile -SortedValues $sortedElapsed -Percentile 95
        p99Ms = Get-JMeterEvidencePercentile -SortedValues $sortedElapsed -Percentile 99
        maxMs = if ($sortedElapsed.Count -gt 0) { Get-JMeterEvidenceRoundedNumber -Value $sortedElapsed[-1] } else { $null }
        throughputPerSec = Get-JMeterEvidenceRoundedNumber -Value $throughput
    }
}

function Write-JMeterEvidenceFile {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]] $Lines
    )

    $parent = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    Set-Content -LiteralPath $Path -Value $Lines -Encoding UTF8
}

function Write-JMeterJtlSummary {
    param(
        [Parameter(Mandatory = $true)]
        [string] $JtlPath,

        [Parameter(Mandatory = $true)]
        [string] $ScenarioName,

        [Parameter(Mandatory = $true)]
        [string] $RunId,

        [Parameter(Mandatory = $true)]
        [string] $PlanPath,

        [Parameter(Mandatory = $true)]
        [string] $BaseUrl,

        [AllowEmptyString()]
        [string] $HtmlReportPath = "",

        [Parameter(Mandatory = $true)]
        [string] $SummaryJsonPath,

        [Parameter(Mandatory = $true)]
        [string] $SummaryMarkdownPath,

        [AllowNull()]
        [System.Collections.IDictionary] $Parameters = @{}
    )

    if (-not (Test-Path -LiteralPath $JtlPath)) {
        throw "JMeter did not create the expected JTL file: $JtlPath"
    }

    $totalCount = 0
    $successCount = 0
    $failureCount = 0
    $elapsedValues = [System.Collections.Generic.List[double]]::new()
    $failures = [System.Collections.Generic.List[object]]::new()
    $labels = @{}
    $firstTimestamp = $null
    $lastTimestamp = $null

    Import-Csv -LiteralPath $JtlPath | ForEach-Object {
        $totalCount++

        $label = [string] $_.label
        if ([string]::IsNullOrWhiteSpace($label)) {
            $label = "(unlabeled)"
        }

        if (-not $labels.ContainsKey($label)) {
            $labels[$label] = @{
                sampleCount = 0
                successCount = 0
                failureCount = 0
                elapsedValues = [System.Collections.Generic.List[double]]::new()
            }
        }

        $labelStats = $labels[$label]
        $labelStats.sampleCount++

        $isSuccess = ([string] $_.success) -eq "true"
        if ($isSuccess) {
            $successCount++
            $labelStats.successCount++
        }
        else {
            $failureCount++
            $labelStats.failureCount++
            if ($failures.Count -lt 5) {
                $failures.Add([ordered] @{
                    label = $label
                    responseCode = [string] $_.responseCode
                    failureMessage = [string] $_.failureMessage
                })
            }
        }

        $elapsed = Get-JMeterEvidenceNumber -Value $_.elapsed
        if ($null -ne $elapsed) {
            $elapsedValues.Add([double] $elapsed)
            $labelStats.elapsedValues.Add([double] $elapsed)
        }

        $timestamp = Get-JMeterEvidenceNumber -Value $_.timeStamp
        if ($null -ne $timestamp) {
            if ($null -eq $firstTimestamp -or $timestamp -lt $firstTimestamp) {
                $firstTimestamp = $timestamp
            }

            $endTimestamp = if ($null -ne $elapsed) { $timestamp + $elapsed } else { $timestamp }
            if ($null -eq $lastTimestamp -or $endTimestamp -gt $lastTimestamp) {
                $lastTimestamp = $endTimestamp
            }
        }
    }

    if ($totalCount -eq 0) {
        throw "JTL file contains no samples: $JtlPath"
    }

    $durationSeconds = if ($null -ne $firstTimestamp -and $null -ne $lastTimestamp -and $lastTimestamp -gt $firstTimestamp) {
        ($lastTimestamp - $firstTimestamp) / 1000.0
    }
    else {
        $null
    }

    $labelSummaries = @(
        foreach ($entry in ($labels.GetEnumerator() | Sort-Object Name)) {
            $stats = $entry.Value
            [ordered] @{
                label = $entry.Name
                metrics = New-JMeterEvidenceMetricBlock `
                    -SampleCount $stats.sampleCount `
                    -SuccessCount $stats.successCount `
                    -FailureCount $stats.failureCount `
                    -ElapsedValues @($stats.elapsedValues) `
                    -DurationSeconds $durationSeconds
            }
        }
    )

    $metrics = New-JMeterEvidenceMetricBlock `
        -SampleCount $totalCount `
        -SuccessCount $successCount `
        -FailureCount $failureCount `
        -ElapsedValues @($elapsedValues) `
        -DurationSeconds $durationSeconds

    $startedAt = if ($null -ne $firstTimestamp) {
        [DateTimeOffset]::FromUnixTimeMilliseconds([int64] $firstTimestamp).ToString("o")
    }
    else {
        $null
    }

    $endedAt = if ($null -ne $lastTimestamp) {
        [DateTimeOffset]::FromUnixTimeMilliseconds([int64] $lastTimestamp).ToString("o")
    }
    else {
        $null
    }

    $summary = [ordered] @{
        schemaVersion = 1
        runId = $RunId
        scenario = $ScenarioName
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        startedAt = $startedAt
        endedAt = $endedAt
        durationSeconds = Get-JMeterEvidenceRoundedNumber -Value $durationSeconds
        baseUrl = $BaseUrl
        plan = Split-Path -Leaf $PlanPath
        planPath = $PlanPath
        jtlPath = $JtlPath
        htmlReportPath = $HtmlReportPath
        summaryJsonPath = $SummaryJsonPath
        summaryMarkdownPath = $SummaryMarkdownPath
        parameters = $Parameters
        metrics = $metrics
        failures = @($failures)
        labels = $labelSummaries
    }

    $summaryParent = Split-Path -Parent $SummaryJsonPath
    if (-not [string]::IsNullOrWhiteSpace($summaryParent)) {
        New-Item -ItemType Directory -Force -Path $summaryParent | Out-Null
    }
    $summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $SummaryJsonPath -Encoding UTF8

    $markdownLines = @(
        "# JMeter Scenario Summary",
        "",
        "- Run ID: $RunId",
        "- Scenario: $ScenarioName",
        "- Plan: $(Split-Path -Leaf $PlanPath)",
        "- Base URL: $BaseUrl",
        "- JTL: $JtlPath",
        "- HTML report: $(if ([string]::IsNullOrWhiteSpace($HtmlReportPath)) { 'not generated' } else { $HtmlReportPath })",
        "",
        "| Samples | Failures | Error % | Avg ms | P95 ms | P99 ms | Throughput/s |",
        "| ---: | ---: | ---: | ---: | ---: | ---: | ---: |",
        "| $($metrics.sampleCount) | $($metrics.failureCount) | $($metrics.errorRatePercent) | $($metrics.avgMs) | $($metrics.p95Ms) | $($metrics.p99Ms) | $($metrics.throughputPerSec) |"
    )

    if ($failures.Count -gt 0) {
        $markdownLines += @("", "## First Failures", "")
        foreach ($failure in $failures) {
            $message = if ([string]::IsNullOrWhiteSpace($failure.failureMessage)) { "(empty failure message)" } else { $failure.failureMessage }
            $markdownLines += "- $($failure.label) [$($failure.responseCode)] $message"
        }
    }

    $markdownLines += @("", "## Labels", "", "| Label | Samples | Failures | Avg ms | P95 ms |", "| --- | ---: | ---: | ---: | ---: |")
    foreach ($labelSummary in $labelSummaries) {
        $labelMetrics = $labelSummary.metrics
        $markdownLines += "| $($labelSummary.label) | $($labelMetrics.sampleCount) | $($labelMetrics.failureCount) | $($labelMetrics.avgMs) | $($labelMetrics.p95Ms) |"
    }

    Write-JMeterEvidenceFile -Path $SummaryMarkdownPath -Lines $markdownLines

    return $summary
}

function Write-JMeterRunSummary {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RunId,

        [Parameter(Mandatory = $true)]
        [string] $OutputDir,

        [Parameter(Mandatory = $true)]
        [string[]] $ScenarioSummaryPaths,

        [Parameter(Mandatory = $true)]
        [string] $SummaryJsonPath,

        [Parameter(Mandatory = $true)]
        [string] $SummaryMarkdownPath
    )

    $scenarioSummaries = @(
        foreach ($path in $ScenarioSummaryPaths) {
            if (Test-Path -LiteralPath $path) {
                Get-Content -Raw -LiteralPath $path | ConvertFrom-Json
            }
        }
    )

    $summary = [ordered] @{
        schemaVersion = 1
        runId = $RunId
        generatedAt = (Get-Date).ToUniversalTime().ToString("o")
        outputDir = $OutputDir
        scenarioCount = $scenarioSummaries.Count
        scenarios = @(
            foreach ($scenarioSummary in $scenarioSummaries) {
                [ordered] @{
                    scenario = $scenarioSummary.scenario
                    plan = $scenarioSummary.plan
                    jtlPath = $scenarioSummary.jtlPath
                    htmlReportPath = $scenarioSummary.htmlReportPath
                    summaryJsonPath = $scenarioSummary.summaryJsonPath
                    summaryMarkdownPath = $scenarioSummary.summaryMarkdownPath
                    metrics = $scenarioSummary.metrics
                    failures = $scenarioSummary.failures
                }
            }
        )
    }

    $summaryParent = Split-Path -Parent $SummaryJsonPath
    if (-not [string]::IsNullOrWhiteSpace($summaryParent)) {
        New-Item -ItemType Directory -Force -Path $summaryParent | Out-Null
    }
    $summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $SummaryJsonPath -Encoding UTF8

    $markdownLines = @(
        "# JMeter Evidence Summary",
        "",
        "- Run ID: $RunId",
        "- Output directory: $OutputDir",
        "",
        "| Scenario | Samples | Failures | Avg ms | P95 ms | Throughput/s | Summary |",
        "| --- | ---: | ---: | ---: | ---: | ---: | --- |"
    )

    foreach ($scenarioSummary in $scenarioSummaries) {
        $metrics = $scenarioSummary.metrics
        $markdownLines += "| $($scenarioSummary.scenario) | $($metrics.sampleCount) | $($metrics.failureCount) | $($metrics.avgMs) | $($metrics.p95Ms) | $($metrics.throughputPerSec) | $($scenarioSummary.summaryMarkdownPath) |"
    }

    Write-JMeterEvidenceFile -Path $SummaryMarkdownPath -Lines $markdownLines

    return $summary
}
