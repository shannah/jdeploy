# Client4JLauncher Coordination for External Preferences Support

## Overview

This document provides specifications for the Client4JLauncher team to implement support for external preferences files as part of the Prebuilt Apps Publishing feature.

## Background

jDeploy is adding support for prebuilt application bundles that are code-signed and notarized. These bundles cannot be modified after installation, so user preferences (such as auto-update settings, version pinning, and JVM arguments) need to be stored externally.

## Current Behavior

Currently, the launcher may read settings directly from the installed app bundle or use hardcoded defaults.

**Problem:** Prebuilt signed apps cannot store user-specific settings inside the bundle without breaking the code signature. User preferences need to be stored externally where they can be modified without affecting the app bundle.

## New Behavior Required

### External Preferences Location

Preferences are stored at:
```
~/.jdeploy/preferences/{fqn}/preferences.xml
```

Where `{fqn}` is the fully-qualified package name.

### FQN Calculation

The fully-qualified name (FQN) is calculated as follows:

**When source URL is provided (GitHub packages):**
```
{md5-hash}.{package-name}
```

Where `{md5-hash}` is the MD5 hash of the source URL string.

**When source URL is NOT provided (NPM packages):**
```
{package-name}
```

### Example FQNs

| Package Name | Source URL | FQN |
|--------------|------------|-----|
| my-app | null | my-app |
| my-app | "" | my-app |
| my-app | https://github.com/user/repo | abc123...def.my-app |
| my-app | https://github.com/other/repo | xyz789...ghi.my-app |

### Preferences XML Schema

```xml
<?xml version="1.0" encoding="UTF-8"?>
<preferences>
    <schemaVersion>1.0</schemaVersion>
    <version>1.2.3</version>
    <prereleaseChannel>stable</prereleaseChannel>
    <autoUpdate>minor</autoUpdate>
    <jvmArgs>-Xmx512m</jvmArgs>
    <prebuiltInstallation>true</prebuiltInstallation>
</preferences>
```

### Field Definitions

| Field | Required | Default | Description |
|-------|----------|---------|-------------|
| schemaVersion | Yes | "1.0" | Schema version for forward/backward compatibility |
| version | No | null | The installed app version, used for version tracking |
| prereleaseChannel | Yes | "stable" | Values: "stable" or "prerelease" |
| autoUpdate | Yes | "minor" | Values: "all", "minor", "patch", "none" |
| jvmArgs | No | null | Additional JVM arguments to pass when launching |
| prebuiltInstallation | Yes | "false" | Whether this is a prebuilt app installation |

### Auto-Update Values Explained

| Value | Meaning |
|-------|---------|
| all | Update to any newer version (including major) |
| minor | Update within same major version (1.x.x → 1.y.y) |
| patch | Update within same minor version (1.2.x → 1.2.y) |
| none | No automatic updates |

### Prerelease Channel Values Explained

| Value | Meaning |
|-------|---------|
| stable | Only consider stable releases |
| prerelease | Include pre-release versions (alpha, beta, rc, etc.) |

## Implementation Checklist for Client4JLauncher

- [ ] Add FQN calculation method
  - [ ] Implement MD5 hash of source URL
  - [ ] Create helper method: `String calculateFqn(String packageName, String source)`
  - [ ] Handle null/empty source (return just package name)

- [ ] Add preferences file path resolution
  - [ ] Create helper method: `File getPreferencesFile(String fqn)`
  - [ ] Path: `{user.home}/.jdeploy/preferences/{fqn}/preferences.xml`

- [ ] Implement preferences reading
  - [ ] Parse XML using standard XML parser
  - [ ] Handle missing file gracefully (use defaults)
  - [ ] Handle corrupted XML gracefully (log warning, use defaults)
  - [ ] Create model class/struct to hold preferences

- [ ] Integrate preferences into launch logic
  - [ ] Check if preferences file exists before launch
  - [ ] Load preferences if file exists
  - [ ] Apply JVM args from preferences (if set)
  - [ ] Use autoUpdate setting when checking for updates
  - [ ] Use prereleaseChannel when checking for updates
  - [ ] Skip self-rebuild if prebuiltInstallation is true

- [ ] Add logging
  - [ ] Log when preferences file is found
  - [ ] Log when preferences file is missing
  - [ ] Log when using default values
  - [ ] Log when corrupted preferences file is encountered

## Preferences Lookup Flow

```
1. Get package name and source URL
   ↓
2. Calculate FQN:
   - If source is null/empty → FQN = packageName
   - If source provided → FQN = MD5(source) + "." + packageName
   ↓
3. Construct preferences path:
   - path = ~/.jdeploy/preferences/{fqn}/preferences.xml
   ↓
4. Check if file exists:
   - If NO → use default preferences
   - If YES → parse XML
   ↓
5. Validate parsed preferences:
   - If parsing fails → log warning, use defaults
   - If parsing succeeds → use loaded preferences
   ↓
6. Apply preferences to launch:
   - Add jvmArgs (if set) to JVM options
   - Use autoUpdate setting for update checks
   - Use prereleaseChannel for version selection
   - Skip rebuild if prebuiltInstallation is true
```

## Reference Implementation (Java)

The jDeploy installer module has a reference implementation:

### FQN Calculation

File: `installer/src/main/java/ca/weblite/jdeploy/installer/preferences/PreferencesService.java`

```java
public String calculateFqn(String packageName, String source) {
    if (source != null && !source.isEmpty()) {
        String sourceHash = MD5.getMd5(source);
        return sourceHash + "." + packageName;
    }
    return packageName;
}
```

### Path Resolution

```java
public File getPreferencesDir(String fqn) {
    return new File(
        System.getProperty("user.home"),
        ".jdeploy/preferences/" + fqn
    );
}

public File getPreferencesFile(String fqn) {
    return new File(getPreferencesDir(fqn), "preferences.xml");
}
```

### Preferences Model

File: `installer/src/main/java/ca/weblite/jdeploy/installer/preferences/AppPreferences.java`

Key fields:
- `String schemaVersion` (default: "1.0")
- `String version` (default: null)
- `String prereleaseChannel` (default: "stable")
- `String autoUpdate` (default: "minor")
- `String jvmArgs` (default: null)
- `boolean prebuiltInstallation` (default: false)

## Testing Requirements

### Test Cases

| Test Case | Steps | Expected Result |
|-----------|-------|-----------------|
| No preferences file | Launch app with no preferences.xml | Uses default values |
| Valid preferences file | Create valid preferences.xml | Loads all values correctly |
| Corrupted preferences | Create invalid XML | Logs warning, uses defaults |
| Empty source | Package with no source URL | FQN is just package name |
| GitHub source | Package with GitHub source | FQN is hash.packageName |
| JVM args applied | Set jvmArgs in preferences | JVM is launched with extra args |
| Prebuilt installation | Set prebuiltInstallation=true | Skip self-rebuild |

### Manual Test Procedure

1. **Fresh Installation (NPM package)**
   - Install app without source URL
   - Verify preferences.xml created at `~/.jdeploy/preferences/{packageName}/preferences.xml`
   - Verify launcher can read preferences

2. **Fresh Installation (GitHub package)**
   - Install app with source URL
   - Verify preferences.xml created at `~/.jdeploy/preferences/{hash}.{packageName}/preferences.xml`
   - Verify FQN matches expected hash

3. **Prebuilt App Behavior**
   - Install prebuilt app (prebuiltInstallation=true)
   - Modify source files in bundle
   - Launch app
   - Verify launcher does NOT rebuild from source

4. **JVM Arguments**
   - Set jvmArgs to `-Xmx1g -Dfoo=bar` in preferences.xml
   - Launch app
   - Verify JVM arguments are applied (check process args)

5. **Auto-Update Behavior**
   - Set autoUpdate to different values
   - Verify update check logic respects setting

## Backward Compatibility

- **Critical:** Apps without preferences files must continue to work with defaults
- New installations will create preferences files
- Legacy installations will continue to work; preferences are optional

## Coordination with jDeploy Installer

The installer creates preferences files during installation:
1. Installer calculates FQN
2. Installer creates preferences directory
3. Installer writes initial preferences.xml
4. Launcher reads preferences on each launch

## Questions or Issues?

Please coordinate with the jDeploy team via:
- GitHub Issue: (link to relevant issue)
- Include Client4JLauncher team in discussions

## Timeline

This feature is being implemented as part of Phase 4 (Launcher Updates) of the Prebuilt Apps Publishing feature.

**Implementation order:**
1. Installer writes preferences (DONE)
2. Launcher reads preferences (TODO)
3. End-to-end testing
4. Coordinate release
