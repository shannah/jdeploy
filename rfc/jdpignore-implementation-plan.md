# Implementation Plan for .jdpignore File Support

## Overview
This document outlines the implementation plan for migrating from `nativeNamespaces` in package.json to `.jdpignore` files for managing platform-specific native library filtering.

## Phase 1: Core Infrastructure (Priority: High)

### 1.1 Create JDeployIgnoreFileParser Service
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/services/JDeployIgnoreFileParser.java`

**Responsibilities**:
- Parse `.jdpignore` and platform-specific `.jdpignore.{platform}` files
- Support comment lines (`#`) and empty lines
- Parse keep patterns (lines with `!` prefix) and ignore patterns (lines without `!`)
- Convert patterns to JAR path format for matching
- Provide pattern matching logic

**Key Methods**:
```java
public class JDeployIgnoreFileParser {
    public List<JDeployIgnorePattern> parseFile(File ignoreFile);
    public List<JDeployIgnorePattern> parseIgnorePatterns(File ignoreFile);
    public List<JDeployIgnorePattern> parseKeepPatterns(File ignoreFile);
    public boolean matchesPattern(String filePath, String pattern);
    public String convertPatternToJarPath(String pattern);
}
```

### 1.2 Create JDeployIgnorePattern Model
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/models/JDeployIgnorePattern.java`

**Structure**:
```java
public class JDeployIgnorePattern {
    private String pattern;      // Original pattern string
    private boolean isKeep;       // Whether this is a keep pattern (! prefix)
    private String jarPath;       // Converted path for JAR matching
    
    public boolean matches(String filePath);
    public boolean isKeepPattern();
    public boolean isIgnorePattern();
}
```

## Phase 2: Create JDeploy Ignore Service (Priority: High)

### 2.1 Create JDeployIgnoreService 
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/services/JDeployIgnoreService.java`

**Responsibilities**:
- Bridge between JDeployProject (shared) and ignore file functionality (CLI)
- Load .jdpignore files from project directory
- Provide file filtering logic for platform bundles
- Cache parsed ignore patterns for performance

**Key Methods**:
```java
public class JDeployIgnoreService {
    public List<JDeployIgnorePattern> getGlobalIgnorePatterns(JDeployProject project);
    public List<JDeployIgnorePattern> getPlatformIgnorePatterns(JDeployProject project, Platform platform);
    public boolean shouldIncludeFile(JDeployProject project, String filePath, Platform platform);
    public File getGlobalIgnoreFile(JDeployProject project);
    public File getPlatformIgnoreFile(JDeployProject project, Platform platform);
}
```

**File Resolution Logic**:
```java
public boolean shouldIncludeFile(JDeployProject project, String filePath, Platform platform) {
    // 1. Load global and platform-specific patterns
    // 2. Check if file matches any keep pattern (global or platform)
    // 3. If yes, return true (include)
    // 4. Check if file matches any ignore pattern (global or platform)
    // 5. If yes, return false (exclude)
    // 6. Otherwise, return true (include by default)
}
```

### 2.2 Update JDeployProject Model (Optional)
**Location**: `/shared/src/main/java/ca/weblite/jdeploy/models/JDeployProject.java`

**Changes Required**:
- Deprecate `getNativeNamespaces()` and related methods with `@Deprecated` annotation
- Keep existing methods for backward compatibility
- Add deprecation warnings in JavaDoc

## Phase 3: Update JAR Processing (Priority: High)

### 3.1 Update PlatformSpecificJarProcessor
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/services/PlatformSpecificJarProcessor.java`

**Changes Required**:
- Add `JDeployIgnoreService` dependency
- Update method signatures to accept `JDeployProject` and `Platform` instead of namespace lists
- Use `JDeployIgnoreService.shouldIncludeFile()` for filtering decisions
- Remove old namespace-based filtering logic

### 3.2 Update PlatformBundleGenerator
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/services/PlatformBundleGenerator.java`

**Changes Required**:
- Add `JDeployIgnoreService` dependency injection
- Remove logic that builds strip/keep lists from `nativeNamespaces`
- Pass `JDeployIgnoreService` to `PlatformSpecificJarProcessor`
- Update calls to use new service-based approach

## Phase 4: Update Publishing Drivers (Priority: High)

### 4.1 Update NPMPublishDriver
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/publishing/npm/NPMPublishDriver.java`

**Changes Required**:
- Update platform bundle generation to use ignore files
- Ensure `.jdpignore` files are included in published packages

### 4.2 Update GitHubPublishDriver
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/publishing/github/GitHubPublishDriver.java`

**Changes Required**:
- Update platform bundle generation to use ignore files
- Ensure proper tarball generation with filtered JARs

## Phase 5: GUI Updates (Priority: Medium)

### 5.1 Create Bundle Filters Panel (MVP)
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/gui/tabs/BundleFiltersPanel.java`

**Panel Structure**:
- **New tab**: "Bundle Filters" positioned after "Runtime Args" and before "Permissions"
- **Sub-tabbed interface**: Global, Windows, macOS, Linux tabs for editing respective `.jdpignore*` files
- **No preview functionality**: MVP excludes build-dependent preview features
- **No separate save buttons**: Integrates with existing project save system

**Components per tab**:
- `JTextArea` (60x15 chars) with scroll pane for pattern editing
- Basic help text explaining pattern syntax  
- Optional quick pattern builder buttons for common operations
- Change listeners that call existing `setModified()` for integration

**File Management**:
- **Auto-cleanup**: Delete empty `.jdpignore*` files during save
- **Load existing**: Read current `.jdpignore*` files when editor opens
- **Change detection**: Use existing file watching system for external changes

### 5.2 Update JDeployProjectEditor Integration
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/gui/JDeployProjectEditor.java`

**Integration Points**:
- Add "Bundle Filters" tab to main tabbed pane
- Add `saveBundleFilters()` method called from existing save functionality
- Implement empty file cleanup logic during save
- Add bundle filter change listeners to modification tracking system

**Save Logic**:
```java
private void saveBundleFilters() {
    for (Platform platform : Platform.values()) {
        String content = getFilterContent(platform);
        File ignoreFile = getIgnoreFile(platform);
        
        if (content.trim().isEmpty()) {
            if (ignoreFile.exists()) {
                ignoreFile.delete(); // Clean up empty files
            }
        } else {
            FileUtils.writeStringToFile(ignoreFile, content, "UTF-8");
        }
    }
}
```

**UI Flow**:
1. User edits patterns in any tab
2. Change listeners mark project as modified
3. Save project (Ctrl+S) saves all `.jdpignore*` files
4. Empty files are automatically deleted
5. External file changes trigger reload (existing file watching)

## Phase 6: Testing (Priority: High)

### 6.1 Unit Tests to Update
- `/cli/src/test/java/ca/weblite/jdeploy/services/PlatformBundleGeneratorTest.java`
- `/cli/src/test/java/ca/weblite/jdeploy/services/PlatformSpecificJarProcessorTest.java`
- `/shared/src/test/java/ca/weblite/jdeploy/models/JDeployProjectPlatformBundlesTest.java`

### 6.2 New Unit Tests to Create
- `/cli/src/test/java/ca/weblite/jdeploy/services/JDeployIgnoreFileParserTest.java`
- `/cli/src/test/java/ca/weblite/jdeploy/models/JDeployIgnorePatternTest.java`

### 6.3 Integration Tests
- Test complete flow with `.jdpignore` files
- Test platform-specific bundle generation
- Test pattern matching edge cases

## Phase 7: Migration Support (Priority: Low)

### 7.1 Create Migration Tool
**Location**: `/cli/src/main/java/ca/weblite/jdeploy/migration/NativeNamespacesToIgnoreFileMigrator.java`

**Functionality**:
- Read existing `nativeNamespaces` from package.json
- Generate equivalent `.jdpignore` files
- Preserve existing configuration semantics

## Phase 8: Documentation (Priority: Medium)

### 8.1 User Documentation
- Update README with .jdpignore file examples
- Create migration guide for existing projects
- Add troubleshooting section

### 8.2 Code Documentation
- Add comprehensive JavaDoc for new classes
- Update existing class documentation

## Implementation Timeline

### Week 1: Core Infrastructure
- [ ] Create `JDeployIgnoreFileParser` service
- [ ] Create `JDeployIgnorePattern` model
- [ ] Update `JDeployProject` model
- [ ] Write unit tests for new components

### Week 2: JAR Processing Updates
- [ ] Update `PlatformSpecificJarProcessor`
- [ ] Update `PlatformBundleGenerator`
- [ ] Update NPM and GitHub publishing drivers
- [ ] Integration testing

### Week 3: GUI and Polish
- [ ] Update GUI components
- [ ] Create migration tool
- [ ] Update documentation
- [ ] Final testing and bug fixes

## Backward Compatibility Strategy

### Deprecation Approach
1. Mark `nativeNamespaces` methods as `@Deprecated`
2. Support reading old format for 1-2 versions
3. Log warnings when old format is detected
4. Provide clear migration path in documentation

### Auto-Migration
1. Detect projects using old `nativeNamespaces` format
2. Offer to auto-generate `.jdpignore` files
3. Preserve existing behavior during transition

### Testing Strategy
1. Keep tests for old format during transition period
2. Add tests for migration scenarios
3. Ensure no regression in existing functionality

## Example .jdpignore Files

### Global .jdpignore
```
# Ignore test and debug libraries
com.testing.mocklibs.native
com.development.debugging.native

# Ignore obsolete libraries
com.obsolete.legacy.native

# But keep required test utilities
!com.testing.mocklibs.native.required
```

### Platform-specific .jdpignore.mac-x64
```
# Keep macOS x64 native libraries
!ca.weblite.native.mac.x64
!com.thirdparty.foo.bar.native.mac.x64
!/my-native-lib-macos.dylib
!/native/macos/
```

### Platform-specific .jdpignore.win-x64
```
# Keep Windows x64 native libraries
!ca.weblite.native.win.x64
!com.thirdparty.foo.bar.native.win.x64
!/my-native-lib.dll
!/native/windows/
```

## Success Criteria

1. **Functional Requirements**:
   - .jdpignore files correctly filter native libraries
   - Keep patterns override ignore patterns
   - Platform-specific bundles are generated correctly
   - Backward compatibility is maintained

2. **Performance Requirements**:
   - File parsing is efficient
   - Pattern matching doesn't significantly impact build time
   - JAR processing remains performant

3. **Quality Requirements**:
   - All existing tests pass
   - New functionality has >80% test coverage
   - Documentation is complete and clear
   - Migration path is smooth for existing users

## Notes and Considerations

1. **Pattern Syntax**:
   - Java package notation: `com.example.native`
   - Path notation: `/path/to/file`
   - Keep notation: `!pattern`
   - Comments: `# comment`

2. **File Resolution Priority**:
   - Keep patterns always win
   - Platform patterns extend global patterns
   - Default is to include files

3. **Edge Cases to Handle**:
   - Empty .jdpignore files
   - Missing platform-specific files
   - Malformed patterns
   - Circular dependencies in patterns

## Status Tracking

Use this section to track implementation progress:

- [x] Phase 1: Core Infrastructure
- [x] Phase 2: Create JDeploy Ignore Service  
- [x] Phase 3: Update JAR Processing
- [x] Phase 4: Update Publishing Drivers
- [x] Phase 6: Testing (Unit Tests and Integration Tests)
- [ ] Phase 5: GUI Updates
- [ ] Phase 7: Migration Support
- [ ] Phase 8: Documentation

## Implementation Notes

**For the implementing model**: This plan was created by Opus. When implementing:
1. Always reference this plan document using the `Read` tool at the start of each session
2. Update the status tracking checkboxes as phases are completed
3. Add implementation notes and decisions in this section
4. If you encounter issues or need to deviate from the plan, document the changes here
5. Use the `TodoWrite` tool to track specific implementation tasks within each phase

**For continuity**: Each implementation session should begin by reading this plan and the current project state to understand context and progress.

### Phase 1 Implementation Notes (Completed 2025-09-08):
- **Architecture Decision**: Moved all .jdpignore functionality to CLI module instead of shared module, since this is build/packaging logic
- **Files Created**:
  - `/cli/src/main/java/ca/weblite/jdeploy/models/JDeployIgnorePattern.java` - Model class for ignore patterns
  - `/cli/src/main/java/ca/weblite/jdeploy/services/JDeployIgnoreFileParser.java` - Parser service for .jdpignore files
- **Pattern Matching**: Implemented regex-based pattern matching with support for wildcards and directory patterns
- **File Formats**: Supports both Java package notation (com.example.native) and path notation (/path/to/file)
- **Keep Patterns**: Implemented ! prefix support for keep patterns that override ignore patterns
- **Compilation**: Both classes compile successfully with Java 8 compatibility

### Phase 2 Implementation Notes (Completed 2025-09-09):
- **Service Layer**: Created JDeployIgnoreService as bridge between shared and CLI modules
- **File Created**: `/cli/src/main/java/ca/weblite/jdeploy/services/JDeployIgnoreService.java` - Main service for ignore file processing
- **Features Implemented**:
  - Pattern caching for performance (uses file modification time as cache key)
  - Global and platform-specific .jdpignore file loading
  - File filtering logic with keep patterns overriding ignore patterns
  - Statistics and debugging methods
  - Proper error handling with fallback to empty patterns
- **Deprecation**: Added @Deprecated annotations to JDeployProject.getNativeNamespaces() methods
- **Module Boundaries**: Correctly maintains CLI → shared dependency direction
- **Compilation**: All modules compile successfully with new service layer

### Phase 2 Unit Tests (Completed 2025-09-09):
- **Test Files Created**:
  - `/cli/src/test/java/ca/weblite/jdeploy/models/JDeployIgnorePatternTest.java` - 16 test methods covering pattern matching logic
  - `/cli/src/test/java/ca/weblite/jdeploy/services/JDeployIgnoreFileParserTest.java` - 15 test methods covering file parsing and pattern conversion
  - `/cli/src/test/java/ca/weblite/jdeploy/services/JDeployIgnoreServiceTest.java` - 18 test methods covering service layer functionality
- **Test Coverage**: Comprehensive coverage including edge cases, error handling, caching, and integration scenarios
- **Java 8 Compatibility**: Fixed List.of() usage for Java 8 compatibility using Arrays.asList() and Collections
- **Pattern Logic Fixes**: Improved whitespace handling and pattern conversion logic based on test feedback
- **Mock Testing**: Extensive use of Mockito for testing service interactions and caching behavior

### Phase 3 Implementation Notes (Completed 2025-09-11):
- **PlatformSpecificJarProcessor Updates**:
  - Added JDeployIgnoreService dependency injection
  - Created new method: `processJarForPlatform(File, JDeployProject, Platform)` using ignore service filtering
  - Implemented `createProcessedJarWithIgnoreService()` method using ignore service filtering
  - **BREAKING CHANGE**: Completely removed legacy namespace-based methods
- **PlatformBundleGenerator Updates**:
  - Added JDeployIgnoreService dependency injection
  - Updated platform bundle generation to use ONLY .jdpignore files
  - Created `processJarsWithIgnoreService()` method for ignore file approach
  - Added `shouldFilterDefaultBundle()` and `shouldGeneratePlatformBundles()` methods
  - **BREAKING CHANGE**: Completely removed all legacy namespace methods and fallback logic
- **Integration Strategy**: 
  - Projects use .jdpignore files if present
  - **NO fallback to legacy nativeNamespaces** - that implementation was completely removed
  - If no .jdpignore files exist, bundles are not filtered at all
- **Platform Detection Logic**: Platform bundles are generated ONLY for platforms that have platform-specific .jdpignore files with patterns
- **Error Handling**: Graceful error handling with warnings for failed JAR processing
- **Compilation**: All updates compile successfully with Java 8 compatibility

### Code Cleanup (Completed 2025-09-11):
- **Duplicate Class Removal**: Removed duplicate `JDeployIgnorePattern` class from shared module at `/shared/src/main/java/ca/weblite/jdeploy/models/JDeployIgnorePattern.java`
- **Architecture Correction**: Confirmed that `JDeployIgnorePattern` class correctly belongs only in CLI module as per original plan
- **Module Verification**: Verified that:
  - No code in shared module was referencing the duplicate class
  - CLI module properly imports and uses the class from `ca.weblite.jdeploy.models.JDeployIgnorePattern`
  - Both shared and CLI modules compile successfully after cleanup
- **Rationale**: The .jdpignore functionality is build/packaging logic that belongs in the CLI module, not the shared module

### Phase 4 Implementation Notes (Completed 2025-09-13):
- **Platform.DEFAULT Implementation**:
  - Added `Platform.DEFAULT` enum value to handle default/global bundle processing
  - Updated `Platform.fromIdentifier()` and `getAllIdentifiers()` to include DEFAULT platform
  - Fixed Platform.DEFAULT to return "package" for `getPackagePropertyName()` (default bundle uses base "package" property)
  - Updated all method signatures to require non-null Platform parameter, eliminating null pointer exceptions
- **DefaultBundleService Creation**:
  - Created reusable service `/cli/src/main/java/ca/weblite/jdeploy/services/DefaultBundleService.java`
  - Combines JAR processing and tarball generation for default bundles
  - Applies only global .jdpignore patterns using Platform.DEFAULT
  - Provides error handling that doesn't fail entire publishing process
  - Methods: `processDefaultBundle()`, `processDefaultBundleAndCreateTarball()`, `shouldProcessDefaultBundle()`
- **Publishing Driver Updates**:
  - **GitHubPublishDriver**: Added DefaultBundleService dependency, processes default bundle with global ignore rules
  - **NPMPublishDriver**: Added DefaultBundleService dependency, processes default bundle in multiple scenarios
  - Both drivers now consistently apply global .jdpignore filtering to default bundles before publishing
- **Pattern Matching Simplification**:
  - Simplified to namespace-based pattern matching (removed complex regex support)
  - Pattern rules: All patterns treated as namespace prefixes, dots convert to slashes, leading "/" forces path interpretation
  - Namespace matching: exact matches, subdirectory matches, class file matching for .class files
  - Added wildcard support: patterns containing "*" use regex matching (`*` becomes `[^/]*`)

### Phase 4 Testing Updates (Completed 2025-09-13):
- **DefaultBundleProcessingTest**: Created comprehensive test for Platform.DEFAULT functionality
- **DefaultBundleServiceTest**: Created full test suite covering JAR processing, tarball creation, and error handling  
- **Test Fixes**: Updated all existing tests for new constructor signatures with DefaultBundleService parameter
- **NPMPublishDriverPlatformBundlesTest**: Updated to handle new flow where default bundle processing occurs multiple times
- **Pattern Matching Tests**: Updated tests to use new namespace-based pattern matching with wildcard support
- **All Tests Passing**: 296+ tests running successfully with comprehensive coverage

### Wildcard Support Implementation (Completed 2025-09-13):
- **Wildcard Pattern Matching**: Added support for `*` wildcards in .jdpignore patterns
- **Implementation Location**: `JDeployIgnorePattern.matchesNamespace()` method
- **Matching Logic**: 
  - If pattern contains `*`, converts to regex where `*` becomes `[^/]*` (matches any chars except path separator)
  - Escapes literal dots in pattern to `\.` for proper regex matching
  - Example: Pattern `/skiko-*.dll` matches `skiko-windows-x64.dll` but not `skiko-linux-x64.so`
- **Test Coverage**: Added `testMatchesWildcard()` test in `JDeployIgnorePatternTest`

### Phase 6 Testing (Completed 2025-09-13):
- **Comprehensive Integration Tests**: Full .jdpignore workflow testing including:
  - `JDeployIgnoreIntegrationTest`: Real JAR files with actual .jdpignore files
  - `DefaultBundleProcessingTest`: Platform.DEFAULT processing with global patterns  
  - `PlatformBundleGeneratorIgnoreTest`: Platform-specific bundle generation
- **Updated Legacy Tests**: All existing tests migrated from nativeNamespaces to .jdpignore approach
- **Test Statistics**: 296 tests, 0 failures, comprehensive coverage of new functionality

**Status Update**: Phases 1-4 and 6 are now FULLY COMPLETED with all functionality working and tested.

Last Updated: 2025-09-13