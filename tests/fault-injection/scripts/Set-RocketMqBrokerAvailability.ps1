<#
.SYNOPSIS
Injects or recovers RocketMQ broker availability faults.

.DESCRIPTION
Stops or starts only the broker container. Namesrv, MySQL, Redis, backend,
volumes, and compose state are not removed.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Stop", "Start", "Status")]
    [string] $Action,

    [ValidateNotNullOrEmpty()]
    [string] $BrokerContainer = "life-service-rocketmq-broker",

    [ValidateNotNullOrEmpty()]
    [string] $BackendContainer = "life-service-backend",

    [switch] $Force,

    [switch] $DryRun
)

$ErrorActionPreference = "Stop"

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

function Invoke-CheckedNativeCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $ActionName
    )

    & $FilePath @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$ActionName failed with exit code $LASTEXITCODE."
    }
}

function Confirm-FaultAction {
    if ($DryRun -or $Force) {
        return
    }

    $answer = Read-Host "RocketMQ broker will be stopped. Type STOP to continue"
    if ($answer -ne "STOP") {
        Write-Host "Canceled."
        exit 0
    }
}

function Invoke-DockerAction {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [string] $ActionName
    )

    if ($DryRun) {
        Write-Host "DRY RUN: $(Format-CommandLine -FilePath "docker" -Arguments $Arguments)"
        return
    }

    Invoke-CheckedNativeCommand -FilePath "docker" -Arguments $Arguments -ActionName $ActionName
}

switch ($Action) {
    "Stop" {
        Confirm-FaultAction
        Invoke-DockerAction -Arguments @("stop", $BrokerContainer) -ActionName "Stopping RocketMQ broker"
        if ($DryRun) {
            Write-Host "DRY RUN: RocketMQ broker state was not changed."
            return
        }
        Write-Host "RocketMQ broker stopped. Recover with: .\tests\fault-injection\scripts\Set-RocketMqBrokerAvailability.ps1 -Action Start"
    }
    "Start" {
        Invoke-DockerAction -Arguments @("start", $BrokerContainer) -ActionName "Starting RocketMQ broker"
        if ($DryRun) {
            Write-Host "DRY RUN: RocketMQ broker state was not changed."
            return
        }
        Write-Host "RocketMQ broker start requested. Wait for broker registration and producer reconnect."
        Write-Host "Suggested checks:"
        Write-Host "  docker logs --tail 100 $BrokerContainer"
        Write-Host "  docker logs --tail 100 $BackendContainer"
    }
    "Status" {
        Invoke-DockerAction -Arguments @(
            "ps",
            "-a",
            "--filter",
            "name=$BrokerContainer",
            "--format",
            "table {{.Names}}\t{{.Status}}"
        ) -ActionName "Reading RocketMQ broker status"
    }
}
