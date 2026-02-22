# startup-wrapper.ps1
# Runs at Windows startup inside the Docker container.
# Orchestrates the jDeploy installation and verification.

param(
    [string]$SharedDir = "C:\shared"
)

$ErrorActionPreference = "Stop"

$ResultsDir = "$SharedDir\results"
$ScriptsDir = "$SharedDir\scripts"

# Create results directory if it doesn't exist
if (-not (Test-Path $ResultsDir)) {
    New-Item -ItemType Directory -Path $ResultsDir -Force | Out-Null
}

# Wait for shared folder to be fully mounted (sometimes takes a moment)
$maxWait = 60
$waited = 0
while (-not (Test-Path "$SharedDir\jdeploy-files\app.xml") -and $waited -lt $maxWait) {
    Start-Sleep -Seconds 1
    $waited++
}

if (-not (Test-Path "$SharedDir\jdeploy-files\app.xml")) {
    Set-Content -Path "$ResultsDir\error.txt" -Value "app.xml not found in shared folder after waiting $maxWait seconds"
    Set-Content -Path "$ResultsDir\exit-code.txt" -Value "1"
    Set-Content -Path "$SharedDir\install-complete.marker" -Value (Get-Date)
    exit 1
}

# Signal that Windows is ready
Set-Content -Path "$SharedDir\windows-ready.marker" -Value (Get-Date)

try {
    # Run the installation and verification script
    & "$ScriptsDir\install-and-verify.ps1" -SharedDir $SharedDir
} catch {
    # Write error to results
    Set-Content -Path "$ResultsDir\error.txt" -Value $_.Exception.Message
    Set-Content -Path "$ResultsDir\exit-code.txt" -Value "1"
}

# Signal completion (if not already done by install-and-verify.ps1)
if (-not (Test-Path "$SharedDir\install-complete.marker")) {
    Set-Content -Path "$SharedDir\install-complete.marker" -Value (Get-Date)
}
