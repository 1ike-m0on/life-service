<#
.SYNOPSIS
Injects or recovers Redis container availability faults.

.DESCRIPTION
Supports pause/unpause and stop/start for the Redis container. This script never
runs docker compose down, removes containers, or removes volumes.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Pause", "Unpause", "Stop", "Start", "Status")]
    [string] $Action,

    [ValidateNotNullOrEmpty()]
    [string] $RedisContainer = "life-service-redis",

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
    param(
        [Parameter(Mandatory = $true)]
        [string] $ExpectedText,

        [Parameter(Mandatory = $true)]
        [string] $Message
    )

    if ($DryRun -or $Force) {
        return
    }

    $answer = Read-Host "$Message Type $ExpectedText to continue"
    if ($answer -ne $ExpectedText) {
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
    "Pause" {
        Confirm-FaultAction -ExpectedText "PAUSE" -Message "Redis will stop responding while the container is paused."
        Invoke-DockerAction -Arguments @("pause", $RedisContainer) -ActionName "Pausing Redis"
        if ($DryRun) {
            Write-Host "DRY RUN: Redis state was not changed."
            return
        }
        Write-Host "Redis paused. Recover with: .\tests\fault-injection\scripts\Set-RedisAvailability.ps1 -Action Unpause"
    }
    "Unpause" {
        Invoke-DockerAction -Arguments @("unpause", $RedisContainer) -ActionName "Unpausing Redis"
        if ($DryRun) {
            Write-Host "DRY RUN: Redis state was not changed."
            return
        }
        Write-Host "Redis unpaused. Wait for backend Redis commands to recover, then warm flash-sale keys if needed."
        Write-Host "Health check: docker exec $RedisContainer redis-cli PING"
    }
    "Stop" {
        Confirm-FaultAction -ExpectedText "STOP" -Message "Redis container will be stopped. Data volume is not removed."
        Invoke-DockerAction -Arguments @("stop", $RedisContainer) -ActionName "Stopping Redis"
        if ($DryRun) {
            Write-Host "DRY RUN: Redis state was not changed."
            return
        }
        Write-Host "Redis stopped. Recover with: .\tests\fault-injection\scripts\Set-RedisAvailability.ps1 -Action Start"
    }
    "Start" {
        Invoke-DockerAction -Arguments @("start", $RedisContainer) -ActionName "Starting Redis"
        if ($DryRun) {
            Write-Host "DRY RUN: Redis state was not changed."
            return
        }
        Write-Host "Redis start requested. Wait until this returns PONG:"
        Write-Host "docker exec $RedisContainer redis-cli PING"
        Write-Host "Then re-run voucher warmup before success-path load tests."
    }
    "Status" {
        Invoke-DockerAction -Arguments @(
            "ps",
            "-a",
            "--filter",
            "name=$RedisContainer",
            "--format",
            "table {{.Names}}\t{{.Status}}"
        ) -ActionName "Reading Redis container status"
    }
}
