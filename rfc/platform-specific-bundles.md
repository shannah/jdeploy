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
      "ignore": [
        "com.obsolete.legacy.native",
        "com.testing.mocklibs.native",
        "com.development.debugging.native"
      ],
      "mac-x64": [
        "ca.weblite.native.mac.x64",
        "com.thirdparty.foo.bar.native.mac.x64",
        "/my-native-lib-macos.dylib",
        "/native/macos/"
      ],
      "mac-arm64": [
        "ca.weblite.native.mac.arm64",
        "com.thirdparty.foo.bar.native.mac.arm64"
      ],
      "win-x64": [
        "ca.weblite.native.win.x64",
        "com.thirdparty.foo.bar.native.win.x64",
        "/my-native-lib.dll",
        "/native/windows/"
      ],
      "win-arm64": [
        "ca.weblite.native.win.arm64"
      ],
      "linux-x64": [
        "ca.weblite.native.linux.x64",
        "/my-native-lib.so",
        "/native/linux/"
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
  - **`ignore`** - Array of namespaces to be stripped from all platform bundles (e.g., testing, debugging, or obsolete libraries)

### Native Namespace Format

Native namespaces support two formats to handle different packaging scenarios:

#### 1. Java Package Notation (Default)
Standard Java package notation using dots (e.g., `ca.weblite.native.mac.x64`):
- Converted to JAR path format by replacing dots with forward slashes
- Example: `ca.weblite.native.mac.x64` → `ca/weblite/native/mac/x64/`
- Used for organized native libraries following Java package conventions

#### 2. Path-based Notation (Forward Slash Prefix)
Direct JAR path notation prefixed with `/` (e.g., `/my-native-lib.dll`, `/native/windows/`):
- Used as literal paths within the JAR file structure
- Enables targeting of native libraries in root namespace or custom directory structures
- Examples:
  - `/my-native-lib.dll` - Targets specific file in JAR root
  - `/native/windows/` - Targets all files under the native/windows/ directory
  - `/lib/` - Targets all files under the lib/ directory

This dual format support allows handling both traditional Java package structures and native libraries placed in root or custom paths within JAR files.

### Namespace Overlap Behavior

When there is overlap between namespaces listed in the `ignore` section and platform-specific `nativeNamespaces`, the following resolution rules apply:

#### Rule 1: Ignore Takes Precedence for General Stripping
Namespaces listed in `ignore` are stripped from **all** platform bundles, including the default universal bundle.

#### Rule 2: Platform-Specific Inclusion Overrides Ignore
If a platform-specific namespace list explicitly includes a namespace or sub-namespace that would otherwise be ignored, that specific namespace **will be included** in that platform's bundle.

#### Example Scenario
```json
{
  "nativeNamespaces": {
    "ignore": [
      "com.myapp.native"
    ],
    "mac-x64": [
      "com.myapp.native.mac.x64"
    ]
  }
}
```

**Behavior:**
- **Default/Universal Bundle**: All content under `com.myapp.native` is stripped
- **mac-x64 Bundle**: Content under `com.myapp.native.mac.x64` is **included**, but all other content under `com.myapp.native` (like `com.myapp.native.windows` or `com.myapp.native.test`) is still stripped
- **Other Platform Bundles**: All content under `com.myapp.native` is stripped (including `com.myapp.native.mac.x64`)

#### File Processing Example
For mac-x64 bundle generation with the above configuration:

**Strip List**: `["com.myapp.native", "ca.weblite.native.win.x64", "ca.weblite.native.linux.x64", ...]`
**Keep List**: `["com.myapp.native.mac.x64"]`

**File Processing Decisions**:
- `com/myapp/native/mac/x64/MacLib.dylib` → **KEEP** (matches keep list)
- `com/myapp/native/windows/WinLib.dll` → **STRIP** (matches strip list, not in keep list)  
- `com/myapp/native/test/TestLib.so` → **STRIP** (matches strip list, not in keep list)
- `com/myapp/core/AppCore.class` → **KEEP** (doesn't match any strip pattern)

#### Implementation Logic
1. For each target platform, create two lists:
   - **Strip List**: All namespaces to be removed
     - All `ignore` namespaces (always stripped)
     - All native namespaces from other platforms (not the target platform)
   - **Keep List**: Namespaces to preserve even if they match the strip list
     - All native namespaces explicitly listed for the target platform
2. Process each file in the JAR:
   - If the file path matches any pattern in the **Keep List**: **KEEP** the file
   - Else if the file path matches any pattern in the **Strip List**: **REMOVE** the file  
   - Else: **KEEP** the file (default behavior)

**Note**: Keep list takes precedence over strip list. This allows platform-specific sub-namespaces to be preserved even when their parent namespace is in the ignore list.

## Implementation Plan

### Phase 1: Data Model Extensions

#### 1.1 JDeployProject Model Updates
Extend the `JDeployProject` class to support new configuration properties:

```java
// New methods to add:
public boolean isPlatformBundlesEnabled();
public boolean isFallbackToUniversal();
public String getPackageName(Platform platform);
public Map<Platform, List<String>> getNativeNamespaces();
public List<String> getNativeNamespacesForPlatform(Platform platform);
public List<String> getIgnoredNamespaces();
public List<String> getAllOtherPlatformNamespaces(Platform targetPlatform);

// Helper methods for the new strip/keep list approach:
public List<String> getNamespacesToStrip(Platform targetPlatform);
public List<String> getNamespacesToKeep(Platform targetPlatform);
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
    public void processJarForPlatform(File jarFile, List<String> namespacesToStrip, List<String> namespacesToKeep);
    public List<String> scanJarForNativeNamespaces(File jarFile); 
    public File createPlatformSpecificJar(File originalJar, Platform targetPlatform, 
                                         List<String> namespacesToStrip, List<String> namespacesToKeep);
}
```

**JAR Stripping Algorithm:**
1. Open original JAR file
2. Create new JAR with same manifest
3. Convert all namespaces to path format for matching:
   - **Java package notation**: `ca.weblite.native.mac.x64` → `ca/weblite/native/mac/x64/`
   - **Path-based notation**: `/my-native-lib.dll` → `my-native-lib.dll` (strip leading `/`)
4. For each entry in original JAR:
   - **Check Keep List First**: If entry path starts with any pattern in `namespacesToKeep`: **COPY** to new JAR
   - **Check Strip List**: Else if entry path starts with any pattern in `namespacesToStrip`: **SKIP** entry
   - **Default**: Else **COPY** to new JAR (preserve by default)
5. Close and replace original JAR with processed version

**Key Principle**: Keep list always overrides strip list for any given file.

#### 2.2 Bundle Generation Process
For each platform-specific bundle:
1. Copy universal bundle to platform-specific directory
2. Prepare namespace filtering lists:
   - **Strip List**: 
     - All `ignore` namespaces (always stripped)
     - All native namespaces from other platforms (exclude target platform)
   - **Keep List**:
     - All native namespaces explicitly listed for the target platform
3. Process each JAR in the bundle:
   - Apply JAR processing with both strip and keep lists
   - Keep list overrides strip list for any overlapping files
   - Update JAR with processed content
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
- "Ignore" section for namespaces to strip from all platform bundles
- Expandable tree/table for each platform-specific namespace list
- Add/remove namespace entries for each platform and ignore list
- "Scan JARs" button to auto-detect native namespaces in project JARs
- Validation to ensure proper format:
  - Java package notation: dot-separated identifiers (e.g., `com.example.native`)
  - Path-based notation: forward slash prefix for direct paths (e.g., `/my-lib.dll`)

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

### Java Package Notation
- **Namespace**: `ca.weblite.native.mac.x64`
- **JAR Path**: `ca/weblite/native/mac/x64/`
- **Effect**: Strips all classes and resources under this path from non-macOS-x64 bundles

### Path-based Notation
- **Namespace**: `/my-native-lib.dll`
- **JAR Path**: `my-native-lib.dll`
- **Effect**: Strips the specific native library file from non-Windows bundles

- **Namespace**: `/native/windows/`
- **JAR Path**: `native/windows/`
- **Effect**: Strips all files under the native/windows/ directory from non-Windows bundles

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