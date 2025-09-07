# RFC: Platform-Specific Bundles Implementation

## Overview

This RFC outlines the implementation plan for adding platform-specific bundle support to jDeploy, enabling smaller download sizes and faster startup times by distributing optimized bundles for each platform architecture.

## Background

Currently, jDeploy creates universal bundles that include native libraries for all supported platforms. This results in larger download sizes and slower startup times. The launcher has been updated to support platform-specific bundles as described in [GitHub Issue #244](https://github.com/shannah/jdeploy/issues/244).

## Configuration Structure

The launcher expects the following configuration properties in `package.json`:

```json
{
  "name": "my-app",
  "version": "1.0.0",
  "jdeploy": {
    "jar": "target/myapp.jar",
    "javaVersion": "11",
    "platformBundlesEnabled": true,
    "fallbackToUniversal": false,
    "packageMacX64": "my-app-macos-intel",
    "packageMacArm64": "my-app-macos-silicon", 
    "packageWinX64": "my-app-windows-x64",
    "packageWinArm64": "my-app-windows-arm64",
    "packageLinuxX64": "my-app-linux-x64",
    "packageLinuxArm64": "my-app-linux-arm64",
    "nativeNamespaces": {
      "mac-x64": [
        "ca.weblite.native.mac.x64",
        "com.thirdparty.foo.bar.native.mac.x64"
      ],
      "mac-arm64": [
        "ca.weblite.native.mac.arm64",
        "com.thirdparty.foo.bar.native.mac.arm64"
      ],
      "win-x64": [
        "ca.weblite.native.win.x64",
        "com.thirdparty.foo.bar.native.win.x64"
      ],
      "win-arm64": [
        "ca.weblite.native.win.arm64"
      ],
      "linux-x64": [
        "ca.weblite.native.linux.x64"
      ],
      "linux-arm64": [
        "ca.weblite.native.linux.arm64"
      ]
    }
  }
}
```

### Configuration Properties

- **`platformBundlesEnabled`** - Boolean flag to enable platform-specific bundle generation
- **`fallbackToUniversal`** - Boolean flag allowing fallback to universal bundle if platform-specific version unavailable
- **`package{Platform}{Arch}`** - NPM package names for each platform-specific bundle
- **`nativeNamespaces`** - Object mapping platform identifiers to arrays of Java namespaces containing native libraries

## Implementation Plan

### Phase 1: Data Model Extensions

#### 1.1 JDeployProject Model Updates
Extend the `JDeployProject` class to support new configuration properties:

```java
// New methods to add:
public boolean isPlatformBundlesEnabled()
public boolean isFallbackToUniversal()
public String getPackageName(Platform platform)
public Map<Platform, List<String>> getNativeNamespaces()
public List<String> getNativeNamespacesForPlatform(Platform platform)
public List<String> getAllOtherPlatformNamespaces(Platform targetPlatform)
```

#### 1.2 Platform Enumeration
Create a `Platform` enum with supported platform-architecture combinations:
- `MAC_X64` ("mac-x64")
- `MAC_ARM64` ("mac-arm64") 
- `WIN_X64` ("win-x64")
- `WIN_ARM64` ("win-arm64")
- `LINUX_X64` ("linux-x64")
- `LINUX_ARM64` ("linux-arm64")

### Phase 2: Bundle Generation Infrastructure

#### 2.1 JAR Processing Service
Create `PlatformSpecificJarProcessor` service for JAR manipulation:

```java
public class PlatformSpecificJarProcessor {
    public void stripNativeNamespaces(File jarFile, List<String> namespacesToStrip)
    public List<String> scanJarForNativeNamespaces(File jarFile) 
    public File createPlatformSpecificJar(File originalJar, Platform targetPlatform, List<String> namespacesToStrip)
}
```

**JAR Stripping Algorithm:**
1. Open original JAR file
2. Create new JAR with same manifest
3. For each entry in original JAR:
   - Convert namespace to path format (e.g., `ca.weblite.native.mac.x64` → `ca/weblite/native/mac/x64/`)
   - If entry path starts with any namespace in `namespacesToStrip`, skip it
   - Otherwise, copy entry to new JAR
4. Close and replace original JAR with stripped version

#### 2.2 Bundle Generation Process
For each platform-specific bundle:
1. Copy universal bundle to platform-specific directory
2. Get all native namespaces for other platforms (exclude target platform)
3. Process each JAR in the bundle:
   - Strip out classes/resources matching other platforms' namespaces
   - Update JAR with stripped content
4. Create platform-specific tarball with naming pattern: `{appname}-{version}-{platform}.tgz`

### Phase 3: GUI Updates

#### 3.1 Project Editor Enhancements
Add new "Platform Bundles" tab to the project editor containing:

**Platform Bundle Configuration:**
- Checkbox: "Enable Platform-Specific Bundles" (`platformBundlesEnabled`)
- Checkbox: "Fallback to Universal Bundle" (`fallbackToUniversal`)

**Platform Package Names:**
- Text fields for each platform package name:
  - macOS Intel (`packageMacX64`)
  - macOS Silicon (`packageMacArm64`)
  - Windows x64 (`packageWinX64`)
  - Windows ARM64 (`packageWinArm64`)
  - Linux x64 (`packageLinuxX64`)
  - Linux ARM64 (`packageLinuxArm64`)

**Native Namespaces Configuration:**
- Expandable tree/table for each platform
- Add/remove namespace entries for each platform
- "Scan JARs" button to auto-detect native namespaces in project JARs
- Validation to ensure proper Java package naming format

### Phase 4: Publishing Infrastructure

#### 4.1 NPM Publishing Updates
Modify `NPMPublishDriver` to handle multiple package publishing:

When `platformBundlesEnabled` is true:
1. Publish universal bundle to main package name
2. For each platform with a configured package name:
   - Generate platform-specific bundle (stripped of other platforms' native libs)
   - Create temporary `package.json` with platform-specific name
   - Publish to the specified platform package name
   - Handle 2FA authentication for each publish operation

#### 4.2 GitHub Publishing Updates
Modify `GitHubPublishDriver` to handle multiple tarballs:

When `platformBundlesEnabled` is true:
1. Generate universal tarball (maintain current behavior)
2. Generate platform-specific tarballs with naming pattern:
   - `{appname}-{version}-mac-x64.tgz`
   - `{appname}-{version}-mac-arm64.tgz`
   - `{appname}-{version}-win-x64.tgz`
   - etc.
3. Upload all tarballs to the same GitHub release

### Phase 5: CLI Integration

#### 5.1 Command Line Support
Update existing CLI commands to support platform bundle operations:
- Ensure `jdeploy publish` command works with platform bundles
- Add validation for platform bundle configuration
- Provide feedback during platform-specific bundle generation

#### 5.2 Integration Tests
Add comprehensive tests covering:
- Platform-specific bundle generation
- JAR processing and namespace stripping
- NPM publishing to multiple packages
- GitHub release creation with multiple tarballs
- Configuration validation and error handling

## Benefits

### Size Optimization
- **Reduced Download Sizes**: Platform-specific bundles contain only necessary native libraries
- **Faster Startup**: Fewer JARs to process and load
- **Bandwidth Savings**: Users only download what they need for their platform

### Flexibility
- **Precise Control**: Developers can specify exactly which namespaces to strip
- **Safety**: Only explicitly configured namespaces are removed
- **Extensibility**: Easy to add support for new platforms and architectures

### Backward Compatibility
- **Universal Bundle Fallback**: Maintains support for universal bundles
- **Graceful Degradation**: Launcher falls back to universal bundle if platform-specific version unavailable
- **Existing Workflow Compatibility**: No changes required for projects not using platform bundles

## Example Namespace to Path Conversion

- **Namespace**: `ca.weblite.native.mac.x64`
- **JAR Path**: `ca/weblite/native/mac/x64/`
- **Effect**: Strips all classes and resources under this path from non-macOS-x64 bundles

## Implementation Timeline

1. **Phase 1**: Data model extensions (1-2 days)
2. **Phase 2**: Bundle generation infrastructure (3-4 days)
3. **Phase 3**: GUI updates (2-3 days)
4. **Phase 4**: Publishing infrastructure (3-4 days)
5. **Phase 5**: CLI integration and testing (2-3 days)

**Total Estimated Time**: 11-16 days

## Risks and Mitigations

### Risk: JAR Processing Errors
- **Mitigation**: Comprehensive testing with various JAR structures
- **Fallback**: Maintain original JAR if processing fails

### Risk: Publishing Failures
- **Mitigation**: Robust error handling and retry logic
- **Rollback**: Ability to publish universal bundle if platform-specific publishing fails

### Risk: Configuration Complexity
- **Mitigation**: Good defaults, auto-detection features, and clear validation messages
- **Documentation**: Comprehensive examples and best practices guide

## Conclusion

This implementation provides a comprehensive solution for platform-specific bundles that:
- Integrates seamlessly with the existing launcher
- Provides significant size and performance benefits
- Maintains backward compatibility
- Offers flexible configuration options
- Supports the existing publishing workflows for both NPM and GitHub

The modular design allows for incremental implementation and future extensibility as new platforms and requirements emerge.