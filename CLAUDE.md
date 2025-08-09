# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

### Primary Build
```bash
# Build entire project (shared library + CLI)
./build_and_test.sh

# Build shared library only
cd shared && mvn clean install

# Build CLI only  
cd cli && mvn clean package

# Run integration tests
cd tests && bash test.sh
```

### Maven Commands
```bash
# Test with Maven
mvn test

# Build with Ant (root project uses NetBeans Ant)
ant clean && ant jar
```

### Environment Variables
- `JDEPLOY_SKIP_INTEGRATION_TESTS` - Set to skip integration tests during build

## Architecture Overview

jDeploy is a Java desktop app deployment tool with a multi-module architecture:

### Core Modules
- **`cli/`** - Main jDeploy CLI application (Maven project)
- **`shared/`** - Shared libraries used by CLI and installer (Maven project)  
- **`installer/`** - Native installer generation (Maven project)
- **Root project** - Uses Ant/NetBeans build system

### Key Components
- **CLI (`cli/src/main/java/ca/weblite/jdeploy/`)** - Main application logic
  - `JDeploy.java` - Main entry point
  - `services/` - Core services (publishing, packaging, project generation)
  - `publishing/` - GitHub and NPM publishing drivers
  - `packaging/` - JAR packaging and bundling
  - `gui/` - Swing GUI components

- **Shared (`shared/src/main/java/ca/weblite/jdeploy/`)** - Common utilities
  - `models/` - Data models (AppManifest, JDeployProject)
  - `services/` - Shared services (signing, verification)
  - `appbundler/` - Native app bundling
  - `jvmdownloader/` - JVM embedding functionality

### Build System Details
- Root project uses **Ant** with NetBeans project structure
- Individual modules use **Maven** with Java 8 compatibility  
- Custom build hooks in `build.xml` embed jar-runner utility
- Local Maven repository at `maven-repository/` for custom dependencies

### Publishing Targets
- **GitHub Actions** - Automated installer generation via GitHub workflow
- **NPM** - CLI tool distribution
- **Maven Central** - Plugin and archetype distribution

### Platform Support
- Windows (x64, ARM64 with new ShellLink_ARM64.dll)
- macOS (x64, ARM64) 
- Linux (x64)

### Test Structure
- Unit tests in each module's `src/test/java/`
- Integration tests in `tests/projects/` with individual test scenarios
- Mock projects for testing different deployment configurations

## Important Files
- `package.json` files - NPM metadata for deployable apps
- `jdeploy.js` - JavaScript shim for running Java apps via NPM
- `action.yml` - GitHub Action definition
- `build_and_test.sh` - Main build script