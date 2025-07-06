#!/bin/bash
set -e

# Check if the system is Windows ARM, and force AMD64 compilation
if [[ $(uname -m) == "aarch64" ]]; then
    # Force compilation for amd64 on Windows ARM
    gcc -o mock_launcher_win.exe mock_launcher_win.c --std=c99 -march=x86-64
else
    # Normal compilation for other architectures
    gcc -o mock_launcher_win.exe mock_launcher_win.c --std=c99
fi
