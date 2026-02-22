#!/bin/bash
# install-and-verify.sh
# Runs inside Linux container, installs jDeploy app and verifies installation.

set -e

SHARED_DIR="${1:-/home/ubuntu/shared}"
RESULTS_DIR="$SHARED_DIR/results"
BUNDLE_DIR="$SHARED_DIR/jdeploy-bundle"
JDEPLOY_FILES_DIR="$SHARED_DIR/jdeploy-files"
SCRIPTS_DIR="$SHARED_DIR/scripts"

# Create results directory
mkdir -p "$RESULTS_DIR"

# Log function
log() {
    local timestamp=$(date '+%Y-%m-%d %H:%M:%S')
    echo "[$timestamp] $1" | tee -a "$RESULTS_DIR/install.log"
}

log "Starting jDeploy Linux installation..."

# Error handler
handle_error() {
    local error_msg="$1"
    log "ERROR: $error_msg"
    echo "$error_msg" > "$RESULTS_DIR/error.txt"
    echo "1" > "$RESULTS_DIR/exit-code.txt"

    # Create failed verification result
    cat > "$RESULTS_DIR/verification.json" << EOF
{
    "Timestamp": "$(date -Iseconds)",
    "AllPassed": false,
    "Checks": [
        {"Name": "Installation", "Status": "FAILED", "Details": "$error_msg"}
    ]
}
EOF
    exit 1
}

# Check for Java
log "Checking for Java..."
JAVA_PATH=""

if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | head -n 1)
    JAVA_PATH="java"
    log "Found Java in PATH: $JAVA_VERSION"
else
    log "Java not found in PATH, checking bundled JRE..."

    # Check for bundled JRE in jdeploy-bundle
    BUNDLED_JRE=$(find "$BUNDLE_DIR" -maxdepth 1 -type d -name "jre*" | head -n 1)
    if [ -n "$BUNDLED_JRE" ] && [ -x "$BUNDLED_JRE/bin/java" ]; then
        JAVA_PATH="$BUNDLED_JRE/bin/java"
        log "Found bundled JRE: $JAVA_PATH"
    fi
fi

if [ -z "$JAVA_PATH" ]; then
    # Try to install Java
    log "Attempting to install Java..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update -qq && sudo apt-get install -qq -y default-jre
        JAVA_PATH="java"
    else
        handle_error "Java not found and could not be installed"
    fi
fi

# Find the installer JAR
log "Looking for installer JAR in $BUNDLE_DIR..."
INSTALLER_JAR=$(find "$BUNDLE_DIR" -maxdepth 1 -name "*.jar" ! -name "*-sources*" ! -name "*-javadoc*" | head -n 1)

if [ -z "$INSTALLER_JAR" ]; then
    handle_error "Installer JAR not found in $BUNDLE_DIR"
fi

log "Found installer JAR: $INSTALLER_JAR"

# Check for app.xml
APP_XML="$JDEPLOY_FILES_DIR/app.xml"
if [ ! -f "$APP_XML" ]; then
    handle_error "app.xml not found at $APP_XML"
fi
log "Found app.xml: $APP_XML"

# Run the jDeploy installer in headless mode
log "Running jDeploy installer..."

export JDEPLOY_APP_XML="$APP_XML"
export DISPLAY=:1  # Use the VNC display if available

# Run installer
if $JAVA_PATH -jar "$INSTALLER_JAR" --headless 2>&1 | tee -a "$RESULTS_DIR/installer-output.log"; then
    log "Installation completed successfully."
else
    EXIT_CODE=$?
    log "Installer exited with code: $EXIT_CODE"
    # Don't fail here, let verification determine success
fi

# Run verification checks
log "Running verification checks..."
bash "$SCRIPTS_DIR/verification-checks.sh" "$SHARED_DIR" "$RESULTS_DIR"

# Signal completion
echo "$(date -Iseconds)" > "$SHARED_DIR/install-complete.marker"
log "Installation and verification complete."
