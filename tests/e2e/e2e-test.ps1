<#
.SYNOPSIS
    End-to-end test script for jDeploy installations on Windows.

.DESCRIPTION
    Designed to run natively (no Docker required) in CI environments.
    Tests headless install, verification, uninstall, and verification.

.PARAMETER App
    Test only the specified app (by package name)

.PARAMETER SkipUninstall
    Skip uninstall testing

.PARAMETER JdeployUrl
    Use custom jdeploy.com URL (default: www.jdeploy.com)

.PARAMETER ConfigFile
    Use custom apps config file

.PARAMETER Verbose
    Show verbose output

.EXAMPLE
    .\e2e-test.ps1
    .\e2e-test.ps1 -App "jdeploy-demo-swingset3" -Verbose
#>

param(
    [string]$App = "",
    [switch]$SkipUninstall,
    [string]$JdeployUrl = "www.jdeploy.com",
    [string]$ConfigFile = "",
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Get-Item "$ScriptDir\..\..").FullName
$ResultsDir = Join-Path $ScriptDir "results"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = Join-Path $ResultsDir "e2e-test-$Timestamp.log"

if (-not $ConfigFile) {
    $ConfigFile = Join-Path $ScriptDir "apps.conf"
}

# Create results directory
if (-not (Test-Path $ResultsDir)) {
    New-Item -ItemType Directory -Path $ResultsDir | Out-Null
}

# Logging function
function Write-Log {
    param([string]$Message)
    $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    $logMessage = "[$timestamp] $Message"
    Write-Host $logMessage
    Add-Content -Path $LogFile -Value $logMessage
}

function Write-LogVerbose {
    param([string]$Message)
    if ($Verbose) {
        Write-Log $Message
    }
}

# Setup jdeploy from source
function Initialize-Jdeploy {
    Write-Log "Setting up jdeploy from source..."

    $script:JdeployJar = Join-Path $ProjectRoot "cli\target\jdeploy-cli-1.0-SNAPSHOT.jar"

    if (-not (Test-Path $script:JdeployJar)) {
        Write-Log "ERROR: jdeploy CLI JAR not found at $script:JdeployJar"
        Write-Log "Please run 'mvn clean install' from the project root first."
        exit 2
    }

    Write-Log "jdeploy CLI ready: $script:JdeployJar"
}

# Run jdeploy command
function Invoke-Jdeploy {
    param([string[]]$Arguments)
    $result = & java -jar $script:JdeployJar @Arguments 2>&1
    return $result
}

# Check prerequisites
function Test-Prerequisites {
    Write-Log "Checking prerequisites..."

    # Check for Java
    try {
        $javaVersion = & java -version 2>&1 | Select-Object -First 1
        Write-Log "Java found: $javaVersion"
    } catch {
        Write-Log "ERROR: Java is not installed"
        return $false
    }

    Write-Log "Prerequisites check passed"
    return $true
}

# Install an app
function Install-App {
    param(
        [string]$PackageName,
        [string]$SourceUrl
    )

    $appLog = Join-Path $ResultsDir "$PackageName-install.log"
    Write-Log "Installing $PackageName..."

    # Create temp directory
    $tempDir = Join-Path $env:TEMP "jdeploy-e2e-$([guid]::NewGuid().ToString('N'))"
    New-Item -ItemType Directory -Path $tempDir | Out-Null

    try {
        # Construct install script URL
        if ($SourceUrl -match "^https://github.com/") {
            $ghPath = $SourceUrl -replace "^https://github.com/", ""
            $installUrl = "https://$JdeployUrl/gh/$ghPath/install.ps1?headless=true"
        } else {
            $installUrl = "https://$JdeployUrl/~$PackageName/install.ps1?headless=true"
        }

        Write-LogVerbose "Install URL: $installUrl"

        # Download install script
        $installScript = Join-Path $tempDir "install.ps1"
        try {
            Invoke-WebRequest -Uri $installUrl -OutFile $installScript -UseBasicParsing
        } catch {
            Write-Log "ERROR: Failed to download install script for $PackageName"
            Add-Content -Path $appLog -Value "Download failed: $_"
            return $false
        }

        # Run installer
        Write-Log "Running headless installer for $PackageName..."
        $output = & powershell -ExecutionPolicy Bypass -File $installScript 2>&1
        $output | Out-File -FilePath $appLog -Append

        if ($LASTEXITCODE -ne 0) {
            Write-Log "ERROR: Installation failed for $PackageName"
            return $false
        }

        Write-Log "Installation completed for $PackageName"
        return $true
    } finally {
        # Cleanup
        if (Test-Path $tempDir) {
            Remove-Item -Path $tempDir -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

# Verify installation
function Test-Installation {
    param(
        [string]$PackageName,
        [string]$SourceUrl
    )

    $appLog = Join-Path $ResultsDir "$PackageName-verify-install.log"
    Write-Log "Verifying installation for $PackageName..."

    $args = @("verify-installation", "--package=$PackageName", "--verbose")
    if ($SourceUrl) {
        $args += "--source=$SourceUrl"
    }

    Write-LogVerbose "Running: java -jar $script:JdeployJar $($args -join ' ')"
    $output = Invoke-Jdeploy -Arguments $args
    $output | Out-File -FilePath $appLog

    # Display output
    $output | ForEach-Object { Write-Host $_ }

    if ($LASTEXITCODE -eq 0) {
        Write-Log "Verification PASSED for $PackageName"
        return $true
    } else {
        Write-Log "Verification FAILED for $PackageName (exit code: $LASTEXITCODE)"
        return $false
    }
}

# Uninstall an app
function Uninstall-App {
    param(
        [string]$PackageName,
        [string]$SourceUrl
    )

    $appLog = Join-Path $ResultsDir "$PackageName-uninstall.log"
    Write-Log "Uninstalling $PackageName..."

    $args = @("uninstall", "--package=$PackageName")
    if ($SourceUrl) {
        $args += "--source=$SourceUrl"
    }

    Write-LogVerbose "Running: java -jar $script:JdeployJar $($args -join ' ')"
    $output = Invoke-Jdeploy -Arguments $args
    $output | Out-File -FilePath $appLog

    # Display output
    $output | ForEach-Object { Write-Host $_ }

    if ($LASTEXITCODE -eq 0) {
        Write-Log "Uninstall completed for $PackageName"
        return $true
    } else {
        Write-Log "Uninstall failed for $PackageName (exit code: $LASTEXITCODE)"
        return $false
    }
}

# Verify uninstallation
function Test-Uninstallation {
    param(
        [string]$PackageName,
        [string]$SourceUrl
    )

    $appLog = Join-Path $ResultsDir "$PackageName-verify-uninstall.log"
    Write-Log "Verifying uninstallation for $PackageName..."

    $args = @("verify-uninstallation", "--package=$PackageName", "--verbose")
    if ($SourceUrl) {
        $args += "--source=$SourceUrl"
    }

    Write-LogVerbose "Running: java -jar $script:JdeployJar $($args -join ' ')"
    $output = Invoke-Jdeploy -Arguments $args
    $output | Out-File -FilePath $appLog

    # Display output
    $output | ForEach-Object { Write-Host $_ }

    if ($LASTEXITCODE -eq 0) {
        Write-Log "Uninstallation verification PASSED for $PackageName"
        return $true
    } else {
        Write-Log "Uninstallation verification FAILED for $PackageName (exit code: $LASTEXITCODE)"
        return $false
    }
}

# Test a single application
function Test-App {
    param(
        [string]$PackageName,
        [string]$SourceUrl,
        [string]$Description
    )

    Write-Log "=========================================="
    Write-Log "Testing: $PackageName"
    Write-Log "Source: $(if ($SourceUrl) { $SourceUrl } else { 'npm' })"
    Write-Log "Description: $Description"
    Write-Log "=========================================="

    $testPassed = $true
    $resultFile = Join-Path $ResultsDir "$PackageName-result.txt"

    # Step 1: Install
    if (-not (Install-App -PackageName $PackageName -SourceUrl $SourceUrl)) {
        "INSTALL_FAILED" | Out-File -FilePath $resultFile
        return $false
    }

    # Step 2: Verify installation
    if (-not (Test-Installation -PackageName $PackageName -SourceUrl $SourceUrl)) {
        "VERIFY_INSTALL_FAILED" | Out-File -FilePath $resultFile
        $testPassed = $false
    }

    # Step 3: Uninstall (if not skipped)
    if (-not $SkipUninstall) {
        if (-not (Uninstall-App -PackageName $PackageName -SourceUrl $SourceUrl)) {
            "UNINSTALL_FAILED" | Out-File -FilePath $resultFile
            $testPassed = $false
        }

        # Step 4: Verify uninstallation
        if (-not (Test-Uninstallation -PackageName $PackageName -SourceUrl $SourceUrl)) {
            "VERIFY_UNINSTALL_FAILED" | Out-File -FilePath $resultFile
            $testPassed = $false
        }
    }

    if ($testPassed) {
        "PASSED" | Out-File -FilePath $resultFile
        Write-Log "Test PASSED for $PackageName"
        return $true
    } else {
        Write-Log "Test FAILED for $PackageName"
        return $false
    }
}

# Main execution
function Main {
    Write-Log "=========================================="
    Write-Log "jDeploy E2E Installation Tests"
    Write-Log "=========================================="
    Write-Log "Platform: Windows"
    Write-Log "Timestamp: $Timestamp"
    Write-Log "Config: $ConfigFile"
    Write-Log "jDeploy URL: $JdeployUrl"
    Write-Log "Skip Uninstall: $SkipUninstall"
    Write-Log ""

    # Check prerequisites
    if (-not (Test-Prerequisites)) {
        Write-Log "ERROR: Prerequisites check failed"
        exit 2
    }

    # Setup jdeploy from source
    Initialize-Jdeploy

    # Check config file
    if (-not (Test-Path $ConfigFile)) {
        Write-Log "ERROR: Config file not found: $ConfigFile"
        exit 2
    }

    # Read applications from config
    $totalApps = 0
    $passedApps = 0
    $failedApps = 0
    $failedList = @()

    Get-Content $ConfigFile | ForEach-Object {
        $line = $_.Trim()

        # Skip empty lines and comments
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line -split '\|'
        $packageName = $parts[0].Trim()
        $sourceUrl = if ($parts.Length -gt 1) { $parts[1].Trim() } else { "" }
        $description = if ($parts.Length -gt 2) { $parts[2].Trim() } else { "" }

        # Filter by single app if specified
        if ($App -and $packageName -ne $App) {
            return
        }

        $totalApps++

        if (Test-App -PackageName $packageName -SourceUrl $sourceUrl -Description $description) {
            $passedApps++
        } else {
            $failedApps++
            $failedList += $packageName
        }

        Write-Log ""
    }

    # Print summary
    Write-Log "=========================================="
    Write-Log "E2E Test Summary"
    Write-Log "=========================================="
    Write-Log "Total apps tested: $totalApps"
    Write-Log "Passed: $passedApps"
    Write-Log "Failed: $failedApps"

    if ($failedList.Count -gt 0) {
        Write-Log ""
        Write-Log "Failed apps:"
        $failedList | ForEach-Object { Write-Log "  - $_" }
    }

    # Write summary JSON
    $summary = @{
        timestamp = $Timestamp
        platform = "windows"
        totalApps = $totalApps
        passed = $passedApps
        failed = $failedApps
        success = ($failedApps -eq 0)
    } | ConvertTo-Json

    $summaryFile = Join-Path $ResultsDir "summary.json"
    $summary | Out-File -FilePath $summaryFile

    Write-Log ""
    Write-Log "Results saved to: $ResultsDir"
    Write-Log "Log file: $LogFile"

    # Exit with appropriate code
    if ($failedApps -gt 0) {
        exit 1
    }
    exit 0
}

# Run main
Main
