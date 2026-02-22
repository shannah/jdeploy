# jDeploy E2E Linux Installation Tests

End-to-end tests for verifying jDeploy headless installation, verification, and uninstallation on Linux.

## Overview

These tests build jdeploy from source inside a Docker container, then use it to verify that jDeploy applications can be:

1. **Installed** - Using the headless installer from jdeploy.com
2. **Verified** - Using `jdeploy verify-installation` command
3. **Uninstalled** - Using the headless uninstaller
4. **Verified (uninstall)** - Using `jdeploy verify-uninstallation` command

## Prerequisites

- Docker Desktop (or Docker Engine on Linux)
- Bash shell

## Quick Start

```bash
# Run all E2E tests
./run-docker-tests.sh

# Run tests for a specific app
./run-docker-tests.sh --app=jdeploy-demo-swingset2

# Run with verbose output
./run-docker-tests.sh --verbose

# Use dev.jdeploy.com instead of www.jdeploy.com
./run-docker-tests.sh --jdeploy-url=dev.jdeploy.com

# Skip uninstall testing (install and verify only)
./run-docker-tests.sh --skip-uninstall
```

## Test Applications

The test applications are defined in `apps.conf`:

| Package Name | Description |
|--------------|-------------|
| jdeploy-demo-swingset3 | SwingSet3 - canonical Swing demo app |
| jdeploy-demo-javafx-ensemble | JavaFX Ensemble - 100+ JavaFX sample apps |
| snapcharts | SnapCharts - Chart design app |
| snapcodejava | SnapCode - Java IDE for education |
| osgifx | OSGi.fx - OSGi framework manager |

## Configuration

Edit `apps.conf` to add or remove test applications:

```
# Format: PACKAGE_NAME|SOURCE_URL|DESCRIPTION
# SOURCE_URL can be empty for npm-published packages

my-package||My Package Description
my-github-app|https://github.com/user/repo|GitHub App
```

## Output

Results are saved to the `results/` directory:

- `e2e-test-{timestamp}.log` - Main test log
- `{package}-install.log` - Installation output per app
- `{package}-verify-install.log` - Verification output per app
- `{package}-uninstall.log` - Uninstall output per app
- `{package}-verify-uninstall.log` - Uninstall verification output per app
- `{package}-result.txt` - Pass/fail result per app
- `summary.json` - Overall test summary

## Exit Codes

- `0` - All tests passed
- `1` - One or more tests failed
- `2` - Configuration error (missing config, Docker not available, etc.)

## Docker Container Details

- **Image**: `ubuntu:22.04`
- **Container Name**: `jdeploy-e2e-linux-test`
- **Virtual Display**: Xvfb on :99 for headless GUI support

The container:
1. Mounts the jdeploy source directory (read-only)
2. Copies and builds jdeploy with `mvn package`
3. Uses `/tmp/jdeploy/bin/jdeploy` for all commands

Installed packages:
- curl, wget, jq
- GTK3 libraries for GUI apps
- Xvfb for headless display
- OpenJDK (full JDK for Maven)
- Maven (for building jdeploy)

## Adding New Test Apps

1. Verify the app is published to jdeploy.com:
   ```bash
   curl -I "https://www.jdeploy.com/~{package-name}/install.sh"
   ```

2. Add to `apps.conf`:
   ```
   package-name||Description of the app
   ```

3. Run the tests:
   ```bash
   ./run-docker-tests.sh --app=package-name --verbose
   ```

## Troubleshooting

### Tests fail with "Docker not available"

Ensure Docker Desktop is running:
```bash
docker info
```

### Tests fail during installation

Check the installation log in `results/{package}-install.log` for details.

### Keep container for debugging

```bash
./run-docker-tests.sh --keep-container
docker exec -it jdeploy-e2e-linux-test /bin/bash
```
