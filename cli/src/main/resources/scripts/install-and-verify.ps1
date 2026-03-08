# install-and-verify.ps1
# Runs inside Windows container, installs jDeploy app and verifies installation.

param(
    [string]$SharedDir = "C:\shared"
)

$ErrorActionPreference = "Stop"

$ResultsDir = "$SharedDir\results"
$BundleDir = "$SharedDir\jdeploy-bundle"
$JDeployFilesDir = "$SharedDir\jdeploy-files"
$ScriptsDir = "$SharedDir\scripts"

# Create results directory
if (-not (Test-Path $ResultsDir)) {
    New-Item -ItemType Directory -Path $ResultsDir -Force | Out-Null
}

# Log function
function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] $Message"
    Add-Content -Path "$ResultsDir\install.log" -Value $logMessage
    Write-Host $logMessage
}

Write-Log "Starting jDeploy Windows installation..."

try {
    # Check for Java
    Write-Log "Checking for Java..."
    $javaPath = $null

    # Check if Java is in PATH
    try {
        $javaVersion = & java -version 2>&1
        $javaPath = "java"
        Write-Log "Found Java in PATH: $javaVersion"
    } catch {
        Write-Log "Java not found in PATH, checking bundled JRE..."
    }

    # Check for bundled JRE in jdeploy-bundle
    if (-not $javaPath) {
        $bundledJre = Get-ChildItem -Path $BundleDir -Filter "jre*" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($bundledJre) {
            $javaPath = Join-Path $bundledJre.FullName "bin\java.exe"
            if (Test-Path $javaPath) {
                Write-Log "Found bundled JRE: $javaPath"
            } else {
                $javaPath = $null
            }
        }
    }

    # If still no Java, try to download portable JRE
    if (-not $javaPath) {
        Write-Log "No Java found. Please ensure Java is available in the container."
        throw "Java not found. Install Java or provide bundled JRE."
    }

    # Find the installer JAR
    Write-Log "Looking for installer JAR in $BundleDir..."
    $InstallerJar = Get-ChildItem "$BundleDir\*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-sources*" -and $_.Name -notlike "*-javadoc*" } |
        Select-Object -First 1

    if (-not $InstallerJar) {
        throw "Installer JAR not found in $BundleDir"
    }

    Write-Log "Found installer JAR: $($InstallerJar.FullName)"

    # Get the app.xml path
    $AppXml = "$JDeployFilesDir\app.xml"
    if (-not (Test-Path $AppXml)) {
        throw "app.xml not found at $AppXml"
    }
    Write-Log "Found app.xml: $AppXml"

    # Run the jDeploy installer in headless mode
    Write-Log "Running jDeploy installer..."

    $installerArgs = @(
        "-jar", $InstallerJar.FullName,
        "--headless"
    )

    # Set the app.xml path via system property
    $env:JDEPLOY_APP_XML = $AppXml

    $process = Start-Process -FilePath $javaPath -ArgumentList $installerArgs `
        -NoNewWindow -Wait -PassThru `
        -RedirectStandardOutput "$ResultsDir\installer-stdout.log" `
        -RedirectStandardError "$ResultsDir\installer-stderr.log"

    $exitCode = $process.ExitCode
    Write-Log "Installer exited with code: $exitCode"

    if ($exitCode -ne 0) {
        $stderr = Get-Content "$ResultsDir\installer-stderr.log" -ErrorAction SilentlyContinue
        throw "Installer failed with exit code $exitCode. Stderr: $stderr"
    }

    Write-Log "Installation completed successfully."

    # Run verification checks
    Write-Log "Running verification checks..."
    & "$ScriptsDir\verification-checks.ps1" -SharedDir $SharedDir -ResultsDir $ResultsDir

} catch {
    Write-Log "ERROR: $($_.Exception.Message)"

    # Write error to results
    Set-Content -Path "$ResultsDir\error.txt" -Value $_.Exception.Message
    Set-Content -Path "$ResultsDir\exit-code.txt" -Value "1"

    # Create a failed verification result
    $failedResult = @{
        Timestamp = (Get-Date -Format "o")
        AllPassed = $false
        Checks = @(
            @{ Name = "Installation"; Status = "FAILED"; Details = $_.Exception.Message }
        )
    }
    $failedResult | ConvertTo-Json -Depth 3 | Set-Content "$ResultsDir\verification.json"
}

# Signal completion
Set-Content -Path "$SharedDir\install-complete.marker" -Value (Get-Date)
Write-Log "Installation and verification complete."
