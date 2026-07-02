#!/bin/bash
# Integration tests for the generated jdeploy.js launcher.
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
cd "$SCRIPTPATH"

# Windows has no Unix execute bit, so the zip-mode test does not apply there.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    echo "Skipping jdeploy.js launcher tests on Windows."
    exit 0
    ;;
esac

if ! command -v node >/dev/null 2>&1; then
  echo "Skipping jdeploy.js launcher tests: node is not installed."
  exit 0
fi

echo "Installing jdeploy.js test dependencies..."
npm install --no-audit --no-fund --silent

echo "Running jdeploy.js launcher tests..."
node extract-zip-modes.test.js

echo "jdeploy.js launcher tests passed"
