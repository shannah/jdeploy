# verification-checks.ps1
# Verifies Windows installation of jDeploy application.

param(
    [string]$SharedDir = "C:\shared",
    [string]$ResultsDir = "$SharedDir\results"
)

$ErrorActionPreference = "Continue"

# Read app info from app.xml to get expected values
$AppXml = "$SharedDir\jdeploy-files\app.xml"
$AppTitle = "Unknown App"
$PackageName = "unknown"

if (Test-Path $AppXml) {
    try {
        [xml]$xmlContent = Get-Content $AppXml
        $AppTitle = $xmlContent.app.title
        $PackageName = $xmlContent.app.'npm-package'
        if (-not $PackageName) {
            $PackageName = $xmlContent.app.name
        }
    } catch {
        Write-Host "Warning: Could not parse app.xml"
    }
}

Write-Host "Verifying installation of: $AppTitle ($PackageName)"

$Checks = @()
$AllPassed = $true

# Helper function to add a check result
function Add-Check {
    param(
        [string]$Name,
        [string]$Status,
        [string]$Details = $null
    )

    $check = @{
        Name = $Name
        Status = $Status
    }
    if ($Details) {
        $check.Details = $Details
    }

    $script:Checks += $check

    if ($Status -eq "FAILED") {
        $script:AllPassed = $false
    }

    $symbol = if ($Status -eq "PASSED") { "[PASS]" } elseif ($Status -eq "FAILED") { "[FAIL]" } else { "[SKIP]" }
    Write-Host "$symbol $Name"
    if ($Details) {
        Write-Host "       $Details"
    }
}

# --- Check 1: App directory exists ---
# jDeploy installs to %LOCALAPPDATA%\jdeploy-apps\{packageName} or custom winAppDir
$possibleAppDirs = @(
    "$env:LOCALAPPDATA\jdeploy-apps\$PackageName",
    "$env:LOCALAPPDATA\Programs\$AppTitle",
    "$env:APPDATA\jdeploy-apps\$PackageName"
)

$appDir = $null
foreach ($dir in $possibleAppDirs) {
    if (Test-Path $dir) {
        $appDir = $dir
        break
    }
}

# Also check for any jdeploy-apps subdirectories
if (-not $appDir) {
    $jdeployAppsDir = "$env:LOCALAPPDATA\jdeploy-apps"
    if (Test-Path $jdeployAppsDir) {
        $subDirs = Get-ChildItem -Path $jdeployAppsDir -Directory -ErrorAction SilentlyContinue
        if ($subDirs.Count -gt 0) {
            $appDir = $subDirs[0].FullName
        }
    }
}

if ($appDir) {
    Add-Check -Name "App directory exists" -Status "PASSED" -Details $appDir
} else {
    Add-Check -Name "App directory exists" -Status "FAILED" -Details "Not found in any expected location"
}

# --- Check 2: Main executable exists ---
$exePath = $null
if ($appDir) {
    # Look for .exe files
    $exeFiles = Get-ChildItem -Path $appDir -Filter "*.exe" -Recurse -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike "*-cli.exe" -and $_.Name -notlike "*uninstall*" }

    if ($exeFiles.Count -gt 0) {
        $exePath = $exeFiles[0].FullName
        Add-Check -Name "Executable exists" -Status "PASSED" -Details $exePath
    } else {
        Add-Check -Name "Executable exists" -Status "FAILED" -Details "No .exe found in $appDir"
    }
} else {
    Add-Check -Name "Executable exists" -Status "SKIPPED" -Details "App directory not found"
}

# --- Check 3: Desktop shortcut exists ---
$desktopPath = [Environment]::GetFolderPath("Desktop")
$shortcutPattern = "$desktopPath\*.lnk"
$shortcuts = Get-ChildItem -Path $shortcutPattern -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "*$AppTitle*" -or $_.Name -like "*$PackageName*" }

# Also check without filtering by name (in case app title differs)
if ($shortcuts.Count -eq 0) {
    $allShortcuts = Get-ChildItem -Path $shortcutPattern -ErrorAction SilentlyContinue
    if ($allShortcuts.Count -gt 0) {
        # Just check if any .lnk exists
        Add-Check -Name "Desktop shortcut exists" -Status "PASSED" -Details "$($allShortcuts.Count) shortcut(s) found on desktop"
    } else {
        Add-Check -Name "Desktop shortcut exists" -Status "FAILED" -Details "No shortcuts found on desktop"
    }
} else {
    Add-Check -Name "Desktop shortcut exists" -Status "PASSED" -Details $shortcuts[0].FullName
}

# --- Check 4: Start Menu shortcut exists ---
$startMenuPath = "$env:APPDATA\Microsoft\Windows\Start Menu\Programs"
$startMenuShortcuts = Get-ChildItem -Path "$startMenuPath\*.lnk" -ErrorAction SilentlyContinue

if ($startMenuShortcuts.Count -gt 0) {
    Add-Check -Name "Start Menu shortcut exists" -Status "PASSED" -Details "$($startMenuShortcuts.Count) shortcut(s) in Start Menu"
} else {
    # Check subdirectories
    $subDirShortcuts = Get-ChildItem -Path $startMenuPath -Filter "*.lnk" -Recurse -ErrorAction SilentlyContinue
    if ($subDirShortcuts.Count -gt 0) {
        Add-Check -Name "Start Menu shortcut exists" -Status "PASSED" -Details "Found in Start Menu subdirectory"
    } else {
        Add-Check -Name "Start Menu shortcut exists" -Status "FAILED" -Details "No shortcuts found in Start Menu"
    }
}

# --- Check 5: Registry uninstall entry ---
$uninstallPath = "HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall"
$uninstallEntries = Get-ChildItem -Path $uninstallPath -ErrorAction SilentlyContinue |
    ForEach-Object {
        $displayName = $_.GetValue("DisplayName")
        $publisher = $_.GetValue("Publisher")
        if ($displayName -and ($displayName -like "*$AppTitle*" -or $displayName -like "*$PackageName*" -or $publisher -like "*jdeploy*")) {
            $_
        }
    } |
    Where-Object { $_ -ne $null }

if ($uninstallEntries.Count -gt 0) {
    $displayName = $uninstallEntries[0].GetValue("DisplayName")
    Add-Check -Name "Registry uninstall entry" -Status "PASSED" -Details "DisplayName: $displayName"
} else {
    # Check for any jdeploy-related entries
    $allEntries = Get-ChildItem -Path $uninstallPath -ErrorAction SilentlyContinue
    $jdeployEntries = $allEntries | Where-Object {
        $_.GetValue("Publisher") -like "*jdeploy*" -or
        $_.GetValue("UninstallString") -like "*jdeploy*"
    }
    if ($jdeployEntries.Count -gt 0) {
        Add-Check -Name "Registry uninstall entry" -Status "PASSED" -Details "Found jDeploy-related uninstall entry"
    } else {
        Add-Check -Name "Registry uninstall entry" -Status "FAILED" -Details "No uninstall entry found for $AppTitle"
    }
}

# --- Check 6: CLI commands (if any) ---
$binDirPattern = "$env:LOCALAPPDATA\jdeploy-packages\*\bin"
$binDirs = Get-ChildItem -Path "$env:LOCALAPPDATA\jdeploy-packages" -Directory -ErrorAction SilentlyContinue |
    ForEach-Object { Get-ChildItem -Path $_.FullName -Filter "bin" -Directory -ErrorAction SilentlyContinue }

if ($binDirs.Count -gt 0) {
    $cmdFiles = Get-ChildItem -Path $binDirs[0].FullName -Filter "*.cmd" -ErrorAction SilentlyContinue
    if ($cmdFiles.Count -gt 0) {
        Add-Check -Name "CLI commands installed" -Status "PASSED" -Details "$($cmdFiles.Count) command(s) found"
    } else {
        Add-Check -Name "CLI commands installed" -Status "SKIPPED" -Details "No CLI commands defined or bin dir empty"
    }
} else {
    Add-Check -Name "CLI commands installed" -Status "SKIPPED" -Details "No CLI bin directory found (may not be configured)"
}

# --- Write results ---
$results = @{
    Timestamp = (Get-Date -Format "o")
    AppTitle = $AppTitle
    PackageName = $PackageName
    AppDirectory = $appDir
    Checks = $Checks
    AllPassed = $AllPassed
}

$results | ConvertTo-Json -Depth 3 | Set-Content "$ResultsDir\verification.json"

# Write exit code
if ($AllPassed) {
    Set-Content -Path "$ResultsDir\exit-code.txt" -Value "0"
    Write-Host ""
    Write-Host "All verification checks PASSED"
} else {
    Set-Content -Path "$ResultsDir\exit-code.txt" -Value "1"
    Write-Host ""
    Write-Host "Some verification checks FAILED"
}
