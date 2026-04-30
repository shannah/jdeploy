#!/bin/bash
# install-and-launch.sh
# Installs jdeploy from npm and uses it to install/launch the app.
# Used when running from a release version of jdeploy (not dev mode).

set -e

SHARED_DIR="${1:-/config/shared}"
APP_DIR="/app"
LOG_FILE="$SHARED_DIR/results/autostart.log"

mkdir -p "$SHARED_DIR/results"

log() {
    echo "[$(date '+%H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log "Starting automatic installation and launch..."

# Wait a moment for desktop to fully initialize
sleep 3

# Check for Node.js/npm
if ! command -v npm &> /dev/null; then
    log "Installing Node.js and npm..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update -qq
        sudo apt-get install -qq -y nodejs npm 2>&1 | tee -a "$LOG_FILE"
    else
        log "ERROR: apt-get not available, cannot install Node.js"
        exit 1
    fi
fi

log "Node.js version: $(node --version)"
log "npm version: $(npm --version)"

# Install jdeploy globally
log "Installing jdeploy from npm..."
sudo npm install -g jdeploy 2>&1 | tee -a "$LOG_FILE"

# Verify jdeploy is installed
if ! command -v jdeploy &> /dev/null; then
    log "ERROR: jdeploy installation failed"
    exit 1
fi

log "jdeploy installed: $(jdeploy --version 2>/dev/null || echo 'version unknown')"

# Read the run arguments (passed via environment or file)
RUN_ARGS=""
if [ -f "$SHARED_DIR/run-args.txt" ]; then
    RUN_ARGS=$(cat "$SHARED_DIR/run-args.txt")
fi

# Change to app directory
log "Changing to app directory: $APP_DIR"
cd "$APP_DIR"

# Ensure DISPLAY is set for GUI apps
export DISPLAY=:1

# Run jdeploy install first.
# --native is required so that the headless installer produces the
# native launcher layout that 'jdeploy run' below expects.
log "Running: jdeploy install --native"
jdeploy install --native 2>&1 | tee -a "$LOG_FILE"

log "Installation complete. Now launching the app..."

# Run jdeploy run to launch the app (in background so terminal stays responsive)
log "Running: jdeploy run $RUN_ARGS"
jdeploy run $RUN_ARGS 2>&1 | tee -a "$LOG_FILE" &

# Give the app a moment to start
sleep 2

log "Autostart script completed"
log "Check $LOG_FILE for details"

# Keep terminal open for debugging
log "Press Enter to close this terminal..."
read
