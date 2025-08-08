# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build Windows executables for both architectures
./build.sh

# Manual builds (alternatives)
# For x64/AMD64
clang -target x86_64-pc-windows-msvc -o mock_launcher_win_x64.exe mock_launcher_win.c -std=c99 -lole32 -lshell32

# For ARM64  
clang -target aarch64-pc-windows-msvc -o mock_launcher_win_arm64.exe mock_launcher_win.c -std=c99 -lole32 -lshell32
```

## Usage

This is a mock launcher for jDeploy installer integration tests. Use:

- `mock_launcher.sh` - For Mac/Linux testing
- `mock_launcher_win_x64.exe` - For Windows x64/AMD64 testing
- `mock_launcher_win_arm64.exe` - For Windows ARM64 testing

Both launchers accept `install` or `uninstall` arguments and are designed to simulate jDeploy app launchers for testing the installer.

## Architecture

### Core Components

- **`mock_launcher_win.c`** - Windows launcher implementation in C
  - Handles environment variable loading from `~/.jdeploy/.env.dev`
  - Converts Unix-style paths to Windows format
  - Executes Java installer JAR with proper system properties
  - Requires `JAVA_HOME` and `JDEPLOY_PROJECT_PATH` environment variables

- **`mock_launcher.sh`** - Unix shell script equivalent
  - Executes installer JAR with client4j properties
  - Uses `JAVA_HOME/bin/java` to run installer
  - Passes through command line arguments

### Environment Requirements

The Windows launcher expects these environment variables (set in `~/.jdeploy/.env.dev`):
- `JAVA_HOME` - Path to JDK installation
- `JDEPLOY_PROJECT_PATH` - Path to jDeploy project root
- `JDEPLOY_INSTALLER_ARGS` - Optional additional arguments

### Key Features

- Cross-platform launcher simulation for installer testing
- Multi-architecture Windows support (x64 and ARM64)
- Windows executables compatible with jDeploy uninstaller requirements
- Environment file loading support for development setup
- Path conversion utilities for Unix/Windows compatibility