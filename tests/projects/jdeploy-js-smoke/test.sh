#!/usr/bin/env bash
set -euo pipefail

# Run from this script's directory
cd "$(dirname "$0")"

# If Node.js is not available, skip the test gracefully
if ! command -v node >/dev/null 2>&1; then
  echo "SKIP: Node.js is not installed"
  exit 0
fi

# Execute the local launcher with sample arguments
output="$(node jdeploy-bundle/jdeploy.js hello world)"

# Show output for logs
echo "$output"

# Validate markers
if ! grep -q '^LAUNCHED$' <<<"$output"; then
  echo "FAIL: Launcher did not run (missing LAUNCHED marker)"
  exit 1
fi

if ! grep -q '^ARGS:hello world$' <<<"$output"; then
  echo "FAIL: Arguments were not forwarded correctly"
  exit 1
fi

echo "PASS: jdeploy-js-smoke"
