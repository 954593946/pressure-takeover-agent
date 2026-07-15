$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$serviceRoot = Join-Path $repoRoot 'services\agent-api'
$python = Join-Path $serviceRoot '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    py -3.11 -m venv (Join-Path $serviceRoot '.venv')
    if ($LASTEXITCODE -ne 0) { throw "virtual environment creation failed with exit code $LASTEXITCODE" }
}

Push-Location $serviceRoot
try {
    & $python -m pip install --upgrade pip
    if ($LASTEXITCODE -ne 0) { throw "pip upgrade failed with exit code $LASTEXITCODE" }
    & $python -m pip install -e '.[dev]'
    if ($LASTEXITCODE -ne 0) { throw "dependency installation failed with exit code $LASTEXITCODE" }
    & $python -m pytest
    if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
