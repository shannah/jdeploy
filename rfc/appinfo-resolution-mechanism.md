# RFC: AppInfo Resolution Mechanism in jDeploy Installer

**Status:** Documented
**Author:** Analysis of existing implementation
**Date:** 2026-01-17
**Component:** jDeploy Installer (`installer/`)
**Entry Point:** `Main.java`

## Abstract

This RFC documents the multi-layered mechanism by which the jDeploy installer locates, loads, and enriches application metadata (`AppInfo`) during the installation process. The system uses multiple fallback strategies to ensure installers work across diverse scenarios: development, production, code-signed applications, macOS Gatekeeper, and network-based installations.

## Table of Contents

1. [Overview](#overview)
2. [Execution Flow](#execution-flow)
3. [Phase 1: Locating app.xml](#phase-1-locating-appxml)
4. [Phase 2: Parsing app.xml](#phase-2-parsing-appxml)
5. [Phase 3: Registry Enrichment](#phase-3-registry-enrichment)
6. [Filename Conventions](#filename-conventions)
7. [Examples](#examples)
8. [Implementation Details](#implementation-details)
9. [Error Handling](#error-handling)

## Overview

### Purpose

The installer must determine:
- **What** application to install (package name, source)
- **Which version** to install
- **Where** to find installation files (`app.xml`, icons, resources)
- **Additional metadata** (description, permissions, file associations)

### Key Challenge

The installer JAR is distributed as a standalone executable. It must discover its installation context without relying on:
- Fixed file paths (may run from translocated/temporary locations)
- Command-line arguments (double-click execution)
- Hardcoded metadata (single installer JAR supports multiple apps)

### Solution Approach

Multi-layered resolution with intelligent fallbacks:
1. System property overrides (development/testing)
2. Bundle code extraction from filename (network download)
3. Local `.jdeploy-files` directory search (standard installation)
4. NPM registry enrichment (package.json metadata)

## Execution Flow

```
Main.main(args)
  └─> Main.run()
      └─> Main.run0()
          └─> Main.loadAppInfo()
              ├─> Phase 1: DefaultInstallationContext.findAppXml()
              │   ├─> Check system property client4j.appxml.path
              │   └─> DefaultInstallationContext.findInstallFilesDir()
              │       ├─> Extract bundle code from filename
              │       ├─> Download from jDeploy registry (if code found)
              │       ├─> Handle macOS Gatekeeper translocation
              │       └─> Recursive search for .jdeploy-files/
              │
              ├─> Phase 2: Parse app.xml into AppInfo
              │   ├─> Extract package name, title, version, source
              │   └─> Generate macOS bundle ID
              │
              └─> Phase 3: loadNPMPackageInfo()
                  ├─> Query NPM/GitHub registry
                  ├─> Resolve version (latest, semver, branch)
                  └─> Enrich with package.json metadata
```

## Phase 1: Locating app.xml

### 1.1 System Property Override (Highest Priority)

**Property:** `client4j.appxml.path`

```java
if (System.getProperty("client4j.appxml.path") != null) {
    return new File(System.getProperty("client4j.appxml.path"));
}
```

**Use Cases:**
- Development and testing
- Automated test suites
- Custom installer configurations

**Example:**
```bash
java -Dclient4j.appxml.path=/path/to/app.xml -jar installer.jar
```

### 1.2 Bundle Code Extraction from Filename

**Property:** `client4j.launcher.path` (set by launcher wrapper)

When the installer is launched via a native wrapper (`.exe`, `.app`), this property points to the launcher executable.

**Filename Pattern:** `AppName_VERSION_BUNDLECODE.extension`

#### 1.2.1 Extract Bundle Code

**Method:** `extractJDeployBundleCodeFromFileName(String fileName)`

```java
// Extracts everything after the last underscore
// that is uppercase alphanumeric (A-Z, 0-9)
// Example: "TextEditor_1.0.0_A1B2C3.exe" -> "A1B2C3"
```

**Algorithm:**
1. Find last `_` in filename
2. Extract characters after `_`
3. Keep only uppercase letters (A-Z) and digits (0-9)
4. Stop at first non-matching character

**Examples:**
- `MyApp_1.0.0_ABC123.exe` → `ABC123`
- `Foo_2.1.0_XYZ789.app` → `XYZ789`
- `Bar_1.0.0_invalid.dmg` → `null` (lowercase not allowed)

#### 1.2.2 Extract Version

**Method:** `extractVersionFromFileName(String fileName)`

```java
// Extracts version between app name and bundle code
// Example: "TextEditor_1.0.0_A1B2C3.exe" -> "1.0.0"
```

**Algorithm:**
1. Remove everything after last `_` (removes bundle code)
2. Find last `_` in remaining string
3. Extract everything after that `_` as version
4. Handle GitHub branches: `@branch` → `0.0.0-branch`

**Examples:**
- `MyApp_1.0.0_ABC123.exe` → `1.0.0`
- `Foo_2.1.3-beta.5_XYZ789.app` → `2.1.3-beta.5`
- `Bar@main_ABC123.dmg` → `0.0.0-main` (GitHub branch)

#### 1.2.3 Download from jDeploy Registry

If both bundle code and version are extracted:

```
https://www.jdeploy.com/download?code={CODE}&version={VERSION}&jdeploy_files=true&platform=*
```

**Response:** ZIP file containing `.jdeploy-files/` directory with:
- `app.xml` - Application manifest
- `icon.png` - Application icon
- `icon.directory.png` - Directory association icon (optional)
- `icon.{ext}.png` - Document type icons (optional)
- `installsplash.png` - Installation splash screen (optional)

**Caching:**
- Bundle metadata cached in `~/.jdeploy/registry-cache/`
- Subsequent lookups use cache before hitting network

**Fallback Registries:**
The system supports fallback registries configured via `JDEPLOY_REGISTRY_FALLBACK` environment variable (comma-separated URLs).

### 1.3 macOS Gatekeeper Special Handling

**Scenario:** macOS Gatekeeper App Translocation

When an unsigned/unnotarized `.app` is first opened, macOS copies it to:
```
/private/var/folders/.../AppTranslocation/{random-id}/d/MyApp.app
```

**Detection:**
```java
if (Platform.getSystemPlatform().isMac() &&
    "AppTranslocation".equals(startDir.getName()))
```

**Resolution:**
1. Detect translocation by checking for `AppTranslocation` in path
2. Find original `.app` bundle using `findAppBundle()`
3. Extract bundle code from `.app` bundle name
4. Extract version from bundle name
5. Download from registry (can't access original `.jdeploy-files`)

**Why This Works:**
Even though the app is in a randomized location, the `.app` bundle name is preserved, allowing bundle code extraction.

### 1.4 Recursive `.jdeploy-files` Search

**Fallback mechanism** when no system properties or bundle codes are available.

**Starting Points:**
1. `client4j.appxml.path` parent directory (if property set)
2. `client4j.launcher.path` directory (if property set)
3. `user.dir` (current working directory)

**Algorithm:**
```java
private File findInstallFilesDir(File startDir) {
    File candidate = new File(startDir, ".jdeploy-files");
    if (candidate.exists() && candidate.isDirectory()) {
        return candidate;
    }
    // Recurse up the directory tree
    return findInstallFilesDir(startDir.getParentFile());
}
```

**Search Order Example:**
```
/Users/me/Downloads/MyApp.jar
  └─> Check /Users/me/Downloads/.jdeploy-files
      └─> Check /Users/me/.jdeploy-files
          └─> Check /Users/.jdeploy-files
              └─> Check /.jdeploy-files
                  └─> null (not found)
```

**Use Cases:**
- Development: JAR in project directory with sibling `.jdeploy-files/`
- Testing: Extracted installer bundle
- Legacy installers without bundle codes

## Phase 2: Parsing app.xml

Once `app.xml` is located, it's parsed into an `AppInfo` object.

### app.xml Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<app
    package="@myorg/myapp"
    title="My Application"
    version="1.2.3"
    source="https://github.com/myorg/myapp"
    macAppBundleId="com.myorg.myapp">

    <!-- Additional configuration in package.json, not app.xml -->
</app>
```

### Required Attributes

| Attribute | Required | Default | Description |
|-----------|----------|---------|-------------|
| `package` | **Yes** | - | NPM package name (e.g., `@myorg/myapp`) |
| `title` | No | `package` value | Human-readable application name |
| `version` | No | `"latest"` | Version to install (semver, tag, or branch) |
| `source` | No | `null` (NPM) | Package source (GitHub URL for GitHub packages) |
| `macAppBundleId` | No | Generated | macOS bundle identifier (reverse-DNS) |

### AppInfo Population

```java
AppInfo appInfo = new AppInfo();
appInfo.setTitle(ifEmpty(root.getAttribute("title"),
                         root.getAttribute("package"),
                         null));
appInfo.setNpmPackage(ifEmpty(root.getAttribute("package"), null));
appInfo.setNpmSource(root.getAttribute("source"));
appInfo.setNpmVersion(ifEmpty(root.getAttribute("version"), "latest"));

// Generate macOS bundle ID
String fullyQualifiedPackageName = appInfo.getNpmPackage();
if (appInfo.getNpmSource() != null && !appInfo.getNpmSource().isEmpty()) {
    // GitHub packages: prefix with MD5 of source URL
    fullyQualifiedPackageName = MD5.getMd5(appInfo.getNpmSource()) +
                                 "." + fullyQualifiedPackageName;
}

String bundleSuffix = "";
if (appInfo.getNpmVersion().startsWith("0.0.0-")) {
    // GitHub branches get suffixed with branch name
    bundleSuffix = "." + version.substring(version.indexOf("-") + 1);
}

appInfo.setMacAppBundleId(
    ifEmpty(root.getAttribute("macAppBundleId"),
            "ca.weblite.jdeploy.apps." + fullyQualifiedPackageName + bundleSuffix)
);
```

### Bundle ID Generation Examples

| Package | Source | Version | Bundle ID |
|---------|--------|---------|-----------|
| `myapp` | (NPM) | `1.0.0` | `ca.weblite.jdeploy.apps.myapp` |
| `@org/app` | (NPM) | `2.1.0` | `ca.weblite.jdeploy.apps.@org/app` |
| `myapp` | `github.com/me/app` | `1.0.0` | `ca.weblite.jdeploy.apps.{MD5}.myapp` |
| `myapp` | (NPM) | `0.0.0-main` | `ca.weblite.jdeploy.apps.myapp.main` |

## Phase 3: Registry Enrichment

After parsing `app.xml`, the installer queries the NPM or GitHub registry to:
1. Resolve version (convert `"latest"`, semver, branches to specific version)
2. Download `package.json` metadata
3. Enrich `AppInfo` with additional fields

### 3.1 Registry Query

```java
NPMRegistry registry = new NPMRegistry();
NPMPackage pkg = registry.loadPackage(
    appInfo.getNpmPackage(),
    appInfo.getNpmSource(),
    successfulTag  // From GitHub download if applicable
);

NPMPackageVersion version = pkg.getLatestVersion(
    installationSettings.isPrerelease(),
    appInfo.getNpmVersion()
);

installationSettings.setNpmPackageVersion(version);
```

### 3.2 Version Resolution

| app.xml version | Registry Resolution |
|-----------------|---------------------|
| `"latest"` | Latest stable release |
| `"1.x"` or `"^1.0.0"` | Latest 1.x.x version |
| `"~1.2.0"` | Latest 1.2.x patch version |
| `"1.2.3"` | Exact version 1.2.3 |
| `"0.0.0-main"` | GitHub `main` branch (latest commit) |
| `"0.0.0-v1.0.0"` | GitHub tag `v1.0.0` |

### 3.3 Metadata Enrichment

**From `package.json` → `AppInfo`:**

```java
// Basic metadata
if (appInfo.getDescription() == null) {
    appInfo.setDescription(npmPackageVersion.getDescription());
}

// Fallback if still null
if (appInfo.getDescription() == null) {
    appInfo.setDescription("Desktop application");
}

// Document type associations
for (DocumentTypeAssociation assoc : documentTypeAssociations) {
    appInfo.addDocumentMimetype(assoc.getExtension(), assoc.getMimetype());

    // Icon paths
    File iconPath = new File(installFilesDir, "icon." + assoc.getExtension() + ".png");
    if (iconPath.exists()) {
        appInfo.addDocumentTypeIcon(assoc.getExtension(), iconPath.getAbsolutePath());
    }

    // Editor flag
    if (assoc.isEditor()) {
        appInfo.setDocumentTypeEditor(assoc.getExtension());
    }
}

// Directory associations
if (directoryAssociation != null) {
    appInfo.setDirectoryAssociation(directoryAssociation);
}

// URL schemes
for (String scheme : urlSchemes) {
    appInfo.addUrlScheme(scheme);
}

// Permission requests
for (Map.Entry<String, PermissionRequest> entry : permissionRequests.entrySet()) {
    appInfo.addPermissionRequest(entry.getKey(), entry.getValue());
}

// JVM settings
if (usePrivateJVM) {
    appInfo.setUsePrivateJVM(true);
}

// Admin requirements
if (requireRunAsAdmin) {
    appInfo.setRequireRunAsAdmin(true);
}
if (allowRunAsAdmin) {
    appInfo.setAllowRunAsAdmin(true);
}
```

## Filename Conventions

### Installer/Launcher Naming Pattern

```
{AppName}_{VERSION}_{BUNDLECODE}.{extension}
```

**Components:**

1. **AppName**: Application display name (can contain `@` for branches)
2. **VERSION**: Semantic version or `0.0.0-{branch}` for GitHub branches
3. **BUNDLECODE**: Uppercase alphanumeric identifier for registry lookup
4. **extension**: Platform-specific (`.exe`, `.app`, `.dmg`, `.jar`)

**Underscore Separators:**
- First `_`: Separates app name from version
- Last `_`: Separates version from bundle code

### Examples

#### NPM Packages

```
TextEditor_1.0.0_A1B2C3.exe          # Windows executable
TextEditor_1.0.0_A1B2C3.app          # macOS application bundle
TextEditor_1.0.0_A1B2C3.dmg          # macOS disk image
TextEditor_1.2.3-beta.1_A1B2C3.jar   # Cross-platform JAR
```

#### GitHub Packages

```
MyApp@main_XYZ789.exe                # GitHub main branch
MyApp@develop_XYZ789.app             # GitHub develop branch
MyApp@v1.0.0_ABC123.dmg              # GitHub tag v1.0.0
```

**Version Conversion:**
- `@main` → `0.0.0-main`
- `@v1.0.0` → `0.0.0-v1.0.0`

### Bundle Code Purpose

The bundle code is a unique identifier that maps to:
- **Package name**: `@myorg/myapp`
- **Source**: NPM or GitHub repository URL

**Registry Lookup:**
```
GET https://www.jdeploy.com/registry/{BUNDLECODE}
```

**Response:**
```json
{
  "packageName": "@myorg/myapp",
  "projectSource": "https://github.com/myorg/myapp"
}
```

This allows a single installer executable to be distributed without embedding package metadata, reducing file size and enabling dynamic updates.

## Examples

### Example 1: Standard NPM Package Installation

**Scenario:** User downloads `TextEditor_1.0.0_A1B2C3.exe` from website

**Resolution Flow:**

1. **Launcher starts:** Sets `client4j.launcher.path=C:\Users\Me\Downloads\TextEditor_1.0.0_A1B2C3.exe`

2. **Extract bundle code:**
   - Filename: `TextEditor_1.0.0_A1B2C3.exe`
   - Code: `A1B2C3`
   - Version: `1.0.0`

3. **Download from registry:**
   ```
   GET https://www.jdeploy.com/download?code=A1B2C3&version=1.0.0&jdeploy_files=true
   ```
   - Downloads ZIP containing `.jdeploy-files/`
   - Extracts to `%TEMP%\jdeploy-files-download-{random}\`

4. **Load app.xml:**
   ```xml
   <app package="text-editor" title="Text Editor" version="1.0.0"/>
   ```

5. **Query NPM registry:**
   ```
   GET https://registry.npmjs.org/text-editor
   ```
   - Resolves version `1.0.0`
   - Downloads `package.json` metadata

6. **Install application:**
   - Creates `%LOCALAPPDATA%\Text Editor\`
   - Installs application files
   - Creates shortcuts

### Example 2: GitHub Branch Installation

**Scenario:** Developer testing `main` branch

**Resolution Flow:**

1. **Launcher:** `MyApp@main_XYZ789.dmg`

2. **Extract:**
   - Code: `XYZ789`
   - Version: `0.0.0-main` (converted from `@main`)

3. **Registry lookup:**
   ```
   GET https://www.jdeploy.com/registry/XYZ789
   ```
   Response:
   ```json
   {
     "packageName": "myapp",
     "projectSource": "https://github.com/myorg/myapp"
   }
   ```

4. **GitHub direct download:**
   ```
   GET https://github.com/myorg/myapp/releases/download/jdeploy-bundle-main/myapp-jdeploy-files.zip
   ```

5. **Load app.xml:**
   ```xml
   <app package="myapp"
        source="https://github.com/myorg/myapp"
        version="0.0.0-main"/>
   ```

6. **Install:**
   - Bundle ID: `ca.weblite.jdeploy.apps.{MD5}.myapp.main`
   - Tracks `main` branch for updates

### Example 3: Development Setup

**Scenario:** Developer running installer from IDE

**Project Structure:**
```
/Users/dev/myproject/
├── .jdeploy-files/
│   ├── app.xml
│   └── icon.png
├── installer/
│   └── target/
│       └── installer.jar
└── src/
```

**Resolution Flow:**

1. **Run from IDE:**
   ```bash
   java -jar /Users/dev/myproject/installer/target/installer.jar
   ```

2. **No bundle code:** Filename is `installer.jar` (no version/code)

3. **Recursive search:**
   - Start: `/Users/dev/myproject/installer/target/`
   - Check: `/Users/dev/myproject/installer/target/.jdeploy-files` ✗
   - Check: `/Users/dev/myproject/installer/.jdeploy-files` ✗
   - Check: `/Users/dev/myproject/.jdeploy-files` ✓

4. **Load app.xml:**
   ```xml
   <app package="myapp" version="latest"/>
   ```

5. **Query NPM:** Resolve `latest` → `1.0.5`

6. **Install locally**

### Example 4: macOS Gatekeeper Translocation

**Scenario:** User opens unsigned `TextEditor.app` on macOS

**Initial Location:**
```
/Users/me/Downloads/TextEditor_1.0.0_A1B2C3.app
```

**After First Launch:**
```
/private/var/folders/zz/zyxvpxvq6csfxvn_n0000000000000/T/AppTranslocation/12345678-ABCD-EFGH-IJKL-123456789ABC/d/TextEditor_1.0.0_A1B2C3.app
```

**Resolution Flow:**

1. **Detect translocation:** `AppTranslocation` in path

2. **Find app bundle:**
   - Scan process info to find original `.app` location
   - Result: `TextEditor_1.0.0_A1B2C3.app`

3. **Extract from bundle name:**
   - Code: `A1B2C3`
   - Version: `1.0.0`

4. **Download from registry:**
   - Can't access original `.jdeploy-files` (in translocated location)
   - Must download fresh copy from network

5. **Complete installation:**
   - Moves app to `~/Applications/`
   - App is no longer translocated on next launch

## Implementation Details

### Key Classes

| Class | Responsibility |
|-------|----------------|
| `Main.java` | Entry point, orchestrates AppInfo loading |
| `DefaultInstallationContext` | Finds `app.xml`, manages installation context |
| `InstallationSettings` | Stores AppInfo and configuration |
| `NPMRegistry` | Queries NPM/GitHub registries |
| `NPMPackageVersion` | Represents package.json metadata |
| `BundleRegistryCache` | Caches bundle code → package mappings |

### System Properties

| Property | Purpose | Example |
|----------|---------|---------|
| `client4j.appxml.path` | Direct path to app.xml | `/path/to/app.xml` |
| `client4j.launcher.path` | Path to launcher executable | `C:\...\MyApp.exe` |
| `jdeploy.bundle-code` | Override bundle code | `ABC123` |
| `jdeploy.bundle-version` | Override version | `1.0.0` |
| `JDEPLOY_REGISTRY_FALLBACK` | Fallback registry URLs | `https://alt.jdeploy.com` |

### Cache Locations

| Cache | Location | Purpose |
|-------|----------|---------|
| Bundle registry | `~/.jdeploy/registry-cache/` | Maps bundle codes to package names |
| Downloaded bundles | System temp directory | Temporary `.jdeploy-files` storage |

## Error Handling

### Common Failure Modes

#### 1. `app.xml` Not Found

**Error:**
```
Cannot load app info because the app.xml file could not be found
```

**Causes:**
- No `.jdeploy-files` directory in path hierarchy
- Bundle code invalid or not in registry
- Network error downloading bundle

**Resolution:**
- Check installer filename format
- Verify bundle code is registered
- Check network connectivity
- Use `-Dclient4j.appxml.path` override

#### 2. Invalid `app.xml` Format

**Error:**
```
Missing package attribute
```

**Cause:**
`<app>` element missing required `package` attribute

**Resolution:**
Ensure `app.xml` contains:
```xml
<app package="your-package-name" ... />
```

#### 3. Version Not Found

**Error:**
```
Cannot find version X.Y.Z for package myapp
```

**Causes:**
- Version doesn't exist in registry
- Network error querying registry
- Private package without authentication

**Resolution:**
- Verify version exists: `npm view myapp versions`
- Check network connectivity
- For private packages, configure authentication

#### 4. Bundle Download Failure

**Error:**
```
Failed to download install files bundle
```

**Causes:**
- jDeploy registry unavailable
- Bundle code not found (404)
- Network connectivity issues

**Resolution:**
- Check https://www.jdeploy.com/ availability
- Verify bundle code in registry
- Configure fallback registries

## Future Considerations

### Potential Enhancements

1. **Multiple Registry Support**
   - Already partially implemented with fallback registries
   - Could add primary/fallback configuration in `app.xml`

2. **Offline Mode**
   - Cache complete `.jdeploy-files` bundles locally
   - Skip registry queries if cached bundle matches version

3. **Custom Registry URLs**
   - Allow `app.xml` to specify registry URL
   - Support private/enterprise registries

4. **Bundle Verification**
   - Cryptographic signing of downloaded bundles
   - Verify bundle integrity before extraction

5. **Lazy Loading**
   - Download only `app.xml` initially
   - Fetch additional resources (icons, etc.) on demand

## References

- **Implementation:** `installer/src/main/java/ca/weblite/jdeploy/installer/Main.java`
- **Context:** `installer/src/main/java/ca/weblite/jdeploy/installer/DefaultInstallationContext.java`
- **Registry Client:** `installer/src/main/java/ca/weblite/jdeploy/installer/npm/NPMRegistry.java`
- **App Model:** `shared/src/main/java/ca/weblite/jdeploy/app/AppInfo.java`

## Change Log

| Date | Change | Author |
|------|--------|--------|
| 2026-01-17 | Initial documentation of existing implementation | Analysis |

---

**Note:** This RFC documents the existing implementation for reference purposes. It is not a proposal for new functionality, but rather a comprehensive guide to understanding the current AppInfo resolution mechanism.
