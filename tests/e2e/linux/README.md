# jDeploy E2E Linux Docker Tests

Docker-based E2E tests for Linux. This provides an isolated testing environment using Docker containers.

For running tests directly (without Docker), see the parent directory's [README](../README.md).

## Prerequisites

- Docker Desktop (or Docker Engine on Linux)
- Bash shell

## Quick Start

```bash
# Run all E2E tests in Docker
./run-docker-tests.sh

# Run tests for a specific app
./run-docker-tests.sh --app=jdeploy-demo-swingset3

# Run with verbose output
./run-docker-tests.sh --verbose

# Use dev.jdeploy.com instead of www.jdeploy.com
./run-docker-tests.sh --jdeploy-url=dev.jdeploy.com

# Skip uninstall testing (install and verify only)
./run-docker-tests.sh --skip-uninstall
```

## How It Works

The Docker wrapper:
1. Starts an Ubuntu 22.04 container
2. Installs Java, Maven, and GUI libraries
3. Builds jdeploy from source inside the container
4. Runs the shared E2E test script (`../e2e-test.sh`)
5. Copies results back to `results/`

## Test Applications

Test apps are defined in `../apps.conf` (shared with other platforms).

## Docker Container Details

- **Image**: `ubuntu:22.04`
- **Container Name**: `jdeploy-e2e-linux-test`
- **Virtual Display**: Xvfb on :99 for headless GUI support

## Troubleshooting

### Tests fail with "Docker not available"

Ensure Docker Desktop is running:
```bash
docker info
```

### Keep container for debugging

```bash
./run-docker-tests.sh --keep-container
docker exec -it jdeploy-e2e-linux-test /bin/bash
```
