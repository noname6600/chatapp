param(
    [string]$GatewayBaseUrl = "http://localhost:8080",
    [string]$Email = "smoke_$(Get-Random)@example.com",
    [string]$Password = "Password1!"
)

$ErrorActionPreference = "Stop"

function Invoke-JsonPost {
    param(
        [string]$Url,
        [hashtable]$Body,
        [hashtable]$Headers = @{}
    )

    $json = $Body | ConvertTo-Json
    return Invoke-RestMethod -Method Post -Uri $Url -Body $json -ContentType "application/json" -Headers $Headers
}

Write-Host "[1/4] Registering user against gateway..."
$register = Invoke-JsonPost -Url "$GatewayBaseUrl/api/v1/auth/register" -Body @{
    username = $Email
    password = $Password
}

if (-not $register.data.accessToken -or -not $register.data.refreshToken) {
    throw "Contract violation: register response missing accessToken or refreshToken"
}

Write-Host "[2/4] Logging in user against gateway..."
$login = Invoke-JsonPost -Url "$GatewayBaseUrl/api/v1/auth/login" -Body @{
    username = $Email
    password = $Password
}

if (-not $login.data.accessToken -or -not $login.data.refreshToken) {
    throw "Contract violation: login response missing accessToken or refreshToken"
}

$accessToken = $login.data.accessToken

Write-Host "[3/4] Calling protected endpoint immediately with issued access token..."
$headers = @{ Authorization = "Bearer $accessToken" }
$protectedStatus = $null

try {
    $null = Invoke-RestMethod -Method Get -Uri "$GatewayBaseUrl/api/v1/users/me" -Headers $headers
    $protectedStatus = 200
} catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
        $protectedStatus = [int]$_.Exception.Response.StatusCode
    } else {
        throw
    }
}

if ($protectedStatus -eq 401) {
    throw "Immediate protected call returned 401 after login"
}

Write-Host "[4/4] Refreshing token..."
$refresh = Invoke-JsonPost -Url "$GatewayBaseUrl/api/v1/auth/refresh" -Body @{
    refreshToken = $login.data.refreshToken
}

if (-not $refresh.data.accessToken -or -not $refresh.data.refreshToken) {
    throw "Contract violation: refresh response missing accessToken or refreshToken"
}

Write-Host "Smoke check passed."
