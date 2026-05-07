Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

if (-not (Test-Path .\.venv\Scripts\python.exe)) {
    throw 'Create the virtual environment first: .\scripts\bootstrap.ps1'
}

& .\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8787

