<#
.SYNOPSIS
    Measures HTTP endpoint parity between Android (AvagoServiceClient.kt) and
    iOS (AvagoServiceClient*.swift). Outputs three sections: iOS-only gaps,
    Android-only extras, and matched endpoints, plus an overall parity score.

.PARAMETER AndroidClient
    Path to AvagoServiceClient.kt. Defaults to the standard module path relative
    to this script's location inside the avagoAndroid repo.

.PARAMETER IosDataDir
    Root directory containing iOS AvagoServiceClient*.swift files (searched
    recursively). Defaults to the standard iOS repo Data folder.

.PARAMETER Json
    Emit machine-readable JSON to stdout instead of coloured console output.
    Useful for CI pipelines or programmatic diffing over time.

.EXAMPLE
    # Run from repo root:
    .\scripts\check-api-parity.ps1

.EXAMPLE
    # Run from CI, fail if score drops below 80%:
    $result = .\scripts\check-api-parity.ps1 -Json | ConvertFrom-Json
    if ($result.parity_score_pct -lt 80) { exit 1 }
#>
param(
    [string]$AndroidClient = (Join-Path $PSScriptRoot '..\core\network\src\main\kotlin\com\avago\core\network\AvagoServiceClient.kt'),
    [string]$IosDataDir    = 'C:\Users\marlong\OneDrive\global\Code\avago\avago\Data',
    [switch]$Json
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ---------------------------------------------------------------------------
# Normalise a URL path so Kotlin and Swift representations are comparable.
#   - Kotlin:  $variable  →  :id
#   - Swift:   \(variable) →  :id
#   - OpenAPI: {variable}  →  :id   (for future use)
# Query strings and trailing slashes are stripped.
# ---------------------------------------------------------------------------
function Normalize-Path([string]$raw) {
    $p = $raw -replace '\?.*$',         ''     # strip query string
    $p = $p   -replace '\$[a-zA-Z_]\w*', ':id'  # Kotlin $variable
    $p = $p   -replace '\\\([^)]+\)',    ':id'  # Swift  \(variable)
    $p = $p   -replace '\{[^}]+\}',     ':id'  # OpenAPI {variable}
    return $p.TrimEnd('/').ToLower()
}

function Make-Key([string]$method, [string]$path) {
    "$($method.ToUpper().PadRight(7))$(Normalize-Path $path)"
}

# ---------------------------------------------------------------------------
# ANDROID — extract from Ktor client calls in AvagoServiceClient.kt
#   Patterns:  client.get("$baseUrl/path")
#              client.post("$baseUrl/path") { ... }
# ---------------------------------------------------------------------------
if (-not (Test-Path $AndroidClient)) {
    Write-Error "Android client not found: $AndroidClient"; exit 1
}

$ktContent = Get-Content $AndroidClient -Raw
$android   = @{}   # key → source comment (empty for now)

$ktRx = [regex]'(?m)client\.(get|post|put|delete|patch)\s*\(\s*"\$baseUrl(/[^"]+)"'
foreach ($m in $ktRx.Matches($ktContent)) {
    $key = Make-Key $m.Groups[1].Value $m.Groups[2].Value
    $android[$key] = $true
}

# ---------------------------------------------------------------------------
# iOS — extract from three call styles across all AvagoServiceClient*.swift
#
#   Style 1 — chatRequest("/path")                  → GET
#   Style 1 — chatRequest("/path", method: "POST")  → POST
#
#   Style 2 — makeURL("/path")                      → GET (default)
#             <next ≤8 lines>  httpMethod = "PUT"   → PUT
#
#   Style 3 — URL(string: "...\(baseURL)/path")     → GET (default)
#             <next ≤8 lines>  httpMethod = "POST"  → POST
# ---------------------------------------------------------------------------
$swiftFiles = Get-ChildItem $IosDataDir -Recurse -Filter 'AvagoServiceClient*.swift' |
              Where-Object FullName -notmatch 'worktrees|\.build'

if (-not $swiftFiles) {
    Write-Error "No iOS Swift files found under: $IosDataDir"; exit 1
}

$ios = @{}

foreach ($f in $swiftFiles) {
    $raw   = Get-Content $f.FullName -Raw
    $lines = $raw -split "`n"

    # Style 1 — chatRequest(...)
    $chatRx = [regex]'chatRequest\(\s*"(/[^"]+)"(?:[^)]*?method:\s*"([A-Z]+)")?'
    foreach ($m in $chatRx.Matches($raw)) {
        $method = if ($m.Groups[2].Success) { $m.Groups[2].Value } else { 'GET' }
        $ios[(Make-Key $method $m.Groups[1].Value)] = $true
    }

    # Style 2 — makeURL("/path") with optional forward-scan for httpMethod
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -notmatch 'makeURL\("(/[^"]+)"') { continue }
        $path   = $Matches[1]
        $method = 'GET'
        $limit  = [Math]::Min($i + 8, $lines.Count - 1)
        for ($j = $i + 1; $j -le $limit; $j++) {
            if ($lines[$j] -match '\.httpMethod\s*=\s*"([A-Z]+)"') {
                $method = $Matches[1]; break
            }
            if ($j -gt $i + 2 -and $lines[$j] -match '\bmakeURL\(') { break }
        }
        $ios[(Make-Key $method $path)] = $true
    }

    # Style 3 — URL(string: "...(AvagoServiceConfig.baseURL)/path")
    for ($i = 0; $i -lt $lines.Count; $i++) {
        # The Swift source contains literal \(AvagoServiceConfig.baseURL); in the
        # file that is: backslash open-paren AvagoServiceConfig.baseURL close-paren
        if ($lines[$i] -notmatch 'URL\(string:.*?AvagoServiceConfig\.baseURL\)(/[^"]+)"') { continue }
        $path   = $Matches[1]
        $method = 'GET'
        $limit  = [Math]::Min($i + 8, $lines.Count - 1)
        for ($j = $i + 1; $j -le $limit; $j++) {
            if ($lines[$j] -match '\.httpMethod\s*=\s*"([A-Z]+)"') {
                $method = $Matches[1]; break
            }
            if ($j -gt $i + 2 -and $lines[$j] -match '\b(URL|makeURL)\(') { break }
        }
        $ios[(Make-Key $method $path)] = $true
    }
}

# ---------------------------------------------------------------------------
# Compare
# ---------------------------------------------------------------------------
$iosOnly     = $ios.Keys     | Where-Object { -not $android.ContainsKey($_) } | Sort-Object
$androidOnly = $android.Keys | Where-Object { -not $ios.ContainsKey($_)     } | Sort-Object
$matched     = $android.Keys | Where-Object {       $ios.ContainsKey($_)     } | Sort-Object

$aCount = $android.Count
$iCount = $ios.Count
$mCount = @($matched).Count
$score  = if (($aCount + $iCount) -gt 0) {
    [int][Math]::Round($mCount * 2.0 / ($aCount + $iCount) * 100)
} else { 0 }

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------
if ($Json) {
    [pscustomobject]@{
        parity_score_pct = $score
        android_total    = $aCount
        ios_total        = $iCount
        matched_count    = $mCount
        ios_only         = @($iosOnly)
        android_only     = @($androidOnly)
        matched          = @($matched)
    } | ConvertTo-Json -Depth 3
    exit 0
}

$w   = try { $Host.UI.RawUI.WindowSize.Width } catch { 100 }
if ($w -lt 60) { $w = 100 }
$bar = [string]::new([char]0x2500, $w - 4)  # ────

Write-Host ""
Write-Host "  ╔$bar╗" -ForegroundColor Cyan
Write-Host ("  ║  API PARITY REPORT".PadRight($w - 3) + "║") -ForegroundColor Cyan
Write-Host ("  ║  Android $aCount  ·  iOS $iCount  ·  Matched $mCount  ·  Score $score%".PadRight($w - 3) + "║") -ForegroundColor Cyan
Write-Host "  ╚$bar╝" -ForegroundColor Cyan

# ── iOS-only: these are gaps Android should fill ─────────────────────────
$iosOnlyArr = @($iosOnly)
if ($iosOnlyArr.Count -gt 0) {
    Write-Host ""
    Write-Host "  iOS ONLY ($($iosOnlyArr.Count)) — Android may be missing these calls:" -ForegroundColor Red
    $iosOnlyArr | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
}

# ── Android-only: likely intentional (inventory/procurement not on iOS yet) ─
$androidOnlyArr = @($androidOnly)
if ($androidOnlyArr.Count -gt 0) {
    Write-Host ""
    Write-Host "  ANDROID ONLY ($($androidOnlyArr.Count)) — not called by iOS (may be intentional):" -ForegroundColor Yellow
    $androidOnlyArr | ForEach-Object { Write-Host "    $_" -ForegroundColor Yellow }
}

# ── Matched ───────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  MATCHED ($mCount):" -ForegroundColor Green
@($matched) | ForEach-Object { Write-Host "    $_" -ForegroundColor Green }
Write-Host ""
