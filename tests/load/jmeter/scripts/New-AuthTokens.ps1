param(
    [string] $BaseUrl = "http://localhost:8081",
    [string] $UserCsv = "$PSScriptRoot\..\data\users-12000.csv",
    [string] $OutputPath = "$PSScriptRoot\..\data\tokens-12000.csv",
    [int] $ProgressEvery = 100
)

$resolvedUserCsv = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($UserCsv)
$resolvedOutput = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputPath)
$outputDir = Split-Path -Parent $resolvedOutput
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$users = Import-Csv -Path $resolvedUserCsv
if ($users.Count -eq 0) {
    throw "No users found in $resolvedUserCsv"
}

"email,token,clientIp" | Set-Content -Encoding UTF8 -Path $resolvedOutput

$index = 0
foreach ($user in $users) {
    $index++
    $body = @{ email = $user.email } | ConvertTo-Json -Compress
    $response = Invoke-RestMethod `
        -Method Post `
        -Uri "$BaseUrl/api/v1/auth/login" `
        -ContentType "application/json" `
        -Body $body

    if (-not $response.success -or -not $response.data.token) {
        throw "Login failed for $($user.email)"
    }

    $ipIndex = $index - 1
    $thirdOctet = [int][Math]::Floor($ipIndex / 254) + 1
    $fourthOctet = ($ipIndex % 254) + 1
    $clientIp = "10.20.$thirdOctet.$fourthOctet"

    "$($user.email),$($response.data.token),$clientIp" | Add-Content -Encoding UTF8 -Path $resolvedOutput

    if ($index % $ProgressEvery -eq 0) {
        Write-Host "Prepared $index / $($users.Count) tokens"
    }
}

Write-Host "Prepared $index tokens at $resolvedOutput"
