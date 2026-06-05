# jDeploy Local Development Guide

This guide covers building jDeploy from source and manually testing the CLI, GUI, and installer during development.

## Prerequisites

- **JDK 8** — the project targets Java 8 (`maven.compiler.source`/`target` are `1.8`)
- **Maven 3.9+**

The repository includes a [`.tool-versions`](.tool-versions) file pinning `java zulu-8` and `maven 3.9.9`, so if you use [mise](https://mise.jdx.dev/) (or another asdf-compatible tool manager), the correct toolchain is selected automatically.

## Project Structure

| Module | Description |
|---|---|
| `shared/` | Shared library used by the CLI and installer (Maven) |
| `cli/` | The main jDeploy CLI/GUI application (Maven) |
| `installer/` | The native installer application (Maven) |

The root `pom.xml` is an aggregator that builds `shared`, `cli`, and `installer` in order.

## Building

```bash
# Build everything and run integration tests
./build_and_test.sh

# Build a single module and the modules it depends on (skipping tests)
mvn -pl cli -am package -DskipTests
mvn -pl installer -am package -DskipTests
```

Each module's `package` phase copies its runtime dependencies into `target/libs/` (via the `maven-dependency-plugin`), so a module can be run directly from `target/classes` plus `target/libs/*` without shading.

Set `JDEPLOY_SKIP_INTEGRATION_TESTS=1` to skip integration tests during `./build_and_test.sh`.

## Manual Testing Scripts

These scripts replicate the IntelliJ run/debug configurations used during development, so you can test from the command line without an IDE. Both scripts:

- Select a JDK 8 automatically (they honor `$JAVA_HOME` if it points to a JDK, otherwise fall back to `/usr/libexec/java_home -v 1.8` on macOS)
- Build the required Maven modules on first run, or when invoked with `--build`

### `test_jdeploy_gui.sh` — Launch the jDeploy GUI

Launches the jDeploy GUI (`ca.weblite.jdeploy.JDeploy` from the `jdeploy-cli` module) against a project directory, the same way the **JDeploy** IntelliJ run configuration does.

```bash
# Open the default demo project (/Users/shannah/jdeploy-demos/jdeploy-service-example)
./test_jdeploy_gui.sh

# Open a specific project
./test_jdeploy_gui.sh /path/to/project

# Force a rebuild of the cli module first
./test_jdeploy_gui.sh --build [/path/to/project]
```

Environment overrides:

| Variable | Purpose |
|---|---|
| `JDEPLOY_PROJECT_DIR` | Project to open (same as passing a project dir argument) |
| `JAVA_HOME` | JDK to use |

### `test_installer_debug.sh` — Test the Install Wizard

Runs the installer in debug mode (`ca.weblite.jdeploy.installer.MainDebug` from the `jdeploy-installer` module), the same way the **JDeploy Installer** IntelliJ run configuration does. It downloads the `.jdeploy-files` bundle for an app code from the registry, then launches the install wizard against it.

```bash
# Install the default test app (code 26AD, version 1.0.15)
./test_installer_debug.sh

# Install a specific app code and version
./test_installer_debug.sh <code> [version]

# Headless install (no GUI; output logged to ~/.jdeploy/log/jdeploy-headless-install.log)
./test_installer_debug.sh <code> <version> install

# Force a rebuild of the installer module first
./test_installer_debug.sh --build [args...]
```

Environment overrides:

| Variable | Purpose |
|---|---|
| `JDEPLOY_REGISTRY_URL` | Registry to download the bundle from (default `https://dev.jdeploy.com/`) |
| `JAVA_HOME` | JDK to use |

The script sets `JDEPLOY_DEBUG=1`, so HTTP requests and other debug information are printed to the console. Installer output is also logged to `~/.jdeploy/log/jdeploy-installer.log`.

## Running Integration Tests

```bash
cd tests && bash test.sh
```

Integration test projects live in `tests/projects/`, each exercising a different deployment configuration.
