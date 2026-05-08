Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

Push-Location $repoRoot
try {
    $null = Get-Command git -ErrorAction Stop

    $gitRoot = git rev-parse --show-toplevel 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitRoot)) {
        throw "This script must be run inside a Git working tree."
    }

    git config core.hooksPath .githooks
    Write-Host "Configured core.hooksPath to .githooks"
    Write-Host "Future commits will sync versions from docs/CHANGELOG.md automatically."
}
finally {
    Pop-Location
}
