param(
    [int] $Count = 12000,
    [int] $StartIndex = 1,
    [string] $EmailPrefix = "load-user",
    [string] $Domain = "life.local",
    [string] $OutputPath = "$PSScriptRoot\..\data\users-12000.csv"
)

$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
$outputDir = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

Write-Host "Generating $Count users..."
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("email")

for ($i = 0; $i -lt $Count; $i++) {
    $seq = $StartIndex + $i
    $lines.Add("$EmailPrefix$seq@$Domain")
}

Set-Content -Encoding UTF8 -Path $resolvedOutput -Value $lines

Write-Host "Generated $Count users at $resolvedOutput"
