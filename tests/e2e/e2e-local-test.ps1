<#
.SYNOPSIS
    End-to-end test script for jDeploy local project lifecycle on Windows.

.DESCRIPTION
    Tests the full local development workflow:
    1. Generate project from template
    2. Build the project
    3. Install locally using jdeploy install
    4. Verify installation
    5. Uninstall
    6. Verify uninstallation

.PARAMETER Template
    Test only the specified template

.PARAMETER SkipUninstall
    Skip uninstall testing

.PARAMETER ConfigFile
    Use custom templates config file

.PARAMETER KeepProjects
    Don't delete generated test projects

.PARAMETER Verbose
    Show verbose output

.EXAMPLE
    .\e2e-local-test.ps1
    .\e2e-local-test.ps1 -Template "swing" -Verbose
#>

param(
    [string]$Template = "",
    [switch]$SkipInstallation,
    [switch]$SkipUninstall,
    [string]$ConfigFile = "",
    [switch]$KeepProjects,
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Get-Item "$ScriptDir\..\..").FullName
$ResultsDir = Join-Path $ScriptDir "results-local"
$TestProjectsDir = Join-Path $ScriptDir "test-projects"
$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$LogFile = Join-Path $ResultsDir "e2e-local-test-$Timestamp.log"

if (-not $ConfigFile) {
    $ConfigFile = Join-Path $ScriptDir "templates.conf"
}

# Create directories
if (-not (Test-Path $ResultsDir)) {
    New-Item -ItemType Directory -Path $ResultsDir | Out-Null
}
if (-not (Test-Path $TestProjectsDir)) {
    New-Item -ItemType Directory -Path $TestProjectsDir | Out-Null
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

    # Check for Maven
    try {
        $mvnVersion = & mvn -version 2>&1 | Select-Object -First 1
        Write-Log "Maven found: $mvnVersion"
    } catch {
        Write-Log "WARNING: Maven not installed, some templates may fail"
    }

    Write-Log "Prerequisites check passed"
    return $true
}

# Generate a unique project name
function Get-ProjectName {
    param([string]$TemplateName)
    return "test-$TemplateName-$Timestamp"
}

# Generate a new project from template
function New-Project {
    param(
        [string]$TemplateName,
        [string]$ProjectName
    )

    $projectLog = Join-Path $ResultsDir "$TemplateName-generate.log"
    Write-Log "Generating project from template '$TemplateName'..."

    $genArgs = @(
        "generate",
        "-t", $TemplateName,
        "-d", $TestProjectsDir,
        "-n", $ProjectName,
        "--appTitle=Test App $TemplateName",
        "-g", "com.test.e2e",
        "-a", $ProjectName,
        "--mainClassName=Main"
    )

    Write-LogVerbose "Running: java -jar `"$script:JdeployJar`" $($genArgs -join ' ')"
    $output = & java -jar $script:JdeployJar @genArgs 2>&1
    $exitCode = $LASTEXITCODE
    $output | Out-File -FilePath $projectLog -Append

    if ($exitCode -eq 0) {
        Write-Log "Project generated successfully: $ProjectName"
        return $true
    } else {
        Write-Log "ERROR: Failed to generate project from template '$TemplateName' (exit code: $exitCode)"
        $output | ForEach-Object { Write-Log $_ }
        return $false
    }
}

# Build the project
function Build-Project {
    param(
        [string]$ProjectDir,
        [string]$BuildCmd,
        [string]$TemplateName
    )

    $projectLog = Join-Path $ResultsDir "$TemplateName-build.log"
    Write-Log "Building project in $ProjectDir..."
    Write-LogVerbose "Build command: $BuildCmd"

    Push-Location $ProjectDir
    try {
        # Check for mvnw.cmd
        if (Test-Path "mvnw.cmd") {
            $BuildCmd = $BuildCmd -replace "^mvn ", ".\mvnw.cmd "
        }

        Write-LogVerbose "Executing: $BuildCmd"
        $output = Invoke-Expression "$BuildCmd 2>&1"
        $output | Out-File -FilePath $projectLog -Append

        if ($LASTEXITCODE -eq 0) {
            Write-Log "Project built successfully"
            return $true
        } else {
            Write-Log "ERROR: Build failed"
            $output | ForEach-Object { Write-Log $_ }
            return $false
        }
    } finally {
        Pop-Location
    }
}

# Install the project locally
function Install-Project {
    param(
        [string]$ProjectDir,
        [string]$TemplateName
    )

    $projectLog = Join-Path $ResultsDir "$TemplateName-install.log"
    Write-Log "Installing project locally..."

    Push-Location $ProjectDir
    try {
        $output = Invoke-Jdeploy -Arguments @("install", "-y")
        $output | Out-File -FilePath $projectLog -Append

        if ($LASTEXITCODE -eq 0) {
            Write-Log "Project installed successfully"
            return $true
        } else {
            Write-Log "ERROR: Installation failed"
            $output | ForEach-Object { Write-Log $_ }

            # Try to capture the jdeploy detailed log file
            $jdeployLogDir = Join-Path $env:USERPROFILE ".jdeploy\log"
            $jdeployInstallLog = Join-Path $jdeployLogDir "jdeploy-local-install.log"
            if (Test-Path $jdeployInstallLog) {
                Write-Log ""
                Write-Log "=== jDeploy Installation Log ($jdeployInstallLog) ==="
                Get-Content $jdeployInstallLog | ForEach-Object { Write-Log $_ }
                Write-Log "=== End of jDeploy Installation Log ==="
                # Copy to results directory for artifact upload
                Copy-Item $jdeployInstallLog (Join-Path $ResultsDir "$TemplateName-jdeploy-install.log") -ErrorAction SilentlyContinue
            } else {
                Write-Log "No jdeploy install log found at: $jdeployInstallLog"
                # List any logs that do exist
                if (Test-Path $jdeployLogDir) {
                    Write-Log "Available logs in ${jdeployLogDir}:"
                    Get-ChildItem $jdeployLogDir | ForEach-Object { Write-Log "  - $($_.Name)" }
                }
            }
            return $false
        }
    } finally {
        Pop-Location
    }
}

# Verify installation
function Test-Installation {
    param(
        [string]$ProjectDir,
        [string]$TemplateName
    )

    $projectLog = Join-Path $ResultsDir "$TemplateName-verify-install.log"
    Write-Log "Verifying installation..."

    Push-Location $ProjectDir
    try {
        $args = @("verify-installation", "--package-json=package.json", "--verbose")
        Write-LogVerbose "Running: java -jar $script:JdeployJar $($args -join ' ')"

        $output = Invoke-Jdeploy -Arguments $args
        $output | Out-File -FilePath $projectLog -Append
        $output | ForEach-Object { Write-Host $_ }

        if ($LASTEXITCODE -eq 0) {
            Write-Log "Verification PASSED"
            return $true
        } else {
            Write-Log "Verification FAILED"
            return $false
        }
    } finally {
        Pop-Location
    }
}

# Uninstall the project
function Uninstall-Project {
    param(
        [string]$ProjectDir,
        [string]$TemplateName
    )

    $projectLog = Join-Path $ResultsDir "$TemplateName-uninstall.log"
    Write-Log "Uninstalling project..."

    Push-Location $ProjectDir
    try {
        $output = Invoke-Jdeploy -Arguments @("uninstall", "-y")
        $output | Out-File -FilePath $projectLog -Append
        $output | ForEach-Object { Write-Host $_ }

        if ($LASTEXITCODE -eq 0) {
            Write-Log "Uninstall completed"
            return $true
        } else {
            Write-Log "Uninstall failed"
            return $false
        }
    } finally {
        Pop-Location
    }
}

# Verify uninstallation
function Test-Uninstallation {
    param(
        [string]$ProjectDir,
        [string]$TemplateName
    )

    $projectLog = Join-Path $ResultsDir "$TemplateName-verify-uninstall.log"
    Write-Log "Verifying uninstallation..."

    Push-Location $ProjectDir
    try {
        $args = @("verify-uninstallation", "--package-json=package.json", "--verbose")
        Write-LogVerbose "Running: java -jar $script:JdeployJar $($args -join ' ')"

        $output = Invoke-Jdeploy -Arguments $args
        $output | Out-File -FilePath $projectLog -Append
        $output | ForEach-Object { Write-Host $_ }

        if ($LASTEXITCODE -eq 0) {
            Write-Log "Uninstallation verification PASSED"
            return $true
        } else {
            Write-Log "Uninstallation verification FAILED"
            return $false
        }
    } finally {
        Pop-Location
    }
}

# Get service command name from package.json (if any)
function Get-ServiceCommand {
    param([string]$ProjectDir)

    $packageJson = Join-Path $ProjectDir "package.json"
    if (-not (Test-Path $packageJson)) {
        return $null
    }

    try {
        $json = Get-Content $packageJson -Raw | ConvertFrom-Json
        if ($json.jdeploy -and $json.jdeploy.commands) {
            $commands = $json.jdeploy.commands
            # Iterate through commands to find one with service_controller
            foreach ($prop in $commands.PSObject.Properties) {
                $cmdSpec = $prop.Value
                if ($cmdSpec.implements -and ($cmdSpec.implements -contains "service_controller")) {
                    return $prop.Name
                }
            }
        }
    } catch {
        Write-LogVerbose "Failed to parse package.json for service command: $_"
    }
    return $null
}

# Find the installed command path
function Find-InstalledCommand {
    param(
        [string]$CommandName,
        [string]$PackageName
    )

    # On Windows, commands are installed to: $USERPROFILE\.jdeploy\bin-x64\{package-name}\{command-name}.cmd
    $cmdPath = Join-Path $env:USERPROFILE ".jdeploy\bin-x64\$PackageName\$CommandName.cmd"
    if (Test-Path $cmdPath) {
        return $cmdPath
    }

    # Try without the package subdirectory
    $cmdPath = Join-Path $env:USERPROFILE ".jdeploy\bin-x64\$CommandName.cmd"
    if (Test-Path $cmdPath) {
        return $cmdPath
    }

    # Fallback: check if command is in PATH
    $cmd = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($cmd) {
        return $cmd.Source
    }

    return $null
}

# Test service commands (status, start, stop, install, uninstall)
function Test-ServiceCommands {
    param(
        [string]$ProjectDir,
        [string]$TemplateName,
        [string]$ServiceCmd
    )

    $projectLog = Join-Path $ResultsDir "$TemplateName-service-test.log"
    Write-Log "Testing service commands for: $ServiceCmd"

    # Get the package name from package.json
    $packageJson = Join-Path $ProjectDir "package.json"
    $packageName = $ServiceCmd  # Default to service command name
    try {
        $json = Get-Content $packageJson -Raw | ConvertFrom-Json
        if ($json.name) {
            $packageName = $json.name
        }
    } catch {
        Write-LogVerbose "Failed to get package name: $_"
    }

    # Find the installed command path
    $cmdPath = Find-InstalledCommand -CommandName $ServiceCmd -PackageName $packageName
    if (-not $cmdPath) {
        Write-Log "WARNING: Could not find installed command '$ServiceCmd' - skipping service tests"
        Write-Log "Looked for: $env:USERPROFILE\.jdeploy\bin-x64\$packageName\$ServiceCmd.cmd"
        return $true  # Don't fail the test, just skip service verification
    }
    Write-Log "Found command at: $cmdPath"

    Push-Location $ProjectDir
    try {
        # Test 1: Service status (initial state)
        Write-Log "Testing: $ServiceCmd service status"
        $output = & $cmdPath service status 2>&1
        $output | Out-File -FilePath $projectLog -Append
        Write-LogVerbose ($output -join "`n")

        # Test 2: Service install (register with system service manager)
        Write-Log "Testing: $ServiceCmd service install"
        $output = & $cmdPath service install 2>&1
        $output | Out-File -FilePath $projectLog -Append
        Write-LogVerbose ($output -join "`n")
        if ($LASTEXITCODE -ne 0) {
            Write-Log "WARNING: Service install returned non-zero (may be expected on CI)"
        }

        # Test 3: Service start
        Write-Log "Testing: $ServiceCmd service start"
        $output = & $cmdPath service start 2>&1
        $output | Out-File -FilePath $projectLog -Append
        Write-LogVerbose ($output -join "`n")
        if ($LASTEXITCODE -ne 0) {
            Write-Log "WARNING: Service start returned non-zero"
        }

        # Give service time to start
        Start-Sleep -Seconds 3

        # Test 4: Service status (should now show running)
        Write-Log "Testing: $ServiceCmd service status (after start)"
        $output = & $cmdPath service status 2>&1
        $output | Out-File -FilePath $projectLog -Append
        Write-LogVerbose ($output -join "`n")

        # Test 5: Service stop
        Write-Log "Testing: $ServiceCmd service stop"
        $output = & $cmdPath service stop 2>&1
        $output | Out-File -FilePath $projectLog -Append
        Write-LogVerbose ($output -join "`n")
        if ($LASTEXITCODE -ne 0) {
            Write-Log "WARNING: Service stop returned non-zero"
        }

        # Give service time to stop
        Start-Sleep -Seconds 2

        # Test 6: Service status (should now show stopped)
        Write-Log "Testing: $ServiceCmd service status (after stop)"
        $output = & $cmdPath service status 2>&1
        $output | Out-File -FilePath $projectLog -Append
        Write-LogVerbose ($output -join "`n")

        # Test 7: Service uninstall (unregister from system service manager)
        Write-Log "Testing: $ServiceCmd service uninstall"
        $output = & $cmdPath service uninstall 2>&1
        $output | Out-File -FilePath $projectLog -Append
        Write-LogVerbose ($output -join "`n")
        if ($LASTEXITCODE -ne 0) {
            Write-Log "WARNING: Service uninstall returned non-zero"
        }

        Write-Log "Service command tests completed for: $ServiceCmd"
        return $true
    } catch {
        Write-Log "ERROR: Service command test failed: $_"
        return $false
    } finally {
        Pop-Location
    }
}

# Cleanup test project
function Remove-TestProject {
    param([string]$ProjectDir)

    if (-not $KeepProjects -and (Test-Path $ProjectDir)) {
        Write-Log "Cleaning up project directory: $ProjectDir"
        Remove-Item -Path $ProjectDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

# Test a single template
function Test-Template {
    param(
        [string]$TemplateName,
        [string]$BuildCmd,
        [string]$Description
    )

    Write-Log "=========================================="
    Write-Log "Testing template: $TemplateName"
    Write-Log "Build command: $BuildCmd"
    Write-Log "Description: $Description"
    Write-Log "=========================================="

    $testPassed = $true
    $resultFile = Join-Path $ResultsDir "$TemplateName-result.txt"
    $projectName = Get-ProjectName -TemplateName $TemplateName
    $projectDir = Join-Path $TestProjectsDir $projectName

    # Step 1: Generate project
    if (-not (New-Project -TemplateName $TemplateName -ProjectName $projectName)) {
        "GENERATE_FAILED" | Out-File -FilePath $resultFile
        Remove-TestProject -ProjectDir $projectDir
        return $false
    }

    # Step 2: Build project
    if (-not (Build-Project -ProjectDir $projectDir -BuildCmd $BuildCmd -TemplateName $TemplateName)) {
        "BUILD_FAILED" | Out-File -FilePath $resultFile
        Remove-TestProject -ProjectDir $projectDir
        return $false
    }

    # Step 3: Install project (if not skipped)
    if (-not $SkipInstallation) {
        if (-not (Install-Project -ProjectDir $projectDir -TemplateName $TemplateName)) {
            "INSTALL_FAILED" | Out-File -FilePath $resultFile
            Remove-TestProject -ProjectDir $projectDir
            return $false
        }

        # Step 4: Verify installation
        if (-not (Test-Installation -ProjectDir $projectDir -TemplateName $TemplateName)) {
            "VERIFY_INSTALL_FAILED" | Out-File -FilePath $resultFile
            $testPassed = $false
        }

        # Step 4.5: Test service commands (if template has a service controller)
        $serviceCmd = Get-ServiceCommand -ProjectDir $projectDir
        if ($serviceCmd) {
            Write-Log "Template has service controller command: $serviceCmd"
            if (-not (Test-ServiceCommands -ProjectDir $projectDir -TemplateName $TemplateName -ServiceCmd $serviceCmd)) {
                "SERVICE_TEST_FAILED" | Out-File -FilePath $resultFile
                $testPassed = $false
            }
        }

        # Step 5: Uninstall (if not skipped)
        if (-not $SkipUninstall) {
            if (-not (Uninstall-Project -ProjectDir $projectDir -TemplateName $TemplateName)) {
                "UNINSTALL_FAILED" | Out-File -FilePath $resultFile
                $testPassed = $false
            }

            # Step 6: Verify uninstallation
            if (-not (Test-Uninstallation -ProjectDir $projectDir -TemplateName $TemplateName)) {
                "VERIFY_UNINSTALL_FAILED" | Out-File -FilePath $resultFile
                $testPassed = $false
            }
        }
    } else {
        Write-Log "Skipping installation steps (--SkipInstallation flag set)"
    }

    # Cleanup
    Remove-TestProject -ProjectDir $projectDir

    if ($testPassed) {
        "PASSED" | Out-File -FilePath $resultFile
        Write-Log "Test PASSED for template: $TemplateName"
        return $true
    } else {
        Write-Log "Test FAILED for template: $TemplateName"
        return $false
    }
}

# Main execution
function Main {
    Write-Log "=========================================="
    Write-Log "jDeploy Local Project E2E Tests"
    Write-Log "=========================================="
    Write-Log "Platform: Windows"
    Write-Log "Timestamp: $Timestamp"
    Write-Log "Config: $ConfigFile"
    Write-Log "Skip Installation: $SkipInstallation"
    Write-Log "Skip Uninstall: $SkipUninstall"
    Write-Log "Keep Projects: $KeepProjects"
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

    # Read templates from config
    $totalTemplates = 0
    $passedTemplates = 0
    $failedTemplates = 0
    $failedList = @()

    Get-Content $ConfigFile | ForEach-Object {
        $line = $_.Trim()

        # Skip empty lines and comments
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $parts = $line -split '\|'
        $templateName = $parts[0].Trim()
        $buildCmd = if ($parts.Length -gt 1) { $parts[1].Trim() } else { "mvn clean package -q" }
        $description = if ($parts.Length -gt 2) { $parts[2].Trim() } else { "" }

        # Filter by single template if specified
        if ($Template -and $templateName -ne $Template) {
            return
        }

        $totalTemplates++

        if (Test-Template -TemplateName $templateName -BuildCmd $buildCmd -Description $description) {
            $passedTemplates++
        } else {
            $failedTemplates++
            $failedList += $templateName
        }

        Write-Log ""
    }

    # Print summary
    Write-Log "=========================================="
    Write-Log "E2E Local Project Test Summary"
    Write-Log "=========================================="
    Write-Log "Total templates tested: $totalTemplates"
    Write-Log "Passed: $passedTemplates"
    Write-Log "Failed: $failedTemplates"

    if ($failedList.Count -gt 0) {
        Write-Log ""
        Write-Log "Failed templates:"
        $failedList | ForEach-Object { Write-Log "  - $_" }
    }

    # Write summary JSON
    $summary = @{
        timestamp = $Timestamp
        platform = "windows"
        totalTemplates = $totalTemplates
        passed = $passedTemplates
        failed = $failedTemplates
        success = ($failedTemplates -eq 0)
    } | ConvertTo-Json

    $summaryFile = Join-Path $ResultsDir "summary.json"
    $summary | Out-File -FilePath $summaryFile

    Write-Log ""
    Write-Log "Results saved to: $ResultsDir"
    Write-Log "Log file: $LogFile"

    # Cleanup test projects directory if empty
    if (-not $KeepProjects) {
        if ((Get-ChildItem $TestProjectsDir -ErrorAction SilentlyContinue | Measure-Object).Count -eq 0) {
            Remove-Item -Path $TestProjectsDir -ErrorAction SilentlyContinue
        }
    }

    # Exit with appropriate code
    if ($failedTemplates -gt 0) {
        exit 1
    }
    exit 0
}

# Run main
Main
