<#
.SYNOPSIS
Injects or recovers MySQL container availability faults.

.DESCRIPTION
Stops or starts only the MySQL container. This script does not remove the
container, compose project, or database volume.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("Stop", "Start", "Status")]
    [string] $Action,

    [ValidateNotNullOrEmpty()]
    [string] $MysqlContainer = "life-service-mysql",

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

    $answer = Read-Host "MySQL container will be stopped. Type STOP to continue"
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
        Invoke-DockerAction -Arguments @("stop", $MysqlContainer) -ActionName "Stopping MySQL"
        if ($DryRun) {
            Write-Host "DRY RUN: MySQL state was not changed."
            return
        }
        Write-Host "MySQL stopped. Recover with: .\tests\fault-injection\scripts\Set-MysqlAvailability.ps1 -Action Start"
    }
    "Start" {
        Invoke-DockerAction -Arguments @("start", $MysqlContainer) -ActionName "Starting MySQL"
        if ($DryRun) {
            Write-Host "DRY RUN: MySQL state was not changed."
            return
        }
        Write-Host "MySQL start requested. Wait until the container health is healthy before reading results."
        Write-Host "Suggested checks:"
        Write-Host "  docker inspect -f '{{.State.Health.Status}}' $MysqlContainer"
        Write-Host "  docker logs --tail 100 $BackendContainer"
    }
    "Status" {
        Invoke-DockerAction -Arguments @(
            "ps",
            "-a",
            "--filter",
            "name=$MysqlContainer",
            "--format",
            "table {{.Names}}\t{{.Status}}"
        ) -ActionName "Reading MySQL container status"
    }
}
