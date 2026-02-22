#!/bin/bash
# verification-checks.sh
# Verifies Linux installation of jDeploy application.

SHARED_DIR="${1:-/home/ubuntu/shared}"
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
# jDeploy installs to ~/.local/share/jdeploy-apps/{packageName} or similar
APP_DIR=""
POSSIBLE_DIRS=(
    "$HOME/.local/share/jdeploy-apps/$PACKAGE_NAME"
    "$HOME/.jdeploy-apps/$PACKAGE_NAME"
    "$HOME/jdeploy-apps/$PACKAGE_NAME"
)

for dir in "${POSSIBLE_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        APP_DIR="$dir"
        break
    fi
done

# Also check for any jdeploy-apps subdirectories
if [ -z "$APP_DIR" ]; then
    JDEPLOY_APPS_DIR="$HOME/.local/share/jdeploy-apps"
    if [ -d "$JDEPLOY_APPS_DIR" ]; then
        FIRST_APP=$(find "$JDEPLOY_APPS_DIR" -mindepth 1 -maxdepth 1 -type d | head -n 1)
        if [ -n "$FIRST_APP" ]; then
            APP_DIR="$FIRST_APP"
        fi
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
BIN_DIR="$HOME/.local/share/jdeploy-packages"
CLI_FOUND=false

if [ -d "$BIN_DIR" ]; then
    for pkg_dir in "$BIN_DIR"/*/; do
        if [ -d "${pkg_dir}bin" ]; then
            CMD_COUNT=$(find "${pkg_dir}bin" -type f | wc -l)
            if [ "$CMD_COUNT" -gt 0 ]; then
                add_check "CLI commands installed" "PASSED" "$CMD_COUNT command(s) found"
                CLI_FOUND=true
                break
            fi
        fi
    done
fi

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
