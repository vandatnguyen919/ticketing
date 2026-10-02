[CmdletBinding()]
param(
    [ValidateSet('all', 'frontend', 'backend')]
    [string]$Check = 'all'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if ($Check -eq 'all' -or $Check -eq 'frontend') {
    Write-Host '==> Frontend tests'
    Push-Location (Join-Path $root 'frontend')
    & npm.cmd test
    $frontendTestExitCode = $LASTEXITCODE
    if ($frontendTestExitCode -ne 0) {
        Pop-Location
        throw "Frontend tests failed with exit code $frontendTestExitCode."
    }

    Write-Host '==> Frontend build'
    & npm.cmd run build
    $frontendExitCode = $LASTEXITCODE
    Pop-Location
    if ($frontendExitCode -ne 0) {
        throw "Frontend build failed with exit code $frontendExitCode."
    }
}

if ($Check -eq 'all' -or $Check -eq 'backend') {
    Write-Host '==> Backend tests'
    Push-Location (Join-Path $root 'backend')
    & .\gradlew.bat test
    $backendExitCode = $LASTEXITCODE
    Pop-Location
    if ($backendExitCode -ne 0) {
        throw "Backend Gradle tests failed with exit code $backendExitCode."
    }
}

Write-Host "Verification passed: $Check"
