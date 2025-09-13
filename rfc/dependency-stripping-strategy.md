# RFC: Platform-Specific Dependency Stripping for jDeploy Installer

## Summary

This RFC proposes a strategy to reduce installer bundle sizes by 50-56% through platform-specific dependency stripping. The current universal installer includes dependencies and binaries for all platforms (~79MB), but each platform only needs a subset of these resources.

## Status

- **Status**: Draft
- **Author**: Claude Code Analysis
- **Created**: 2025-09-07

## Problem Statement

The jDeploy installer currently bundles all dependencies and platform binaries universally, resulting in:

- **Current installer size**: ~79MB
- **Wasted space**: ~40MB of unused platform-specific resources per installation
- **Distribution inefficiency**: Users download 2-3x more data than needed
- **Poor user experience**: Slow download times, especially on limited bandwidth

### Dependency Size Breakdown

| Component | Size | Usage |
|-----------|------|--------|
| Client4JLauncher binaries (all platforms) | 45MB | Platform-specific |
| Java dependencies | 34MB | Mixed |
| - Scala dependencies | 5MB | Unused |
| - OpenAI/HTTP stack | 4MB | Unused |  
| - JNA (Windows-only) | 3MB | Platform-specific |
| - Other dependencies | 22MB | Mixed |

## Proposed Solution

### Three-Phase Dependency Stripping Strategy

#### Phase 1: Platform-Specific Client4JLauncher Filtering (Highest Impact)

**Current State**: All platforms include all Client4JLauncher binaries
```
/shared/src/main/resources/com/joshondesign/appbundler/
├── win/x64/Client4JLauncher.exe (8.1MB)
├── win/arm64/Client4JLauncher.exe (7.9MB)
├── mac/x64/Client4JLauncher (7.9MB)
├── mac/arm64/Client4JLauncher (7.6MB)
├── linux/x64/Client4JLauncher (6.7MB)
└── linux/arm64/Client4JLauncher (6.9MB)
```

**Solution**: Maven profile-based resource filtering
```xml
<profiles>
  <profile>
    <id>windows</id>
    <build>
      <resources>
        <resource>
          <directory>src/main/resources</directory>
          <excludes>
            <exclude>**/mac/**</exclude>
            <exclude>**/linux/**</exclude>
          </excludes>
        </resource>
      </resources>
    </build>
  </profile>
  <!-- Similar profiles for mac and linux -->
</profiles>
```

**Impact**: 
- Windows: Keep 16MB, remove 29MB
- Mac: Keep 15.5MB, remove 29.5MB  
- Linux: Keep 13.6MB, remove 31.4MB

#### Phase 2: Universal Dependency Cleanup (Medium Impact)

**Remove Unused Dependencies** (identified via Maven dependency analysis):

1. **OpenAI GPT Stack** (4MB) - Not used in installer
   - `com.theokanning.openai-gpt3-java:service`
   - Jackson, Retrofit, RxJava, OkHttp transitive dependencies

2. **Scala Dependencies** (5MB) - Transitively included, not used
   - `scala-library-2.12.8.jar`
   - `mbknor-jackson-jsonschema_2.12`

3. **Unused Commons Libraries** (500KB)
   - `commons-cli`, `commons-exec`, `commons-lang3`
   - `slf4j-api`, `jmdns`

**Implementation**: POM exclusions
```xml
<dependency>
  <groupId>ca.weblite</groupId>
  <artifactId>jdeploy-shared</artifactId>
  <exclusions>
    <exclusion>
      <groupId>com.theokanning.openai-gpt3-java</groupId>
      <artifactId>service</artifactId>
    </exclusion>
    <exclusion>
      <groupId>com.kjetland</groupId>
      <artifactId>mbknor-jackson-jsonschema_2.12</artifactId>
    </exclusion>
    <!-- Additional exclusions -->
  </exclusions>
</dependency>
```

#### Phase 3: Platform-Specific Java Dependencies (Low Impact)

**JNA Dependencies** (3MB) - Windows-only
- `jna-5.10.0.jar` (1.7MB)
- `jna-platform-5.10.0.jar` (1.3MB)

Used only in installer Windows registry operations:
- `ca.weblite.jdeploy.installer.win.WinRegistry`
- `ca.weblite.jdeploy.installer.win.Shell32`
- `ca.weblite.jdeploy.installer.win.InstallWindowsRegistry`

**Implementation**: Profile-based conditional dependencies
```xml
<profile>
  <id>windows</id>
  <dependencies>
    <dependency>
      <groupId>net.java.dev.jna</groupId>
      <artifactId>jna</artifactId>
    </dependency>
  </dependencies>
</profile>
```

## Expected Results

### Size Reduction Summary

| Platform | Current Size | New Size | Reduction |
|----------|--------------|----------|-----------|
| Windows | 79MB | 40.5MB | 49% |
| Mac | 79MB | 37MB | 53% |
| Linux | 79MB | 35MB | 56% |

### Detailed Breakdown

**Windows Installer:**
- Base: 79MB
- Remove Mac/Linux Client4J: -29MB
- Remove unused dependencies: -9.5MB
- Keep JNA: 0MB
- **Final: 40.5MB (49% reduction)**

**Mac Installer:**
- Base: 79MB
- Remove Windows/Linux Client4J: -29.5MB
- Remove unused dependencies: -9.5MB
- Remove JNA: -3MB
- **Final: 37MB (53% reduction)**

**Linux Installer:**
- Base: 79MB
- Remove Windows/Mac Client4J: -31.4MB
- Remove unused dependencies: -9.5MB
- Remove JNA: -3MB
- **Final: 35MB (56% reduction)**

## Implementation Plan

### Phase 1: Build System Changes

1. **Update Maven Profiles**
   - Add platform-specific profiles in `installer/pom.xml`
   - Configure resource filtering by platform
   - Test profile activation

2. **CI/CD Pipeline Updates**
   - Modify GitHub Actions to build 3 separate installers
   - Update artifact naming: `installer-{platform}-{version}.jar`
   - Parallel builds using Maven profiles: `-Pwindows`, `-Pmac`, `-Plinux`

### Phase 2: Distribution Updates

1. **GitHub Release Strategy**
   - Upload 3 platform-specific installer JARs
   - Update download documentation
   - Maintain backward compatibility with universal installer (deprecated)

2. **NPM Package Updates**
   - Modify `jdeploy.js` to detect platform and download appropriate installer
   - Update installer resolution logic

### Phase 3: Testing & Validation

1. **Integration Tests**
   - Verify each platform-specific installer works correctly
   - Test resource loading and platform detection
   - Validate missing dependencies don't cause runtime issues

2. **Performance Testing**
   - Measure actual download times and installation speed
   - Validate size reductions match estimates
   - Test on different network conditions

## Risks & Mitigations

### Risk: Missing Platform Detection
**Impact**: Installer downloads wrong platform binaries
**Mitigation**: Robust platform detection in build and runtime

### Risk: Dependency Resolution Failures  
**Impact**: ClassNotFoundException at runtime
**Mitigation**: Comprehensive testing of all installer functions per platform

### Risk: Build Complexity Increase
**Impact**: More complex CI/CD pipeline, potential build failures
**Mitigation**: Gradual rollout, maintain universal build as fallback

## Alternative Approaches Considered

### Option 1: Single Installer with Runtime Platform Detection
- **Pros**: Maintains current distribution model
- **Cons**: Still includes all binaries, minimal size reduction

### Option 2: Separate Installer Modules
- **Pros**: Clean separation, easier testing
- **Cons**: Major architectural changes, higher maintenance overhead

### Option 3: Dynamic Dependency Loading
- **Pros**: Maximum flexibility
- **Cons**: Complex implementation, runtime dependency management

## Success Metrics

1. **Size Reduction**: Achieve 50%+ reduction in installer size for all platforms
2. **Download Speed**: 50%+ improvement in download times  
3. **User Satisfaction**: Positive feedback on installation experience
4. **Build Reliability**: <5% increase in build failure rate
5. **Distribution Efficiency**: Reduced CDN/bandwidth costs

## Timeline

- **Phase 1 (Weeks 1-2)**: Implement Maven profiles and resource filtering
- **Phase 2 (Weeks 3-4)**: Update CI/CD pipeline and testing
- **Phase 3 (Weeks 5-6)**: Distribution updates and documentation
- **Phase 4 (Weeks 7-8)**: Comprehensive testing and rollout

## Conclusion

Platform-specific dependency stripping represents the highest impact optimization for jDeploy installer distribution. By eliminating unused Client4JLauncher binaries and dependencies, we can achieve 50-56% size reductions while maintaining full functionality. The implementation leverages existing Maven tooling and requires minimal architectural changes, making it a low-risk, high-reward improvement.

The proposed three-phase approach allows for incremental implementation and validation, with the first phase alone delivering 60-70% of the total benefit through Client4JLauncher filtering.