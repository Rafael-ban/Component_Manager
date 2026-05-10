Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$serverRoot = Split-Path -Parent $PSScriptRoot
$repoRoot = Split-Path -Parent $serverRoot
$uvCacheDir = Join-Path $repoRoot '.uv-cache'
$uvPythonInstallDir = Join-Path $repoRoot '.uv-python'
$venvPath = Join-Path $serverRoot '.venv'
$venvPython = Join-Path $venvPath 'Scripts\python.exe'

function Test-UsablePythonLauncher {
    if (-not (Get-Command py -ErrorAction SilentlyContinue)) {
        return $false
    }

    try {
        py -3.12 -c "import sys; print(sys.version)" | Out-Null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Test-UsableVenvPython {
    param(
        [string]$PythonPath
    )

    if (-not (Test-Path $PythonPath)) {
        return $false
    }

    try {
        & $PythonPath --version | Out-Null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

Push-Location $serverRoot
try {
    if (-not (Test-UsableVenvPython -PythonPath $venvPython)) {
        if (Test-Path $venvPath) {
            Remove-Item -LiteralPath $venvPath -Recurse -Force
        }

        if (Test-UsablePythonLauncher) {
            py -3.12 -m venv .venv
        } elseif (Get-Command uv -ErrorAction SilentlyContinue) {
            uv python install 3.12 --install-dir $uvPythonInstallDir --cache-dir $uvCacheDir --no-bin --no-registry
            $env:UV_PYTHON_INSTALL_DIR = $uvPythonInstallDir
            uv venv .venv --seed --python 3.12 --managed-python --cache-dir $uvCacheDir
        } else {
            throw 'Neither a usable Python 3.12 launcher nor uv was found. Install Python 3.12 or uv first.'
        }
    }

    & .\.venv\Scripts\python.exe -m pip install --upgrade pip
    & .\.venv\Scripts\python.exe -m pip install -r requirements-dev.txt

    Write-Host 'Server virtual environment is ready at server/.venv'
} finally {
    Pop-Location
}
