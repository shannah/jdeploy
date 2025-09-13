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
    "packageLinuxArm64": "my-app-linux-arm64"
  }
}
```

### .jdpignore Files

Native library filtering rules are now stored in `.jdpignore` files in the project directory, similar to `.gitignore` files:

**`.jdpignore`** (Global ignore/keep rules):
```
# Ignore namespaces/paths (exclude from all platform bundles)
com.obsolete.legacy.native
com.testing.mocklibs.native  
com.development.debugging.native

# Keep namespaces/paths (include in all platform bundles even if parent is ignored)
!com.testing.mocklibs.native.required
```

**`.jdpignore.mac-x64`** (Platform-specific rules for macOS x64):
```
# Keep these namespaces for macOS x64 bundles
!ca.weblite.native.mac.x64
!com.thirdparty.foo.bar.native.mac.x64
!/my-native-lib-macos.dylib
!/native/macos/
```

**`.jdpignore.win-x64`** (Platform-specific rules for Windows x64):
```
# Keep these namespaces for Windows x64 bundles  
!ca.weblite.native.win.x64
!com.thirdparty.foo.bar.native.win.x64
!/my-native-lib.dll
!/native/windows/
```

### Configuration Properties

- **`platformBundlesEnabled`** - Boolean flag to enable platform-specific bundle generation
- **`fallbackToUniversal`** - Boolean flag allowing fallback to universal bundle if platform-specific version unavailable
- **`package{Platform}{Arch}`** - NPM package names for each platform-specific bundle

### .jdpignore File Rules

- **`.jdpignore`** - Global ignore/keep rules applied to all platform bundles
- **`.jdpignore.{platform-arch}`** - Platform-specific rules (e.g., `.jdpignore.mac-x64`, `.jdpignore.win-x64`)
- **Rule Syntax**:
  - Lines without `!` prefix: Namespaces/paths to ignore (exclude from bundle)
  - Lines with `!` prefix: Namespaces/paths to keep (include in bundle, overrides ignore rules)
  - Comments start with `#`
  - Empty lines are ignored

### Supported Platforms

- `mac-x64` - macOS Intel
- `mac-arm64` - macOS Apple Silicon  
- `win-x64` - Windows x64
- `win-arm64` - Windows ARM64
- `linux-x64` - Linux x64
- `linux-arm64` - Linux ARM64

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

### Rule Resolution Behavior

When there are overlapping rules between global `.jdpignore` and platform-specific `.jdpignore.{platform}` files, the following resolution rules apply:

#### Rule 1: Global Ignore Rules Apply to All Platforms
Namespaces listed without `!` prefix in `.jdpignore` are ignored in **all** platform bundles, including the universal bundle.

#### Rule 2: Keep Rules Override Ignore Rules
Namespaces with `!` prefix (keep rules) override ignore rules, both within the same file and across global/platform files.

#### Rule 3: Platform-Specific Rules Extend Global Rules
Platform-specific `.jdpignore.{platform}` files extend (not replace) the global `.jdpignore` rules.

#### Example Scenario

**`.jdpignore`** (Global rules):
```
# Ignore all native libraries by default
com.myapp.native
```

**`.jdpignore.mac-x64`** (macOS x64 specific rules):
```
# Keep macOS x64 libraries for this platform
!com.myapp.native.mac.x64
```

**Behavior:**
- **Universal Bundle**: All content under `com.myapp.native` is stripped
- **mac-x64 Bundle**: Content under `com.myapp.native.mac.x64` is **included**, but all other content under `com.myapp.native` is still stripped
- **Other Platform Bundles**: All content under `com.myapp.native` is stripped

#### File Processing Example
For mac-x64 bundle generation with the above configuration:

**Global Ignore List**: `["com.myapp.native"]` (from `.jdpignore`)
**Global Keep List**: `[]` (none in this example)
**Platform Keep List**: `["com.myapp.native.mac.x64"]` (from `.jdpignore.mac-x64`)
**Platform Ignore List**: `[]` (none in this example)

**File Processing Decisions**:
- `com/myapp/native/mac/x64/MacLib.dylib` → **KEEP** (matches platform keep list)
- `com/myapp/native/windows/WinLib.dll` → **IGNORE** (matches global ignore list, not in any keep list)  
- `com/myapp/native/test/TestLib.so` → **IGNORE** (matches global ignore list, not in any keep list)
- `com/myapp/core/AppCore.class` → **KEEP** (doesn't match any ignore pattern)

#### Implementation Logic
1. For each target platform, parse ignore files to create lists:
   - **Global Ignore List**: Rules without `!` from `.jdpignore`
   - **Global Keep List**: Rules with `!` from `.jdpignore` 
   - **Platform Ignore List**: Rules without `!` from `.jdpignore.{platform}`
   - **Platform Keep List**: Rules with `!` from `.jdpignore.{platform}`
2. Process each file in the JAR:
   - If file path matches any pattern in **Global Keep List** or **Platform Keep List**: **KEEP** the file
   - Else if file path matches any pattern in **Global Ignore List** or **Platform Ignore List**: **IGNORE** the file  
   - Else: **KEEP** the file (default behavior)

**Note**: Any keep rule (global or platform) overrides any ignore rule. This allows fine-grained control over which sub-namespaces to preserve.

## Implementation Plan

### Phase 1: Data Model Extensions

#### 1.1 JDeployProject Model Updates
Extend the `JDeployProject` class to support new configuration properties:

```java
// New methods to add:
public boolean isPlatformBundlesEnabled();
public boolean isFallbackToUniversal();
public String getPackageName(Platform platform);

// New methods for .jdpignore file processing:
public List<String> getGlobalIgnorePatterns();
public List<String> getGlobalKeepPatterns();
public List<String> getPlatformIgnorePatterns(Platform platform);
public List<String> getPlatformKeepPatterns(Platform platform);
public List<String> getAllIgnorePatterns(Platform platform);
public List<String> getAllKeepPatterns(Platform platform);

// Helper methods for file resolution:
public boolean shouldKeepFile(String filePath, Platform platform);
public boolean shouldIgnoreFile(String filePath, Platform platform);
```

#### 1.2 JDeployIgnoreFile Service
Create a new service for parsing `.jdpignore` files:

```java
public class JDeployIgnoreFileParser {
    public List<String> parseIgnorePatterns(File ignoreFile);
    public List<String> parseKeepPatterns(File ignoreFile);
    public boolean matchesPattern(String filePath, String pattern);
    public List<String> convertPatternsToJarPaths(List<String> patterns);
}
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
    public void processJarForPlatform(File jarFile, Platform targetPlatform, JDeployProject project);
    public List<String> scanJarForNativeNamespaces(File jarFile); 
    public File createPlatformSpecificJar(File originalJar, Platform targetPlatform, JDeployProject project);
}
```

**JAR Processing Algorithm:**
1. Open original JAR file
2. Create new JAR with same manifest
3. Parse all relevant .jdpignore files:
   - Load global ignore/keep patterns from `.jdpignore`
   - Load platform-specific ignore/keep patterns from `.jdpignore.{platform}`
4. Convert all patterns to path format for matching:
   - **Java package notation**: `ca.weblite.native.mac.x64` → `ca/weblite/native/mac/x64/`
   - **Path-based notation**: `/my-native-lib.dll` → `my-native-lib.dll` (strip leading `/`)
5. For each entry in original JAR:
   - **Check Keep Patterns**: If entry path matches any global or platform keep pattern: **COPY** to new JAR
   - **Check Ignore Patterns**: Else if entry path matches any global or platform ignore pattern: **SKIP** entry
   - **Default**: Else **COPY** to new JAR (preserve by default)
6. Close and replace original JAR with processed version

**Key Principle**: Keep patterns (with `!` prefix) always override ignore patterns for any given file.

#### 2.2 Bundle Generation Process
For each platform-specific bundle:
1. Copy universal bundle to platform-specific directory
2. Parse .jdpignore files:
   - Load global patterns from `.jdpignore`
   - Load platform-specific patterns from `.jdpignore.{platform}`
3. Process each JAR in the bundle:
   - Apply JAR processing using parsed ignore/keep patterns
   - Keep patterns override ignore patterns for any overlapping files
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

**.jdpignore File Management:**
- "Edit .jdpignore" button to open global ignore file in text editor
- Platform-specific ignore file editors for each platform (`.jdpignore.mac-x64`, etc.)
- "Create Platform Ignore Files" button to generate empty platform-specific files
- "Scan JARs" button to auto-detect native namespaces and suggest ignore patterns
- Preview pane showing resolved ignore/keep patterns for each platform
- Validation to ensure proper format:
  - Java package notation: dot-separated identifiers (e.g., `com.example.native`)
  - Path-based notation: forward slash prefix for direct paths (e.g., `/my-lib.dll`)
  - Keep notation: exclamation prefix (e.g., `!com.example.keep`)

### Phase 4: Publishing Infrastructure

#### 4.1 NPM Publishing Updates
Modify `NPMPublishDriver` to handle multiple package publishing:

When `platformBundlesEnabled` is true:
1. Publish universal bundle to main package name
2. For each platform with a configured package name:
   - Process .jdpignore files to determine ignore/keep patterns
   - Generate platform-specific bundle using resolved patterns
   - Create temporary `package.json` with platform-specific name
   - Publish to the specified platform package name
   - Handle 2FA authentication for each publish operation

#### 4.2 GitHub Publishing Updates
Modify `GitHubPublishDriver` to handle multiple tarballs:

When `platformBundlesEnabled` is true:
1. Generate universal tarball (maintain current behavior)
2. For each platform:
   - Process .jdpignore files to determine ignore/keep patterns
   - Generate platform-specific tarballs using resolved patterns
   - Use naming pattern: `{appname}-{version}-{platform}.tgz`
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

## Example .jdpignore Pattern to Path Conversion

### Java Package Notation
- **Pattern**: `ca.weblite.native.mac.x64`
- **JAR Path**: `ca/weblite/native/mac/x64/`
- **Effect**: Ignores all classes and resources under this path

### Path-based Notation
- **Pattern**: `/my-native-lib.dll`
- **JAR Path**: `my-native-lib.dll`
- **Effect**: Ignores the specific native library file

- **Pattern**: `/native/windows/`
- **JAR Path**: `native/windows/`
- **Effect**: Ignores all files under the native/windows/ directory

### Keep Notation (Override Ignore)
- **Pattern**: `!ca.weblite.native.mac.x64`
- **JAR Path**: `ca/weblite/native/mac/x64/`
- **Effect**: Keeps all classes and resources under this path, even if parent namespace is ignored

## File Resolution Logic

The new file resolution logic for determining whether a file should be included in a platform bundle:

1. **Load Patterns**: Parse global `.jdpignore` and platform-specific `.jdpignore.{platform}` files
2. **Resolve Patterns**: Combine all ignore and keep patterns for the target platform
3. **File Decision**: For each file in the JAR:
   - If file matches any **keep pattern** (global or platform): **INCLUDE** the file
   - Else if file matches any **ignore pattern** (global or platform): **EXCLUDE** the file
   - Else: **INCLUDE** the file (default behavior)

**Key Principle**: Keep patterns always override ignore patterns, allowing fine-grained control over sub-namespaces.

## Implementation Timeline

1. **Phase 1**: Data model extensions and .jdpignore parsing (2-3 days)
2. **Phase 2**: Bundle generation infrastructure with file pattern matching (3-4 days)
3. **Phase 3**: GUI updates for .jdpignore file management (2-3 days)
4. **Phase 4**: Publishing infrastructure updates (3-4 days)
5. **Phase 5**: CLI integration and testing (2-3 days)

**Total Estimated Time**: 12-17 days

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