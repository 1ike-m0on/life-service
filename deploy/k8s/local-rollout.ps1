param(
    [ValidateSet("all", "backend", "frontend")]
    [string]$Target = "all",

    [string]$ImageTag = "",

    [switch]$SkipBuild,

    [switch]$ApplyBase,

    [switch]$ForceApplyBase,

    [switch]$SkipImageLoad
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Resolve-Path (Join-Path $scriptDir "..\..")
$overlayPath = Join-Path $scriptDir "overlays\local"

function Invoke-Step {
    param(
        [string]$Title,
        [scriptblock]$Command
    )

    Write-Host ""
    Write-Host "==> $Title" -ForegroundColor Cyan
    & $Command
}

function Invoke-Native {
    param(
        [string]$File,
        [string[]]$Arguments
    )

    & $File @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$File failed with exit code $LASTEXITCODE"
    }
}

function Invoke-DockerBuild {
    param(
        [string]$Name,
        [string]$Dockerfile,
        [string]$ContextPath,
        [string]$Tag
    )

    Invoke-Native "docker" @("build", "-t", "${Name}:local", "-t", "${Name}:${Tag}", "-f", $Dockerfile, $ContextPath)
}

function Load-ImageIfNeeded {
    param(
        [string]$Name,
        [string]$Tag,
        [string]$Context
    )

    if ($SkipImageLoad) {
        return
    }

    if ($Context -like "kind-*") {
        Invoke-Native "kind" @("load", "docker-image", "${Name}:${Tag}")
    }
}

if ([string]::IsNullOrWhiteSpace($ImageTag)) {
    $shortSha = (& git -C $repoRoot rev-parse --short HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "git rev-parse failed with exit code $LASTEXITCODE"
    }
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $ImageTag = "local-$shortSha-$timestamp"
}

$kubectlContext = (& kubectl config current-context).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "kubectl config current-context failed with exit code $LASTEXITCODE"
}
$namespaceExists = $false
& kubectl get namespace life-service | Out-Null
$namespaceExists = $LASTEXITCODE -eq 0

Write-Host "Life Service local Kubernetes rollout" -ForegroundColor Green
Write-Host "Repository : $repoRoot"
Write-Host "Context    : $kubectlContext"
Write-Host "Target     : $Target"
Write-Host "Image tag  : $ImageTag"
Write-Host "Apply base : $ApplyBase"

if ($ApplyBase -and $namespaceExists -and -not $ForceApplyBase) {
    $legacyInfra = (& kubectl -n life-service get sts mysql redis rocketmq-broker -o name --ignore-not-found) -join ", "
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl get statefulsets failed with exit code $LASTEXITCODE"
    }
    if (-not [string]::IsNullOrWhiteSpace($legacyInfra)) {
        throw "Existing StatefulSet infra found: $legacyInfra. Delete namespace life-service first, or rerun with -ForceApplyBase if you intentionally want to apply base manifests over the current namespace."
    }
}

if (-not $SkipBuild) {
    if ($Target -eq "all" -or $Target -eq "backend") {
        Invoke-Step "Build backend image" {
            Invoke-DockerBuild `
                -Name "life-service-backend" `
                -Dockerfile (Join-Path $repoRoot "Dockerfile") `
                -ContextPath $repoRoot `
                -Tag $ImageTag
        }
        Invoke-Step "Load backend image when using kind" {
            Load-ImageIfNeeded -Name "life-service-backend" -Tag $ImageTag -Context $kubectlContext
        }
    }

    if ($Target -eq "all" -or $Target -eq "frontend") {
        Invoke-Step "Build frontend image" {
            Invoke-DockerBuild `
                -Name "life-service-frontend" `
                -Dockerfile (Join-Path $repoRoot "frontend\Dockerfile") `
                -ContextPath (Join-Path $repoRoot "frontend") `
                -Tag $ImageTag
        }
        Invoke-Step "Load frontend image when using kind" {
            Load-ImageIfNeeded -Name "life-service-frontend" -Tag $ImageTag -Context $kubectlContext
        }
    }
}

if ($ApplyBase -or -not $namespaceExists) {
    Invoke-Step "Apply base/local manifests" {
        Invoke-Native "kubectl" @("apply", "-k", $overlayPath)
    }
} else {
    Write-Host ""
    Write-Host "==> Skip base/local manifests; pass -ApplyBase for first deploy or intentional infra updates." -ForegroundColor DarkYellow
}

if ($Target -eq "all" -or $Target -eq "backend") {
    Invoke-Step "Roll backend image" {
        Invoke-Native "kubectl" @("-n", "life-service", "set", "image", "deployment/backend", "backend=life-service-backend:${ImageTag}")
        Invoke-Native "kubectl" @("-n", "life-service", "rollout", "status", "deployment/backend", "--timeout=300s")
    }
}

if ($Target -eq "all" -or $Target -eq "frontend") {
    Invoke-Step "Roll frontend image" {
        Invoke-Native "kubectl" @("-n", "life-service", "set", "image", "deployment/frontend", "frontend=life-service-frontend:${ImageTag}")
        Invoke-Native "kubectl" @("-n", "life-service", "rollout", "status", "deployment/frontend", "--timeout=180s")
    }
}

Invoke-Step "Current pods" {
    Invoke-Native "kubectl" @("-n", "life-service", "get", "pods", "-o", "wide")
}

Write-Host ""
Write-Host "Done. Useful checks:" -ForegroundColor Green
Write-Host "  kubectl -n life-service port-forward svc/frontend 8080:80"
Write-Host "  kubectl -n life-service port-forward svc/backend 8081:8081"
