# Local Docker Testing

## Overview

The local Docker testing feature allows developers to test their jDeploy applications on Linux and Windows platforms using Docker containers, without needing physical access to those platforms. This enables cross-platform testing directly from macOS, Linux, or Windows development machines.

## Command Syntax

### Linux Testing

```bash
# Interactive mode with VNC/browser access
jdeploy run --linux=vnc

# Headless automated testing
jdeploy run --linux=headless
jdeploy run --linux

# Clean state (fresh container)
jdeploy run --linux=vnc,clean
```

### Windows Testing

```bash
# Interactive mode with web-based viewer
jdeploy run --windows=rdp

# Headless automated testing
jdeploy run --windows=headless
jdeploy run --windows

# Clean state (from golden snapshot)
jdeploy run --windows=rdp,clean
```

## Modes

### Interactive Mode (`vnc` / `rdp`)

Opens a graphical desktop environment accessible via web browser:

- **Linux**: Opens browser to `http://localhost:3000` (noVNC web interface)
- **Windows**: Opens browser to `http://localhost:8006` (web-based viewer)

The developer can interact with the desktop, run the application, and verify the installation manually.

### Headless Mode (`headless`)

Runs automated installation and verification checks without user interaction. Returns pass/fail results for:

- Application directory structure
- Executable presence
- Desktop shortcuts
- Menu entries
- CLI commands (if configured)

## State Management

### Default Behavior (Persistent State)

By default, containers reuse their previous state for fast startup:

- **Linux**: Reuses existing container and config volume
- **Windows**: Reuses existing working volume with installed Windows

This allows rapid iteration during development without waiting for OS installation or container initialization.

### Clean Mode (`clean`)

Adding `clean` to the options starts from a fresh state:

- **Linux**: Removes existing container and volume, creates fresh container
- **Windows**: Copies from golden snapshot to working volume, providing clean Windows installation

```bash
jdeploy run --linux=vnc,clean
jdeploy run --windows=rdp,clean
```

### Windows Golden Snapshot

Windows testing uses a two-volume approach:

1. **Golden Volume**: Contains a clean Windows installation (created on first run)
2. **Working Volume**: Copy of golden used for actual testing

First run installs Windows to the golden volume (~20 minutes on macOS without KVM). Subsequent runs copy from golden to working (~30 seconds), providing clean state without reinstallation.

## Dev Mode

When running from a jDeploy development environment (the jDeploy source project), the container automatically:

1. Mounts the jDeploy source directory
2. Builds jDeploy from source inside the container
3. Uses the freshly built jDeploy for installation

This enables testing changes to jDeploy itself without publishing.

## Prerequisites

### Docker

Docker Desktop (or Docker Engine on Linux) must be installed and running.

```bash
# Verify Docker is available
docker info
```

### Platform-Specific Notes

#### macOS

- Windows testing runs without KVM acceleration (software emulation)
- Significantly slower than Linux hosts (~20+ minutes for first Windows boot)
- Linux testing uses ARM64-compatible images on Apple Silicon

#### Linux

- Windows testing benefits from KVM acceleration (fast)
- Ensure user has access to `/dev/kvm` for hardware virtualization

## Container Details

### Linux Container

- **Image**: `linuxserver/webtop:ubuntu-xfce`
- **Container Name**: `jdeploy-linux-test`
- **Config Volume**: `jdeploy-linux-config`
- **Ports**: 3000 (web), 5901 (VNC)

### Windows Container

- **Image**: `dockurr/windows`
- **Container Name**: `jdeploy-windows-test`
- **Golden Volume**: `jdeploy-windows-golden`
- **Working Volume**: `jdeploy-windows-storage`
- **Ports**: 8006 (web), 3389 (RDP)

## Stopping Containers

Press `Ctrl+C` to stop the container gracefully. The container will be stopped using `docker stop`.

To manually stop or remove containers:

```bash
# Stop containers
docker stop jdeploy-linux-test
docker stop jdeploy-windows-test

# Remove containers
docker rm jdeploy-linux-test
docker rm jdeploy-windows-test

# Remove volumes (resets to fresh state)
docker volume rm jdeploy-linux-config
docker volume rm jdeploy-windows-golden jdeploy-windows-storage
```

## Verification Checks

In headless mode, the following checks are performed:

### Linux

- App directory exists
- Executable is present and runnable
- Desktop shortcut created (if applicable)
- Applications menu entry created
- CLI commands installed and in PATH (if configured)

### Windows

- App directory exists
- Executable (.exe) is present
- Desktop shortcut created
- Start menu entry created
- Registry uninstall entry created

## Exit Codes

- `0`: All verification checks passed
- `1`: One or more checks failed or an error occurred

## Limitations

- **Windows on macOS**: Very slow due to software CPU emulation (no KVM)
- **First Windows run**: Takes ~20 minutes to install Windows
- **Windows ARM64**: Not currently supported (x86_64 emulation only)
- **Network-dependent features**: May require additional Docker network configuration

## Future Enhancements

- UTM integration for faster Windows testing on macOS
- ARM64 Windows support
- Parallel multi-platform testing
- Integration with CI/CD pipelines
- Custom verification scripts
