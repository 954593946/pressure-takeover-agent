$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$serviceRoot = Join-Path $repoRoot 'services\agent-api'
$python = Join-Path $serviceRoot '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    throw 'Agent virtual environment is missing. Run scripts\setup-agent.ps1 first.'
}

Push-Location $serviceRoot
try {
    & $python -m pytest
    if ($LASTEXITCODE -ne 0) { throw "tests failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}
