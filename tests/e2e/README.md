# jDeploy E2E Installation Tests

End-to-end tests for verifying jDeploy headless installation, verification, and uninstallation across all platforms.

## Overview

These tests verify that jDeploy applications can be:

1. **Installed** - Using the headless installer from jdeploy.com
2. **Verified** - Using `jdeploy verify-installation` command
3. **Uninstalled** - Using `jdeploy uninstall` command
4. **Verified (uninstall)** - Using `jdeploy verify-uninstallation` command

## Prerequisites

- Java 17+ (for building jdeploy)
- Maven (for building jdeploy)
- curl or wget (Linux/macOS)
- PowerShell (Windows)

## Running Locally

### Build jdeploy first

```bash
# From project root
mvn clean install -DskipTests
```

### Linux/macOS

```bash
cd tests/e2e
./e2e-test.sh --verbose

# Test a specific app
./e2e-test.sh --app=jdeploy-demo-swingset3 --verbose

# Skip uninstall testing
./e2e-test.sh --skip-uninstall --verbose
```

### Windows (PowerShell)

```powershell
cd tests\e2e
.\e2e-test.ps1 -Verbose

# Test a specific app
.\e2e-test.ps1 -App "jdeploy-demo-swingset3" -Verbose

# Skip uninstall testing
.\e2e-test.ps1 -SkipUninstall -Verbose
```

### Using Docker (Linux only)

For isolated testing in a clean Linux container:

```bash
cd tests/e2e/linux
./run-docker-tests.sh --verbose
```

## GitHub Actions

The E2E tests run automatically on:
- Push to master
- Pull requests to master

You can also trigger them manually from the Actions tab with optional parameters:
- `app` - Test only a specific app
- `jdeploy_url` - Use a custom jdeploy.com URL (e.g., dev.jdeploy.com)

## Test Applications

Applications to test are defined in `apps.conf`:

```
# Format: PACKAGE_NAME|SOURCE_URL|DESCRIPTION
jdeploy-demo-swingset3||SwingSet3 - canonical Swing demo app
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
- `2` - Configuration error (missing config, Java not available, etc.)

## Adding New Test Apps

1. Verify the app is published to jdeploy.com:
   ```bash
   curl -I "https://www.jdeploy.com/~{package-name}/install.sh"
   ```

2. Add to `apps.conf`:
   ```
   package-name||Description of the app
   ```

3. Run the tests to verify:
   ```bash
   ./e2e-test.sh --app=package-name --verbose
   ```
