#!/bin/bash
set -e

# Build for x64 (AMD64)
clang -target x86_64-pc-windows-msvc -o mock_launcher_win_x64.exe mock_launcher_win.c -std=c99 -lole32 -lshell32

# Build for ARM64
clang -target aarch64-pc-windows-msvc -o mock_launcher_win_arm64.exe mock_launcher_win.c -std=c99 -lole32 -lshell32

echo "Built executables:"
echo "  mock_launcher_win_x64.exe - for x64/AMD64 systems"
echo "  mock_launcher_win_arm64.exe - for ARM64 systems"