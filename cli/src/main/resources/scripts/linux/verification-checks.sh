#!/bin/bash
# verification-checks.sh
# Verifies Linux installation of jDeploy application.

SHARED_DIR="${1:-/config/shared}"
RESULTS_DIR="${2:-$SHARED_DIR/results}"

# Read app info from app.xml to get expected values
APP_XML="$SHARED_DIR/jdeploy-files/app.xml"
APP_TITLE="Unknown App"
PACKAGE_NAME="unknown"

if [ -f "$APP_XML" ]; then
    # Extract title and package name using grep/sed (portable)
    APP_TITLE=$(grep -oP '(?<=<title>)[^<]+' "$APP_XML" 2>/dev/null || echo "Unknown App")
    PACKAGE_NAME=$(grep -oP '(?<=<npm-package>)[^<]+' "$APP_XML" 2>/dev/null || \
                   grep -oP '(?<=<name>)[^<]+' "$APP_XML" 2>/dev/null || echo "unknown")
fi

echo "Verifying installation of: $APP_TITLE ($PACKAGE_NAME)"

# Initialize results
CHECKS="[]"
ALL_PASSED=true

# Helper function to add a check result
add_check() {
    local name="$1"
    local status="$2"
    local details="$3"

    local symbol
    case "$status" in
        PASSED) symbol="[PASS]" ;;
        FAILED) symbol="[FAIL]"; ALL_PASSED=false ;;
        *) symbol="[SKIP]" ;;
    esac

    echo "$symbol $name"
    if [ -n "$details" ]; then
        echo "       $details"
    fi

    # Add to JSON array
    if [ -n "$details" ]; then
        CHECKS=$(echo "$CHECKS" | jq --arg n "$name" --arg s "$status" --arg d "$details" \
            '. + [{"Name": $n, "Status": $s, "Details": $d}]')
    else
        CHECKS=$(echo "$CHECKS" | jq --arg n "$name" --arg s "$status" \
            '. + [{"Name": $n, "Status": $s}]')
    fi
}

# --- Check 1: App directory exists ---
# jDeploy installs to ~/.jdeploy/apps/{packageName}
APP_DIR=""
JDEPLOY_APPS_DIR="$HOME/.jdeploy/apps"

# Check for exact package name match first
if [ -d "$JDEPLOY_APPS_DIR/$PACKAGE_NAME" ]; then
    APP_DIR="$JDEPLOY_APPS_DIR/$PACKAGE_NAME"
fi

# If not found, check for any app directory (package name may differ from directory name)
if [ -z "$APP_DIR" ] && [ -d "$JDEPLOY_APPS_DIR" ]; then
    # Try partial match on package name
    APP_DIR=$(find "$JDEPLOY_APPS_DIR" -maxdepth 1 -type d -name "*$PACKAGE_NAME*" 2>/dev/null | head -n 1)

    # If still not found, just find any app directory
    if [ -z "$APP_DIR" ]; then
        APP_DIR=$(find "$JDEPLOY_APPS_DIR" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -n 1)
    fi
fi

if [ -n "$APP_DIR" ] && [ -d "$APP_DIR" ]; then
    add_check "App directory exists" "PASSED" "$APP_DIR"
else
    add_check "App directory exists" "FAILED" "Not found in any expected location"
fi

# --- Check 2: Main executable exists ---
if [ -n "$APP_DIR" ]; then
    # Look for executable files (not .jar)
    EXE_FILE=$(find "$APP_DIR" -maxdepth 2 -type f -executable ! -name "*.jar" ! -name "*uninstall*" | head -n 1)

    if [ -n "$EXE_FILE" ]; then
        add_check "Executable exists" "PASSED" "$EXE_FILE"
    else
        # Check for shell scripts or AppImage
        SCRIPT_FILE=$(find "$APP_DIR" -maxdepth 2 -type f \( -name "*.sh" -o -name "*.AppImage" \) | head -n 1)
        if [ -n "$SCRIPT_FILE" ]; then
            add_check "Executable exists" "PASSED" "$SCRIPT_FILE"
        else
            add_check "Executable exists" "FAILED" "No executable found in $APP_DIR"
        fi
    fi
else
    add_check "Executable exists" "SKIPPED" "App directory not found"
fi

# --- Check 3: Desktop shortcut exists ---
DESKTOP_DIR="$HOME/Desktop"
DESKTOP_SHORTCUT=""

if [ -d "$DESKTOP_DIR" ]; then
    DESKTOP_SHORTCUT=$(find "$DESKTOP_DIR" -maxdepth 1 -name "*.desktop" | head -n 1)
fi

if [ -n "$DESKTOP_SHORTCUT" ]; then
    add_check "Desktop shortcut exists" "PASSED" "$DESKTOP_SHORTCUT"
else
    add_check "Desktop shortcut exists" "FAILED" "No .desktop file found on desktop"
fi

# --- Check 4: Applications menu entry exists ---
APPS_DIR="$HOME/.local/share/applications"
MENU_ENTRY=""

if [ -d "$APPS_DIR" ]; then
    MENU_ENTRY=$(find "$APPS_DIR" -maxdepth 1 -name "*.desktop" | head -n 1)
fi

if [ -n "$MENU_ENTRY" ]; then
    add_check "Applications menu entry" "PASSED" "$MENU_ENTRY"
else
    add_check "Applications menu entry" "SKIPPED" "No .desktop file in applications folder"
fi

# --- Check 5: CLI commands (if any) ---
# CLI commands are installed in ~/.jdeploy/packages-{arch}/{package}/bin/
# and symlinked from ~/.local/bin/
CLI_FOUND=false

# Check for packages directories
for BIN_DIR in "$HOME/.jdeploy/packages"*; do
    if [ -d "$BIN_DIR" ]; then
        for pkg_dir in "$BIN_DIR"/*/; do
            if [ -d "${pkg_dir}bin" ]; then
                CMD_COUNT=$(find "${pkg_dir}bin" -type f 2>/dev/null | wc -l)
                if [ "$CMD_COUNT" -gt 0 ]; then
                    add_check "CLI commands installed" "PASSED" "$CMD_COUNT command(s) found in ${pkg_dir}bin"
                    CLI_FOUND=true
                    break 2
                fi
            fi
        done
    fi
done

if [ "$CLI_FOUND" = false ]; then
    add_check "CLI commands installed" "SKIPPED" "No CLI bin directory found (may not be configured)"
fi

# --- Write results ---
# Check if jq is available
if command -v jq &> /dev/null; then
    cat > "$RESULTS_DIR/verification.json" << EOF
{
    "Timestamp": "$(date -Iseconds)",
    "AppTitle": "$APP_TITLE",
    "PackageName": "$PACKAGE_NAME",
    "AppDirectory": "$APP_DIR",
    "Checks": $CHECKS,
    "AllPassed": $ALL_PASSED
}
EOF
else
    # Fallback without jq - simple JSON
    cat > "$RESULTS_DIR/verification.json" << EOF
{
    "Timestamp": "$(date -Iseconds)",
    "AppTitle": "$APP_TITLE",
    "PackageName": "$PACKAGE_NAME",
    "AppDirectory": "$APP_DIR",
    "AllPassed": $ALL_PASSED,
    "Checks": []
}
EOF
fi

# Write exit code
if [ "$ALL_PASSED" = true ]; then
    echo "0" > "$RESULTS_DIR/exit-code.txt"
    echo ""
    echo "All verification checks PASSED"
else
    echo "1" > "$RESULTS_DIR/exit-code.txt"
    echo ""
    echo "Some verification checks FAILED"
fi
