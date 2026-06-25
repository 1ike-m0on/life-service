<#
.SYNOPSIS
Runs durable fault-injection recovery verification for the local demo stack.

.DESCRIPTION
Builds a staged Redis, MySQL, and RocketMQ recovery run that continuously probes
backend health, core read/warmup APIs, and Redis/MySQL consistency checks.

The default duration is 600 seconds. Use -DurationSeconds 300 for a shorter
recovery run or -Smoke for a 30 second smoke run.

Non-destructive default behavior: without -ApplyFaults this script does not
pause, stop, or start containers. It still writes JSON and Markdown summaries
under tests/fault-injection/results.

When -ApplyFaults is used, the script only delegates to the existing availability
scripts. Those scripts pause/stop/start containers and never remove containers,
compose state, or volumes.

.EXAMPLE
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1 -PlanOnly

.EXAMPLE
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1 -DurationSeconds 300 -ApplyFaults -Force

.EXAMPLE
.\tests\fault-injection\scripts\Invoke-DurableRecoveryVerification.ps1 -Smoke -ApplyFaults -Force
#>
[CmdletBinding()]
param(
    [ValidateRange(30, 3600)]
    [int] $DurationSeconds = 600,

    [switch] $Smoke,

    [ValidateSet("Redis", "MySQL", "RocketMQ")]
    [string[]] $FaultTargets = @("Redis", "MySQL", "RocketMQ"),

    [ValidateNotNullOrEmpty()]
    [string] $BaseUrl = "http://localhost:8081",

    [ValidateRange(1, [long]::MaxValue)]
    [long] $VoucherId = 1001,

    [ValidateRange(1, [long]::MaxValue)]
    [long] $MerchantId = 1,

    [ValidateSet("Pause", "Stop")]
    [string] $RedisFaultAction = "Pause",

    [ValidateRange(0, 3600)]
    [int] $FaultHoldSeconds = 0,

    [ValidateRange(0, 300)]
    [int] $ProbeIntervalSeconds = 0,

    [ValidateRange(0, 3600)]
    [int] $ConsistencyIntervalSeconds = 0,

    [ValidateRange(1, 120)]
    [int] $HttpTimeoutSeconds = 5,

    [string] $AuthToken = "",

    [switch] $IncludeOrderProbe,

    [ValidateNotNullOrEmpty()]
    [string] $RedisContainer = "life-service-redis",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlContainer = "life-service-mysql",

    [ValidateNotNullOrEmpty()]
    [string] $RocketMqBrokerContainer = "life-service-rocketmq-broker",

    [ValidateNotNullOrEmpty()]
    [string] $BackendContainer = "life-service-backend",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlDatabase = "life_service",

    [ValidateNotNullOrEmpty()]
    [string] $MysqlUser = "root",

    [AllowEmptyString()]
    [string] $MysqlPassword = "root",

    [string] $ResultsRoot = "",

    [string] $RunName = "",

    [switch] $ApplyFaults,

    [switch] $Force,

    [switch] $PlanOnly,

    [switch] $SkipHttpProbes,

    [switch] $SkipConsistency
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Smoke) {
    $DurationSeconds = 30
}

if ($FaultTargets.Count -eq 0) {
    throw "At least one fault target is required."
}

if ($IncludeOrderProbe -and [string]::IsNullOrWhiteSpace($AuthToken)) {
    throw "-IncludeOrderProbe requires -AuthToken because order endpoints are authenticated."
}

if ([string]::IsNullOrWhiteSpace($ResultsRoot)) {
    $ResultsRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot "..\results"))
} else {
    $ResultsRoot = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($ResultsRoot)
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($RunName)) {
    $RunName = "durable-recovery-$timestamp"
}

$script:RunDirectory = Join-Path $ResultsRoot $RunName
$null = New-Item -ItemType Directory -Force -Path $script:RunDirectory

$script:ProbeRecords = @()
$script:ConsistencyRecords = @()
$script:ActionRecords = @()
$script:PhaseRecords = @()
$script:ActiveFaults = @{}
$script:RunError = $null
$script:RunSummary = $null
$script:LastConsistencyUtc = [DateTime]::MinValue
$script:ConsistencySequence = 0
$script:ActionSequence = 0

function Get-PowerShellExecutable {
    if ($PSVersionTable.PSEdition -eq "Core") {
        return "pwsh"
    }

    $windowsPowerShell = Join-Path $PSHOME "powershell.exe"
    if (Test-Path -LiteralPath $windowsPowerShell) {
        return $windowsPowerShell
    }

    return "powershell"
}

$script:PowerShellExe = Get-PowerShellExecutable

function Limit-Text {
    param(
        [AllowNull()]
        [object] $Value,

        [ValidateRange(1, 20000)]
        [int] $MaxChars = 2000
    )

    if ($null -eq $Value) {
        return ""
    }

    $text = ""
    if ($Value -is [array]) {
        $text = ($Value | ForEach-Object { "$_" }) -join [Environment]::NewLine
    } else {
        $text = "$Value"
    }

    if ($text.Length -gt $MaxChars) {
        return $text.Substring(0, $MaxChars) + "...(truncated)"
    }

    return $text
}

function Format-CommandLine {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments
    )

    $parts = @($FilePath) + ($Arguments | ForEach-Object {
        if ($_ -match "\s") { '"' + $_ + '"' } else { $_ }
    })
    return $parts -join " "
}

function New-PhasePlan {
    param(
        [Parameter(Mandatory = $true)]
        [int] $TotalSeconds,

        [Parameter(Mandatory = $true)]
        [string[]] $Targets,

        [Parameter(Mandatory = $true)]
        [int] $ConfiguredFaultHoldSeconds
    )

    $targetCount = $Targets.Count
    if ($TotalSeconds -le 60) {
        $baselineSeconds = 3
        $finalSeconds = 3
    } elseif ($TotalSeconds -le 300) {
        $baselineSeconds = 20
        $finalSeconds = 30
    } else {
        $baselineSeconds = 30
        $finalSeconds = 60
    }

    $minimumTargetWindow = 2 * $targetCount
    if (($baselineSeconds + $finalSeconds + $minimumTargetWindow) -gt $TotalSeconds) {
        $baselineSeconds = [Math]::Max(1, [Math]::Floor($TotalSeconds * 0.1))
        $finalSeconds = [Math]::Max(1, [Math]::Floor($TotalSeconds * 0.1))
    }

    $remainingSeconds = $TotalSeconds - $baselineSeconds - $finalSeconds
    if ($remainingSeconds -lt $minimumTargetWindow) {
        $remainingSeconds = $minimumTargetWindow
    }

    $perTargetSeconds = [Math]::Max(2, [Math]::Floor($remainingSeconds / $targetCount))
    $autoFaultSeconds = [Math]::Max(1, [Math]::Floor($perTargetSeconds * 0.30))
    if ($ConfiguredFaultHoldSeconds -gt 0) {
        $faultSeconds = [Math]::Min($ConfiguredFaultHoldSeconds, [Math]::Max(1, $perTargetSeconds - 1))
    } else {
        $faultSeconds = $autoFaultSeconds
    }
    $recoverySeconds = [Math]::Max(1, $perTargetSeconds - $faultSeconds)

    $phases = @()
    $phases += [ordered]@{
        name = "baseline"
        target = $null
        action = "Probe"
        durationSeconds = $baselineSeconds
    }

    foreach ($target in $Targets) {
        $phases += [ordered]@{
            name = "$target-fault"
            target = $target
            action = "Inject"
            durationSeconds = $faultSeconds
        }
        $phases += [ordered]@{
            name = "$target-recovery"
            target = $target
            action = "Recover"
            durationSeconds = $recoverySeconds
        }
    }

    $phases += [ordered]@{
        name = "final-steady"
        target = $null
        action = "Probe"
        durationSeconds = $finalSeconds
    }

    $plannedSeconds = ($phases | ForEach-Object { $_.durationSeconds } | Measure-Object -Sum).Sum
    $delta = $TotalSeconds - $plannedSeconds
    if ($delta -ne 0) {
        $phases[$phases.Count - 1].durationSeconds = [Math]::Max(1, $phases[$phases.Count - 1].durationSeconds + $delta)
    }

    return $phases
}

function Get-EffectiveProbeIntervalSeconds {
    if ($ProbeIntervalSeconds -gt 0) {
        return $ProbeIntervalSeconds
    }

    if ($DurationSeconds -le 60) {
        return 3
    }

    if ($DurationSeconds -le 300) {
        return 5
    }

    return 10
}

function Get-EffectiveConsistencyIntervalSeconds {
    param([int] $EffectiveProbeIntervalSeconds)

    if ($ConsistencyIntervalSeconds -gt 0) {
        return $ConsistencyIntervalSeconds
    }

    if ($DurationSeconds -le 60) {
        return 10
    }

    if ($DurationSeconds -le 300) {
        return 30
    }

    return [Math]::Max(60, $EffectiveProbeIntervalSeconds * 3)
}

function Confirm-ApplyFaults {
    if (-not $ApplyFaults) {
        Write-Warning "Probe-only mode: no containers will be paused, stopped, or started. Pass -ApplyFaults to inject faults."
        return
    }

    Write-Warning "Fault injection will temporarily pause or stop selected local demo containers."
    Write-Warning "This script does not remove containers, compose state, or volumes, but the app will be unavailable during fault windows."
    Write-Warning "Use only against a disposable/local demo stack."

    if ($IncludeOrderProbe) {
        Write-Warning "-IncludeOrderProbe can create or reuse voucher orders for the authenticated user."
    }

    if ($Force) {
        return
    }

    $answer = Read-Host "Type FAULT-RECOVERY to continue"
    if ($answer -ne "FAULT-RECOVERY") {
        throw "Canceled by user."
    }
}

function Invoke-ChildPowerShell {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ScriptPath,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $Name
    )

    $argumentList = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $ScriptPath) + $Arguments
    $commandLine = Format-CommandLine -FilePath $script:PowerShellExe -Arguments $argumentList
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& $script:PowerShellExe @argumentList 2>&1)
        $exitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($null -eq $exitCode) {
        $exitCode = 0
    }

    return [ordered]@{
        name = $Name
        commandLine = $commandLine
        exitCode = $exitCode
        success = ($exitCode -eq 0)
        output = Limit-Text -Value $output -MaxChars 3000
    }
}

function Get-FaultScriptInvocation {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("Redis", "MySQL", "RocketMQ")]
        [string] $Target,

        [Parameter(Mandatory = $true)]
        [ValidateSet("Inject", "Recover")]
        [string] $ActionKind
    )

    if ($Target -eq "Redis") {
        $scriptPath = Join-Path $PSScriptRoot "Set-RedisAvailability.ps1"
        if ($ActionKind -eq "Inject") {
            $action = $RedisFaultAction
        } elseif ($RedisFaultAction -eq "Pause") {
            $action = "Unpause"
        } else {
            $action = "Start"
        }

        $arguments = @("-Action", $action, "-RedisContainer", $RedisContainer)
    } elseif ($Target -eq "MySQL") {
        $scriptPath = Join-Path $PSScriptRoot "Set-MysqlAvailability.ps1"
        if ($ActionKind -eq "Inject") {
            $action = "Stop"
        } else {
            $action = "Start"
        }

        $arguments = @(
            "-Action", $action,
            "-MysqlContainer", $MysqlContainer,
            "-BackendContainer", $BackendContainer
        )
    } else {
        $scriptPath = Join-Path $PSScriptRoot "Set-RocketMqBrokerAvailability.ps1"
        if ($ActionKind -eq "Inject") {
            $action = "Stop"
        } else {
            $action = "Start"
        }

        $arguments = @(
            "-Action", $action,
            "-BrokerContainer", $RocketMqBrokerContainer,
            "-BackendContainer", $BackendContainer
        )
    }

    if ($ApplyFaults) {
        $arguments += "-Force"
    } else {
        $arguments += "-DryRun"
    }

    return [ordered]@{
        scriptPath = $scriptPath
        arguments = $arguments
        delegatedAction = $action
    }
}

function Invoke-FaultAction {
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet("Redis", "MySQL", "RocketMQ")]
        [string] $Target,

        [Parameter(Mandatory = $true)]
        [ValidateSet("Inject", "Recover")]
        [string] $ActionKind,

        [Parameter(Mandatory = $true)]
        [string] $PhaseName
    )

    $script:ActionSequence++
    $invocation = Get-FaultScriptInvocation -Target $Target -ActionKind $ActionKind
    $record = [ordered]@{
        sequence = $script:ActionSequence
        timestampUtc = [DateTime]::UtcNow.ToString("o")
        phase = $PhaseName
        target = $Target
        actionKind = $ActionKind
        delegatedAction = $invocation.delegatedAction
        applyFaults = [bool] $ApplyFaults
        success = $false
        exitCode = $null
        commandLine = $null
        outputPath = $null
        outputPreview = ""
    }

    if ($PlanOnly) {
        $record.success = $true
        $record.outputPreview = "PLAN ONLY: delegated action was not invoked."
        $script:ActionRecords += $record
        return $record
    }

    $result = Invoke-ChildPowerShell -ScriptPath $invocation.scriptPath -Arguments $invocation.arguments -Name "$Target $ActionKind"
    $logPath = Join-Path $script:RunDirectory ("action-{0:000}-{1}-{2}.log" -f $record.sequence, $Target.ToLowerInvariant(), $ActionKind.ToLowerInvariant())
    Set-Content -LiteralPath $logPath -Value $result.output -Encoding UTF8

    $record.success = [bool] $result.success
    $record.exitCode = $result.exitCode
    $record.commandLine = $result.commandLine
    $record.outputPath = $logPath
    $record.outputPreview = Limit-Text -Value $result.output -MaxChars 500
    $script:ActionRecords += $record

    if ($record.success -and $ApplyFaults) {
        if ($ActionKind -eq "Inject") {
            $script:ActiveFaults[$Target] = $true
        } else {
            $script:ActiveFaults.Remove($Target)
        }
    }

    if (-not $record.success) {
        throw "$Target $ActionKind failed with exit code $($record.exitCode). See $logPath"
    }

    return $record
}

function New-HttpHeaders {
    $headers = @{}
    if (-not [string]::IsNullOrWhiteSpace($AuthToken)) {
        if ($AuthToken.StartsWith("Bearer ")) {
            $headers["Authorization"] = $AuthToken
        } else {
            $headers["Authorization"] = "Bearer $AuthToken"
        }
    }
    return $headers
}

function Get-ProbeDefinitions {
    $normalizedBaseUrl = $BaseUrl.TrimEnd("/")
    $definitions = @(
        [ordered]@{
            name = "health"
            method = "GET"
            uri = "$normalizedBaseUrl/actuator/health"
            expectation = "HealthUp"
            body = $null
        },
        [ordered]@{
            name = "merchant-page"
            method = "GET"
            uri = "$normalizedBaseUrl/api/v1/merchants?pageNo=1&pageSize=5"
            expectation = "ApiSuccess"
            body = $null
        },
        [ordered]@{
            name = "merchant-detail"
            method = "GET"
            uri = "$normalizedBaseUrl/api/v1/merchants/$MerchantId"
            expectation = "ApiSuccess"
            body = $null
        },
        [ordered]@{
            name = "merchant-vouchers"
            method = "GET"
            uri = "$normalizedBaseUrl/api/v1/merchants/$MerchantId/vouchers"
            expectation = "ApiSuccess"
            body = $null
        },
        [ordered]@{
            name = "flash-sale-warmup"
            method = "POST"
            uri = "$normalizedBaseUrl/api/v1/flash-sale-vouchers/$VoucherId/warmup"
            expectation = "ApiSuccess"
            body = $null
        }
    )

    if (-not [string]::IsNullOrWhiteSpace($AuthToken)) {
        $definitions += [ordered]@{
            name = "my-voucher-orders"
            method = "GET"
            uri = "$normalizedBaseUrl/api/v1/users/me/voucher-orders?pageNo=1&pageSize=5"
            expectation = "ApiSuccess"
            body = $null
        }
    }

    if ($IncludeOrderProbe) {
        $definitions += [ordered]@{
            name = "flash-sale-order"
            method = "POST"
            uri = "$normalizedBaseUrl/api/v1/flash-sale-vouchers/$VoucherId/orders"
            expectation = "ApiReachable"
            body = $null
        }
    }

    return $definitions
}

function Test-ProbeExpectation {
    param(
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [object] $Json,

        [Parameter(Mandatory = $true)]
        [string] $Expectation,

        [Parameter(Mandatory = $true)]
        [int] $StatusCode
    )

    if ($StatusCode -lt 200 -or $StatusCode -ge 300) {
        return $false
    }

    if ($Expectation -eq "HealthUp") {
        if ($null -eq $Json) {
            return $true
        }
        if ($Json.PSObject.Properties.Name -contains "status") {
            return ($Json.status -eq "UP")
        }
        return $true
    }

    if ($Expectation -eq "ApiSuccess") {
        if ($null -eq $Json) {
            return $false
        }
        if ($Json.PSObject.Properties.Name -contains "success") {
            return [bool] $Json.success
        }
        return $false
    }

    if ($Expectation -eq "ApiReachable") {
        if ($null -eq $Json) {
            return $true
        }
        if ($Json.PSObject.Properties.Name -contains "code") {
            return ($Json.code -ne "SYSTEM_ERROR")
        }
        return $true
    }

    return $false
}

function Invoke-HttpProbe {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary] $Definition,

        [Parameter(Mandatory = $true)]
        [string] $PhaseName,

        [ValidateRange(1, 120)]
        [int] $TimeoutSeconds = $HttpTimeoutSeconds
    )

    $startedUtc = [DateTime]::UtcNow
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    $statusCode = 0
    $content = ""
    $json = $null
    $errorText = ""
    $success = $false

    try {
        $headers = New-HttpHeaders
        $parameters = @{
            Uri = $Definition.uri
            Method = $Definition.method
            TimeoutSec = $TimeoutSeconds
            Headers = $headers
            UseBasicParsing = $true
        }
        if ($Definition.method -eq "POST") {
            $parameters.ContentType = "application/json"
        }

        $response = Invoke-WebRequest @parameters
        $statusCode = [int] $response.StatusCode
        if ($response.Content -is [byte[]]) {
            $content = [System.Text.Encoding]::UTF8.GetString($response.Content)
        } else {
            $content = [string] $response.Content
        }
    } catch {
        $errorText = $_.Exception.Message
        if ($_.Exception.Response -and ($_.Exception.Response.PSObject.Properties.Name -contains "StatusCode")) {
            $statusCode = [int] $_.Exception.Response.StatusCode
        }
    } finally {
        $watch.Stop()
    }

    if (-not [string]::IsNullOrWhiteSpace($content)) {
        try {
            $json = $content | ConvertFrom-Json
        } catch {
            $errorText = "JSON parse failed: $($_.Exception.Message)"
        }
    }

    if ($errorText -eq "") {
        $success = Test-ProbeExpectation -Json $json -Expectation $Definition.expectation -StatusCode $statusCode
    }

    $code = $null
    $message = $null
    if ($null -ne $json) {
        if ($json.PSObject.Properties.Name -contains "code") {
            $code = $json.code
        }
        if ($json.PSObject.Properties.Name -contains "message") {
            $message = $json.message
        }
    }

    $record = [ordered]@{
        timestampUtc = $startedUtc.ToString("o")
        phase = $PhaseName
        name = $Definition.name
        method = $Definition.method
        uri = $Definition.uri
        expectation = $Definition.expectation
        success = [bool] $success
        statusCode = $statusCode
        durationMs = [int] $watch.ElapsedMilliseconds
        code = $code
        message = $message
        error = $errorText
    }
    $script:ProbeRecords += $record
    return $record
}

function Invoke-ConsistencyCheck {
    param(
        [Parameter(Mandatory = $true)]
        [string] $PhaseName,

        [switch] $ForceRun
    )

    if ($SkipConsistency) {
        return $null
    }

    $now = [DateTime]::UtcNow
    if ((-not $ForceRun) -and $script:LastConsistencyUtc -ne [DateTime]::MinValue) {
        $elapsed = ($now - $script:LastConsistencyUtc).TotalSeconds
        if ($elapsed -lt $script:EffectiveConsistencyIntervalSeconds) {
            return $null
        }
    }

    $script:LastConsistencyUtc = $now
    $script:ConsistencySequence++
    $acceptanceScript = Join-Path $PSScriptRoot "Invoke-FaultInjectionAcceptance.ps1"
    $arguments = @(
        "-VoucherId", "$VoucherId",
        "-RedisContainer", $RedisContainer,
        "-MysqlContainer", $MysqlContainer,
        "-MysqlDatabase", $MysqlDatabase,
        "-MysqlUser", $MysqlUser,
        "-MysqlPassword", $MysqlPassword
    )

    $record = [ordered]@{
        sequence = $script:ConsistencySequence
        timestampUtc = $now.ToString("o")
        phase = $PhaseName
        voucherId = $VoucherId
        success = $false
        exitCode = $null
        commandLine = $null
        outputPath = $null
        outputPreview = ""
    }

    $result = Invoke-ChildPowerShell -ScriptPath $acceptanceScript -Arguments $arguments -Name "Consistency check"
    $logPath = Join-Path $script:RunDirectory ("consistency-{0:000}-{1}.log" -f $record.sequence, ($PhaseName -replace "[^a-zA-Z0-9-]", "-"))
    Set-Content -LiteralPath $logPath -Value $result.output -Encoding UTF8

    $record.success = [bool] $result.success
    $record.exitCode = $result.exitCode
    $record.commandLine = $result.commandLine
    $record.outputPath = $logPath
    $record.outputPreview = Limit-Text -Value $result.output -MaxChars 700
    $script:ConsistencyRecords += $record
    return $record
}

function Invoke-ProbeCycle {
    param(
        [Parameter(Mandatory = $true)]
        [string] $PhaseName,

        [DateTime] $DeadlineUtc = [DateTime]::MaxValue
    )

    if (-not $SkipHttpProbes) {
        foreach ($definition in (Get-ProbeDefinitions)) {
            $remainingSeconds = [Math]::Floor(($DeadlineUtc - [DateTime]::UtcNow).TotalSeconds)
            if ($remainingSeconds -lt 1) {
                break
            }
            $timeoutForProbe = [Math]::Max(1, [Math]::Min($HttpTimeoutSeconds, $remainingSeconds))
            $null = Invoke-HttpProbe -Definition $definition -PhaseName $PhaseName -TimeoutSeconds $timeoutForProbe
        }
    }

    if ($DeadlineUtc -ne [DateTime]::MaxValue -and ([DateTime]::UtcNow -ge $DeadlineUtc)) {
        return
    }

    $null = Invoke-ConsistencyCheck -PhaseName $PhaseName
}

function Invoke-Phase {
    param(
        [Parameter(Mandatory = $true)]
        [System.Collections.IDictionary] $Phase,

        [Parameter(Mandatory = $true)]
        [int] $EffectiveProbeIntervalSeconds
    )

    $startedUtc = [DateTime]::UtcNow
    Write-Host ("[{0}] phase={1} action={2} target={3} duration={4}s" -f `
        (Get-Date -Format "HH:mm:ss"), $Phase.name, $Phase.action, $Phase.target, $Phase.durationSeconds)

    $phaseRecord = [ordered]@{
        name = $Phase.name
        target = $Phase.target
        action = $Phase.action
        plannedDurationSeconds = $Phase.durationSeconds
        startedUtc = $startedUtc.ToString("o")
        endedUtc = $null
        actionSuccess = $null
        probeCount = 0
        failedProbeCount = 0
        consistencyCount = 0
        failedConsistencyCount = 0
    }

    if ($Phase.action -eq "Inject" -or $Phase.action -eq "Recover") {
        $actionRecord = Invoke-FaultAction -Target $Phase.target -ActionKind $Phase.action -PhaseName $Phase.name
        $phaseRecord.actionSuccess = [bool] $actionRecord.success
    }

    $deadlineUtc = $startedUtc.AddSeconds($Phase.durationSeconds)
    do {
        $probeCountBefore = $script:ProbeRecords.Count
        $consistencyCountBefore = $script:ConsistencyRecords.Count
        Invoke-ProbeCycle -PhaseName $Phase.name -DeadlineUtc $deadlineUtc

        if ($script:ProbeRecords.Count -gt $probeCountBefore) {
            $newProbes = @($script:ProbeRecords[$probeCountBefore..($script:ProbeRecords.Count - 1)])
        } else {
            $newProbes = @()
        }
        if ($script:ConsistencyRecords.Count -gt $consistencyCountBefore) {
            $newConsistency = @($script:ConsistencyRecords[$consistencyCountBefore..($script:ConsistencyRecords.Count - 1)])
        } else {
            $newConsistency = @()
        }

        $phaseRecord.probeCount += $newProbes.Count
        $phaseRecord.failedProbeCount += @($newProbes | Where-Object { -not $_.success }).Count
        $phaseRecord.consistencyCount += $newConsistency.Count
        $phaseRecord.failedConsistencyCount += @($newConsistency | Where-Object { -not $_.success }).Count

        $remainingSeconds = [Math]::Ceiling(($deadlineUtc - [DateTime]::UtcNow).TotalSeconds)
        if ($remainingSeconds -le 0) {
            break
        }

        Start-Sleep -Seconds ([Math]::Min($EffectiveProbeIntervalSeconds, $remainingSeconds))
    } while ([DateTime]::UtcNow -lt $deadlineUtc)

    $phaseRecord.endedUtc = [DateTime]::UtcNow.ToString("o")
    $script:PhaseRecords += $phaseRecord
}

function Recover-ActiveFaults {
    if (-not $ApplyFaults) {
        return
    }

    $activeTargets = @($script:ActiveFaults.Keys)
    foreach ($target in $activeTargets) {
        try {
            Write-Warning "Attempting cleanup recovery for active $target fault."
            $null = Invoke-FaultAction -Target $target -ActionKind "Recover" -PhaseName "cleanup-recovery"
        } catch {
            Write-Warning "Cleanup recovery for $target failed: $($_.Exception.Message)"
        }
    }
}

function Get-LatestProbeFailures {
    $latestByName = @{}
    foreach ($probe in $script:ProbeRecords) {
        $latestByName[$probe.name] = $probe
    }

    return @($latestByName.Values | Where-Object { -not $_.success })
}

function Complete-RunSummary {
    param(
        [Parameter(Mandatory = $true)]
        [object[]] $PhasePlan
    )

    $failedActions = @($script:ActionRecords | Where-Object { -not $_.success })
    $failedProbes = @($script:ProbeRecords | Where-Object { -not $_.success })
    $latestProbeFailures = @(Get-LatestProbeFailures)
    $failedConsistency = @($script:ConsistencyRecords | Where-Object { -not $_.success })
    $finalConsistency = $null
    if ($script:ConsistencyRecords.Count -gt 0) {
        $finalConsistency = $script:ConsistencyRecords[$script:ConsistencyRecords.Count - 1]
    }

    $result = "PASS"
    if ($PlanOnly) {
        $result = "PLAN_ONLY"
    } elseif (-not $ApplyFaults) {
        if ($latestProbeFailures.Count -gt 0 -or ($null -ne $finalConsistency -and -not $finalConsistency.success)) {
            $result = "PROBE_ONLY_FAIL"
        } else {
            $result = "PROBE_ONLY_PASS"
        }
    } elseif ($failedActions.Count -gt 0 -or $latestProbeFailures.Count -gt 0 -or ($null -ne $finalConsistency -and -not $finalConsistency.success)) {
        $result = "FAIL"
    }

    if ($null -ne $script:RunError -and $result -ne "PLAN_ONLY") {
        $result = "FAIL"
    }

    $finalConsistencySuccess = $null
    if ($null -ne $finalConsistency) {
        $finalConsistencySuccess = [bool] $finalConsistency.success
    }

    $script:RunSummary = [ordered]@{
        result = $result
        runError = $script:RunError
        durationSeconds = $DurationSeconds
        applyFaults = [bool] $ApplyFaults
        planOnly = [bool] $PlanOnly
        faultTargets = @($FaultTargets)
        phasePlan = $PhasePlan
        phaseRecords = $script:PhaseRecords
        actionCount = $script:ActionRecords.Count
        failedActionCount = $failedActions.Count
        probeCount = $script:ProbeRecords.Count
        failedProbeCount = $failedProbes.Count
        latestProbeFailureCount = $latestProbeFailures.Count
        consistencyCount = $script:ConsistencyRecords.Count
        failedConsistencyCount = $failedConsistency.Count
        finalConsistencySuccess = $finalConsistencySuccess
    }
}

function Write-RunReports {
    param(
        [Parameter(Mandatory = $true)]
        [object[]] $PhasePlan
    )

    $jsonPath = Join-Path $script:RunDirectory "summary.json"
    $markdownPath = Join-Path $script:RunDirectory "summary.md"

    $payload = [ordered]@{
        run = [ordered]@{
            name = $RunName
            startedUtc = $script:StartedUtc
            endedUtc = [DateTime]::UtcNow.ToString("o")
            baseUrl = $BaseUrl
            voucherId = $VoucherId
            merchantId = $MerchantId
            resultsDirectory = $script:RunDirectory
        }
        parameters = [ordered]@{
            durationSeconds = $DurationSeconds
            smoke = [bool] $Smoke
            faultTargets = @($FaultTargets)
            redisFaultAction = $RedisFaultAction
            faultHoldSeconds = $FaultHoldSeconds
            probeIntervalSeconds = $script:EffectiveProbeIntervalSeconds
            consistencyIntervalSeconds = $script:EffectiveConsistencyIntervalSeconds
            applyFaults = [bool] $ApplyFaults
            force = [bool] $Force
            includeOrderProbe = [bool] $IncludeOrderProbe
            skipHttpProbes = [bool] $SkipHttpProbes
            skipConsistency = [bool] $SkipConsistency
        }
        summary = $script:RunSummary
        phasePlan = $PhasePlan
        phases = $script:PhaseRecords
        actions = $script:ActionRecords
        probes = $script:ProbeRecords
        consistencyChecks = $script:ConsistencyRecords
    }

    $payload | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $jsonPath -Encoding UTF8

    $lines = @()
    $lines += "# Durable fault-injection recovery verification"
    $lines += ""
    $lines += "- Result: $($script:RunSummary.result)"
    $lines += "- Started UTC: $($script:StartedUtc)"
    $lines += "- Ended UTC: $([DateTime]::UtcNow.ToString("o"))"
    $lines += "- Duration: $DurationSeconds second(s)"
    $lines += "- Base URL: $BaseUrl"
    $lines += "- Fault targets: $($FaultTargets -join ', ')"
    $lines += "- Apply faults: $ApplyFaults"
    $lines += "- Probe interval: $($script:EffectiveProbeIntervalSeconds) second(s)"
    $lines += "- Consistency interval: $($script:EffectiveConsistencyIntervalSeconds) second(s)"
    $lines += ""
    $lines += "## Safety"
    $lines += ""
    if ($ApplyFaults) {
        $lines += "Faults were applied by delegating to Set-RedisAvailability.ps1, Set-MysqlAvailability.ps1, and Set-RocketMqBrokerAvailability.ps1. No compose down, container removal, or volume removal is performed."
    } else {
        $lines += "Probe-only mode was used. No containers were paused, stopped, or started."
    }
    if ($IncludeOrderProbe) {
        $lines += "Order probe was enabled and may create or reuse voucher orders for the authenticated user."
    }
    $lines += ""
    $lines += "## Phase Plan"
    $lines += ""
    $lines += "| Phase | Action | Target | Planned seconds |"
    $lines += "| --- | --- | --- | ---: |"
    foreach ($phase in $PhasePlan) {
        $target = if ($null -eq $phase.target) { "" } else { $phase.target }
        $lines += "| $($phase.name) | $($phase.action) | $target | $($phase.durationSeconds) |"
    }
    $lines += ""
    $lines += "## Phase Results"
    $lines += ""
    $lines += "| Phase | Probes | Failed probes | Consistency checks | Failed consistency |"
    $lines += "| --- | ---: | ---: | ---: | ---: |"
    foreach ($phaseRecord in $script:PhaseRecords) {
        $lines += "| $($phaseRecord.name) | $($phaseRecord.probeCount) | $($phaseRecord.failedProbeCount) | $($phaseRecord.consistencyCount) | $($phaseRecord.failedConsistencyCount) |"
    }
    $lines += ""
    $lines += "## Summary Counts"
    $lines += ""
    $lines += "- Actions: $($script:RunSummary.actionCount), failed: $($script:RunSummary.failedActionCount)"
    $lines += "- Probes: $($script:RunSummary.probeCount), failed total: $($script:RunSummary.failedProbeCount), latest failed: $($script:RunSummary.latestProbeFailureCount)"
    $lines += "- Consistency checks: $($script:RunSummary.consistencyCount), failed: $($script:RunSummary.failedConsistencyCount), final success: $($script:RunSummary.finalConsistencySuccess)"
    if ($null -ne $script:RunError) {
        $lines += "- Run error: $script:RunError"
    }
    $lines += ""
    $lines += "## Output Files"
    $lines += ""
    $lines += "- JSON: $jsonPath"
    $lines += "- Markdown: $markdownPath"
    foreach ($record in $script:ActionRecords) {
        if (-not [string]::IsNullOrWhiteSpace($record.outputPath)) {
            $lines += "- Action log: $($record.outputPath)"
        }
    }
    foreach ($record in $script:ConsistencyRecords) {
        if (-not [string]::IsNullOrWhiteSpace($record.outputPath)) {
            $lines += "- Consistency log: $($record.outputPath)"
        }
    }

    Set-Content -LiteralPath $markdownPath -Value $lines -Encoding UTF8

    $script:SummaryJsonPath = $jsonPath
    $script:SummaryMarkdownPath = $markdownPath
}

$script:StartedUtc = [DateTime]::UtcNow.ToString("o")
$script:EffectiveProbeIntervalSeconds = Get-EffectiveProbeIntervalSeconds
$script:EffectiveConsistencyIntervalSeconds = Get-EffectiveConsistencyIntervalSeconds -EffectiveProbeIntervalSeconds $script:EffectiveProbeIntervalSeconds
$phasePlan = @(New-PhasePlan -TotalSeconds $DurationSeconds -Targets $FaultTargets -ConfiguredFaultHoldSeconds $FaultHoldSeconds)

Write-Host "Durable recovery verification"
Write-Host "  Run directory: $script:RunDirectory"
Write-Host "  Duration: $DurationSeconds second(s)"
Write-Host "  Targets: $($FaultTargets -join ', ')"
Write-Host "  Apply faults: $ApplyFaults"
Write-Host "  Plan only: $PlanOnly"

$exitCode = 0

try {
    if ($PlanOnly) {
        Write-Host "Plan only requested. No probes or fault actions will be executed."
    } else {
        Confirm-ApplyFaults
        foreach ($phase in $phasePlan) {
            Invoke-Phase -Phase $phase -EffectiveProbeIntervalSeconds $script:EffectiveProbeIntervalSeconds
        }
    }
} catch {
    $script:RunError = $_.Exception.Message
    $exitCode = 1
    Write-Warning "Run failed: $script:RunError"
} finally {
    try {
        Recover-ActiveFaults
        if (-not $PlanOnly -and -not $SkipConsistency) {
            $null = Invoke-ConsistencyCheck -PhaseName "final" -ForceRun
        }
    } catch {
        $script:RunError = "Finalization failed: $($_.Exception.Message)"
        $exitCode = 1
        Write-Warning $script:RunError
    }

    Complete-RunSummary -PhasePlan $phasePlan
    Write-RunReports -PhasePlan $phasePlan

    Write-Host "Result: $($script:RunSummary.result)"
    Write-Host "JSON summary: $script:SummaryJsonPath"
    Write-Host "Markdown summary: $script:SummaryMarkdownPath"
}

if ($script:RunSummary.result -eq "FAIL" -or $script:RunSummary.result -eq "PROBE_ONLY_FAIL") {
    $exitCode = 1
}

exit $exitCode
