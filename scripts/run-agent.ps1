$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$serviceRoot = Join-Path $repoRoot 'services\agent-api'
$python = Join-Path $serviceRoot '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    throw 'Agent virtual environment is missing. Run scripts\setup-agent.ps1 first.'
}

Push-Location $serviceRoot
try {
    & $python -m uvicorn auri_agent.app:app --host 127.0.0.1 --port 8000
}
finally {
    Pop-Location
}
