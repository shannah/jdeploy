#!/bin/bash
# dev-install-and-launch.sh
# Builds jdeploy from source and runs it to install/launch the app.
# Used when testing from the jdeploy development environment.

set -e

JDEPLOY_MOUNT="/jdeploy"
APP_MOUNT="/app"
LOG_FILE="/config/shared/results/dev-autostart.log"

# Use local directories for faster builds (Docker mounts are slow on macOS)
JDEPLOY_DIR="/tmp/jdeploy-build"
APP_DIR="/tmp/app-build"

mkdir -p /config/shared/results

log() {
    echo "[$(date '+%H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

log "Starting dev install and launch..."

# Wait for desktop to initialize
sleep 3

# Copy jdeploy project to local filesystem for faster builds
log "Copying jdeploy project to local filesystem (this speeds up the build)..."
rm -rf "$JDEPLOY_DIR"
cp -r "$JDEPLOY_MOUNT" "$JDEPLOY_DIR"
log "jdeploy project copied."

# Copy app project to local filesystem
log "Copying app project to local filesystem..."
rm -rf "$APP_DIR"
cp -r "$APP_MOUNT" "$APP_DIR"
log "App project copied."

# Check if Java is installed
if ! command -v java &> /dev/null; then
    log "Installing Java 21..."
    if command -v apt-get &> /dev/null; then
        sudo apt-get update -qq
        sudo apt-get install -qq -y openjdk-21-jdk maven 2>&1 | tee -a "$LOG_FILE"
    else
        log "ERROR: apt-get not available, cannot install Java"
        exit 1
    fi
fi

# Verify Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1)
log "Java version: $JAVA_VERSION"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    log "Installing Maven..."
    sudo apt-get install -qq -y maven 2>&1 | tee -a "$LOG_FILE"
fi

# Build jdeploy
log "Building jdeploy..."
cd "$JDEPLOY_DIR"

# Build shared module first if it exists
if [ -d "shared" ]; then
    log "Building shared module..."
    cd shared
    mvn package -DskipTests -q 2>&1 | tee -a "$LOG_FILE" || true
    cd ..
fi

# Build CLI module
if [ -d "cli" ]; then
    log "Building CLI module..."
    cd cli
    mvn package -DskipTests -q 2>&1 | tee -a "$LOG_FILE"
    cd ..
fi

# Find the jdeploy JAR
JDEPLOY_JAR=$(find "$JDEPLOY_DIR/cli/target" -name "jdeploy-cli-*.jar" ! -name "*-sources*" ! -name "*-javadoc*" 2>/dev/null | head -n 1)

if [ -z "$JDEPLOY_JAR" ]; then
    log "ERROR: Could not find jdeploy JAR after build"
    exit 1
fi

log "Found jdeploy JAR: $JDEPLOY_JAR"

# Read the run arguments (passed via environment or file)
RUN_ARGS=""
if [ -f "/config/shared/run-args.txt" ]; then
    RUN_ARGS=$(cat /config/shared/run-args.txt)
fi

# Change to app directory and run jdeploy
log "Changing to app directory: $APP_DIR"
cd "$APP_DIR"

# Ensure DISPLAY is set for GUI apps
export DISPLAY=:1

# Run jdeploy install first
log "Running: java -jar $JDEPLOY_JAR install"
java -jar "$JDEPLOY_JAR" install 2>&1 | tee -a "$LOG_FILE"

log "Installation complete. Now launching the app..."

# Run jdeploy run to launch the app (in foreground so it stays open)
log "Running: java -jar $JDEPLOY_JAR run $RUN_ARGS"
java -jar "$JDEPLOY_JAR" run $RUN_ARGS 2>&1 | tee -a "$LOG_FILE" &

# Give the app a moment to start
sleep 2

log "Dev install and launch completed"
log "Check $LOG_FILE for details"

# Keep terminal open for debugging (remove this line once autostart works reliably)
log "Press Enter to close this terminal..."
read
