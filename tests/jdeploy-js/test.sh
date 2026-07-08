#!/bin/bash
# Integration tests for the generated jdeploy.js launcher.
set -e
SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
cd "$SCRIPTPATH"

if ! command -v node >/dev/null 2>&1; then
  echo "Skipping jdeploy.js launcher tests: node is not installed."
  exit 0
fi

# The package-args test has no native dependencies and is platform-independent
# (it mocks process.platform), so run it everywhere before any early exit.
echo "Running jdeploy.js package-args test..."
node package-args.test.js

# Windows has no Unix execute bit, so the zip-mode test does not apply there.
case "$(uname -s)" in
  MINGW*|MSYS*|CYGWIN*)
    echo "Skipping jdeploy.js zip-mode test on Windows."
    exit 0
    ;;
esac

echo "Installing jdeploy.js test dependencies..."
npm install --no-audit --no-fund --silent

echo "Running jdeploy.js launcher tests..."
node extract-zip-modes.test.js

echo "jdeploy.js launcher tests passed"
