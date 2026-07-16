param(
    [string]$BindAddress = '127.0.0.1',
    [int]$Port = 8000,
    [switch]$NoAccessLog
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$serviceRoot = Join-Path $repoRoot 'services\agent-api'
$python = Join-Path $serviceRoot '.venv\Scripts\python.exe'

if (-not (Test-Path -LiteralPath $python)) {
    throw 'Agent virtual environment is missing. Run scripts\setup-agent.ps1 first.'
}

Push-Location $serviceRoot
try {
    $arguments = @('-m', 'uvicorn', 'auri_agent.app:app', '--host', $BindAddress, '--port', $Port)
    if ($NoAccessLog) { $arguments += '--no-access-log' }
    & $python @arguments
}
finally {
    Pop-Location
}
