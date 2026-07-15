param(
    [int]$Port = 8000
)

$ErrorActionPreference = 'Stop'
$name = 'AURI Agent API - Local Subnet'
$existing = Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue

if ($existing) {
    Remove-NetFirewallRule -DisplayName $name
}

New-NetFirewallRule `
    -DisplayName $name `
    -Description 'Allow AURI shared demo backend only from directly connected local subnets.' `
    -Direction Inbound `
    -Action Allow `
    -Protocol TCP `
    -LocalPort $Port `
    -Profile Any `
    -RemoteAddress LocalSubnet | Out-Null

Write-Host "Configured '$name' for TCP $Port and LocalSubnet only."
