# Implementation Plan: Prebuilt Apps Publishing

**RFC**: `rfc/app-bundle-publishing.md`
**Status**: Detailed Implementation Tasks
**Created**: 2026-02-08
**Updated**: 2026-02-12

## Overview

This plan outlines the implementation tasks for the Prebuilt Apps Publishing feature, organized by component. The feature enables publishing pre-built native app bundles to GitHub releases, which is required infrastructure for Windows code signing.

## Component Breakdown

---

## 1. CLI / Publishing (`cli/`)

The CLI handles the publishing workflow and needs the most changes.

### 1.1 PrebuiltAppRequirementService

Create a service to determine when prebuilt apps are needed.

**New Files:**
- `cli/src/main/java/ca/weblite/jdeploy/services/PrebuiltAppRequirementService.java` (interface)
- `cli/src/main/java/ca/weblite/jdeploy/services/PrebuiltAppRequirementServiceImpl.java` (implementation)

**Tasks:**

- [ ] **1.1.1** Create `PrebuiltAppRequirementService` interface:
  ```java
  public interface PrebuiltAppRequirementService {
      boolean requiresPrebuiltApp(JDeployProject project, Platform platform);
      List<Platform> getRequiredPlatforms(JDeployProject project);
      boolean isPrebuiltAppsEnabled(JDeployProject project);
  }
  ```

- [ ] **1.1.2** Create `PrebuiltAppRequirementServiceImpl`:
  - Inject `JDeployProject` to read configuration
  - Check `windowsSigning.enabled` → return `[WIN_X64, WIN_ARM64]`
  - Check future `macSigning.enabled` → return `[MAC_X64, MAC_ARM64]`
  - Return empty list if no signing enabled

- [ ] **1.1.3** Add unit tests in `cli/src/test/java/ca/weblite/jdeploy/services/PrebuiltAppRequirementServiceTest.java`:
  - Test: Windows signing enabled returns Windows platforms
  - Test: No signing enabled returns empty list
  - Test: Future macOS signing returns Mac platforms

---

### 1.2 JDeployProject Model Updates

Extend the model to support prebuilt apps configuration.

**File:** `shared/src/main/java/ca/weblite/jdeploy/models/JDeployProject.java`

**Tasks:**

- [ ] **1.2.1** Add method to read `prebuiltApps` array:
  ```java
  public List<String> getPrebuiltApps() {
      JSONArray arr = getJDeployObject().optJSONArray("prebuiltApps");
      if (arr == null) return Collections.emptyList();
      List<String> platforms = new ArrayList<>();
      for (int i = 0; i < arr.length(); i++) {
          platforms.add(arr.getString(i));
      }
      return platforms;
  }
  ```

- [ ] **1.2.2** Add method to write `prebuiltApps` array:
  ```java
  public void setPrebuiltApps(List<String> platforms) {
      JSONArray arr = new JSONArray();
      for (String p : platforms) arr.put(p);
      getJDeployObject().put("prebuiltApps", arr);
  }
  ```

- [ ] **1.2.3** Add method to check if prebuilt app exists for platform:
  ```java
  public boolean hasPrebuiltApp(Platform platform) {
      return getPrebuiltApps().contains(platform.getIdentifier());
  }
  ```

- [ ] **1.2.4** Add method to read `windowsSigning` configuration:
  ```java
  public boolean isWindowsSigningEnabled() {
      JSONObject signing = getJDeployObject().optJSONObject("windowsSigning");
      return signing != null && signing.optBoolean("enabled", false);
  }
  ```

- [ ] **1.2.5** Add unit tests for new methods

---

### 1.3 Native Bundle Generation (Bundler)

The bundler creates native app bundles (exe, .app, etc.). Verify cross-platform support.

**Files:**
- `shared/src/main/java/ca/weblite/jdeploy/appbundler/Bundler.java`
- `shared/src/main/java/com/joshondesign/appbundler/win/WindowsBundler.java`
- `shared/src/main/java/com/joshondesign/appbundler/mac/MacBundler.java`

**Tasks:**

- [ ] **1.3.1** Verify `Bundler.runit()` can generate bundles for non-host platforms:
  - Test generating Windows bundle on macOS
  - Test generating macOS bundle on Windows
  - Document any limitations

- [ ] **1.3.2** Ensure `BundlerResult` properly tracks output files per platform:
  - Review `BundlerResult.getOutputFile(String type)` method
  - Verify all platforms return valid output directories

- [ ] **1.3.3** Create helper method to invoke bundler for specific platform:
  ```java
  // In new PrebuiltAppBundler class or existing service
  public File generateNativeBundle(JDeployProject project, Platform platform, File outputDir)
  ```

- [ ] **1.3.4** Add integration test: Generate native bundle for each platform

---

### 1.4 Tarball Packaging Service

Create service to package native bundles into `.tgz` tarballs.

**New File:** `cli/src/main/java/ca/weblite/jdeploy/services/PrebuiltAppPackager.java`

**Tasks:**

- [ ] **1.4.1** Create `PrebuiltAppPackager` class:
  ```java
  public class PrebuiltAppPackager {
      public File packageNativeBundle(
          File nativeBundleDir,
          String appName,
          String version,
          Platform platform,
          File outputDir
      );

      public String getTarballName(String appName, String version, Platform platform);

      public String generateChecksum(File tarball);
  }
  ```

- [ ] **1.4.2** Implement tarball naming convention:
  ```java
  public String getTarballName(String appName, String version, Platform platform) {
      // Returns: "myapp-1.0.0-win-x64-bin.tgz"
      return String.format("%s-%s-%s-bin.tgz", appName, version, platform.getIdentifier());
  }
  ```

- [ ] **1.4.3** Implement tarball creation using existing `ArchiveUtil` or `npm pack`:
  - Option A: Use `npm.pack()` like `PlatformBundleGenerator`
  - Option B: Use `ArchiveUtil.tar()` directly
  - Ensure proper directory structure in tarball

- [ ] **1.4.4** Implement checksum generation (follow icon checksum pattern):
  ```java
  public String generateChecksum(File tarball) {
      // SHA-256 checksum
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(Files.readAllBytes(tarball.toPath()));
      return Base64.getEncoder().encodeToString(hash);
  }
  ```

- [ ] **1.4.5** Add unit tests for tarball naming and checksum generation

---

### 1.5 GitHub Publishing Integration

Modify the GitHub publish driver to upload prebuilt apps.

**File:** `cli/src/main/java/ca/weblite/jdeploy/publishing/github/GitHubPublishDriver.java`

**Tasks:**

- [ ] **1.5.1** Inject `PrebuiltAppRequirementService` into `GitHubPublishDriver`:
  ```java
  private final PrebuiltAppRequirementService prebuiltAppRequirementService;
  ```

- [ ] **1.5.2** Inject `PrebuiltAppPackager` into `GitHubPublishDriver`

- [ ] **1.5.3** Add prebuilt app generation to `prepare()` method (after line ~335):
  ```java
  // After makePackage() and platform bundle generation
  if (prebuiltAppRequirementService.isPrebuiltAppsEnabled(project)) {
      generatePrebuiltApps(context);
  }
  ```

- [ ] **1.5.4** Implement `generatePrebuiltApps()` method:
  ```java
  private void generatePrebuiltApps(PublishingContext context) {
      List<Platform> platforms = prebuiltAppRequirementService.getRequiredPlatforms(project);
      for (Platform platform : platforms) {
          // 1. Generate native bundle using bundler
          File nativeBundle = bundler.generateNativeBundle(project, platform, tempDir);

          // 2. Package into tarball
          File tarball = packager.packageNativeBundle(
              nativeBundle,
              context.getAppName(),
              context.getVersion(),
              platform,
              context.getGithubReleaseFilesDir()
          );

          // 3. Track successful platforms
          successfulPlatforms.add(platform);
      }
  }
  ```

- [ ] **1.5.5** Ensure tarballs are uploaded in `publish()` method:
  - Files in `githubReleaseFilesDir` are automatically uploaded
  - Verify `*-bin.tgz` files are included

- [ ] **1.5.6** Add error handling and retry logic for upload failures:
  - Catch upload exceptions per platform
  - Continue with other platforms on failure
  - Log failed platforms with reason

- [ ] **1.5.7** Add integration test for GitHub upload flow:
  - Mock GitHub API
  - Verify correct files uploaded
  - Verify naming convention

---

### 1.6 Package.json Embedding

Write the `prebuiltApps` platform list to package.json at publish time.

**File:** `cli/src/main/java/ca/weblite/jdeploy/publishing/github/GitHubPublishDriver.java`

**Tasks:**

- [ ] **1.6.1** After successful uploads, embed platform list into package.json:
  ```java
  // In publish() method, after successful uploads
  List<String> platformIds = successfulPlatforms.stream()
      .map(Platform::getIdentifier)
      .collect(Collectors.toList());
  project.setPrebuiltApps(platformIds);
  project.save();  // Persist to package.json
  ```

- [ ] **1.6.2** Follow the same pattern as `iconHash` embedding:
  - Review how `saveGithubReleaseFiles()` handles icon
  - Apply same approach for prebuiltApps

- [ ] **1.6.3** Only include platforms that were successfully uploaded:
  - Track successful vs failed platforms during upload
  - Embed only successful ones

- [ ] **1.6.4** Add test: Verify package.json contains `prebuiltApps` array after publish

---

## 2. Launcher (`launcher/` - native code)

The launcher needs to support external preferences for prebuilt apps.

### 2.1 External Preferences Support

Implement loading of external preferences.xml file.

**Note:** This is native code (C/C++/Rust depending on platform). Tasks are high-level.

**Tasks:**

- [ ] **2.1.1** Define preferences.xml schema:
  ```xml
  <?xml version="1.0" encoding="UTF-8"?>
  <preferences>
    <version>1.0.0</version>
    <prereleaseChannel>stable</prereleaseChannel>
    <autoUpdate>true</autoUpdate>
    <jvmArgs>-Xmx512m</jvmArgs>
  </preferences>
  ```

- [ ] **2.1.2** Implement FQN calculation:
  ```
  With source hash: {hash}.{package-name}  → abc123.my-app
  Without source:   {package-name}         → my-app
  ```

- [ ] **2.1.3** Implement preferences file path resolution:
  ```
  ~/.jdeploy/preferences/{fqn}/preferences.xml
  ```

- [ ] **2.1.4** Implement preferences loading at startup:
  - Check if preferences.xml exists
  - Parse XML if present
  - Fall back to embedded app.xml values if missing

- [ ] **2.1.5** Implement settings overlay logic:
  - External preferences override embedded values
  - Handle missing keys gracefully (use embedded default)

- [ ] **2.1.6** Implement preferences writing:
  - Create directory structure if needed
  - Write XML file when user changes settings

- [ ] **2.1.7** Handle corrupted preferences:
  - Catch XML parse errors
  - Log warning and use embedded defaults
  - Optionally rename corrupted file for debugging

- [ ] **2.1.8** Add unit tests for preferences loading/saving

---

## 3. Installer (`installer/`)

The installer needs to detect and use prebuilt apps instead of building locally.

### 3.1 Prebuilt App Detection

Detect when prebuilt apps are available.

**File:** `installer/src/main/java/ca/weblite/jdeploy/installer/` (identify main installer class)

**Tasks:**

- [ ] **3.1.1** Create `PrebuiltAppDetector` class:
  ```java
  public class PrebuiltAppDetector {
      public boolean hasPrebuiltApp(JSONObject packageJson, String platform);
      public String getPrebuiltAppUrl(JSONObject packageJson, String platform, PublishSource source);
  }
  ```

- [ ] **3.1.2** Implement platform detection:
  ```java
  public boolean hasPrebuiltApp(JSONObject packageJson, String platform) {
      JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
      if (jdeploy == null) return false;
      JSONArray prebuiltApps = jdeploy.optJSONArray("prebuiltApps");
      if (prebuiltApps == null) return false;
      for (int i = 0; i < prebuiltApps.length(); i++) {
          if (platform.equals(prebuiltApps.getString(i))) return true;
      }
      return false;
  }
  ```

- [ ] **3.1.3** Implement URL construction for GitHub releases:
  ```java
  // Pattern: https://github.com/{owner}/{repo}/releases/download/v{version}/{name}-{version}-{platform}-bin.tgz
  public String getGitHubUrl(String owner, String repo, String name, String version, String platform) {
      return String.format(
          "https://github.com/%s/%s/releases/download/v%s/%s-%s-%s-bin.tgz",
          owner, repo, version, name, version, platform
      );
  }
  ```

- [ ] **3.1.4** Implement URL construction for npm registry (future):
  ```java
  // Pattern derived from npm package URL + tarball naming
  ```

- [ ] **3.1.5** Add unit tests for URL construction

---

### 3.2 Prebuilt App Download

Download and extract prebuilt apps.

**New File:** `installer/src/main/java/ca/weblite/jdeploy/installer/PrebuiltAppDownloader.java`

**Tasks:**

- [ ] **3.2.1** Create `PrebuiltAppDownloader` class:
  ```java
  public class PrebuiltAppDownloader {
      public File download(String url, File destinationDir) throws IOException;
      public boolean verifyChecksum(File tarball, String expectedChecksum);
      public void extract(File tarball, File destinationDir) throws IOException;
  }
  ```

- [ ] **3.2.2** Implement download logic:
  - Use existing HTTP client infrastructure
  - Support progress reporting (for UI)
  - Handle redirects

- [ ] **3.2.3** Implement checksum verification:
  ```java
  public boolean verifyChecksum(File tarball, String expectedChecksum) {
      String actualChecksum = calculateChecksum(tarball);
      return expectedChecksum.equals(actualChecksum);
  }
  ```

- [ ] **3.2.4** Implement tarball extraction using `ArchiveUtil`:
  ```java
  public void extract(File tarball, File destinationDir) throws IOException {
      ArchiveUtil.extract(tarball, destinationDir, null);
  }
  ```

- [ ] **3.2.5** Add integration test for download and extraction

---

### 3.3 Fallback Behavior

Implement fallback to local building.

**Tasks:**

- [ ] **3.3.1** Wrap prebuilt app download in try-catch:
  ```java
  try {
      File bundle = prebuiltAppDownloader.downloadAndExtract(url, destDir);
      // Use prebuilt bundle
  } catch (PrebuiltAppException e) {
      logger.warn("Prebuilt app download failed: " + e.getMessage());
      // Fall back to local building
      buildAppLocally();
  }
  ```

- [ ] **3.3.2** Detect if app was supposed to be signed:
  ```java
  boolean wasSignedApp = packageJson.optJSONObject("jdeploy")
      .optJSONObject("windowsSigning")
      .optBoolean("enabled", false);
  ```

- [ ] **3.3.3** Warn user about unsigned fallback:
  ```java
  if (wasSignedApp && usingFallback) {
      showWarning("The app could not be downloaded and was built locally. " +
                  "The local build is NOT code-signed and may trigger security warnings.");
  }
  ```

- [ ] **3.3.4** Log fallback reason:
  - Network error
  - Checksum mismatch
  - Extraction failure
  - Missing platform in prebuiltApps

---

### 3.4 External Preferences Setup

Configure external preferences during installation.

**Tasks:**

- [ ] **3.4.1** Create preferences directory structure:
  ```java
  File prefsDir = new File(System.getProperty("user.home"),
      ".jdeploy/preferences/" + fqn);
  prefsDir.mkdirs();
  ```

- [ ] **3.4.2** Calculate FQN from app info:
  ```java
  public String calculateFqn(String source, String packageName) {
      if (source != null && !source.isEmpty()) {
          String hash = sha256(source).substring(0, 8);
          return hash + "." + packageName;
      }
      return packageName;
  }
  ```

- [ ] **3.4.3** Write initial preferences.xml:
  ```java
  public void writeInitialPreferences(File prefsDir, InstallerOptions options) {
      File prefsFile = new File(prefsDir, "preferences.xml");
      // Write XML with user-selected settings
  }
  ```

- [ ] **3.4.4** Include user-selected settings:
  - Prerelease channel preference (from installer UI)
  - Auto-update settings
  - Any other user-configurable options

- [ ] **3.4.5** Add test for preferences file creation

---

### 3.5 Installation Flow Integration

Integrate prebuilt app support into main installation flow.

**Tasks:**

- [ ] **3.5.1** Modify main installation flow to check for prebuilt apps first:
  ```java
  public void install() {
      String platform = detectCurrentPlatform();

      if (prebuiltAppDetector.hasPrebuiltApp(packageJson, platform)) {
          try {
              installFromPrebuiltApp(platform);
              return;
          } catch (Exception e) {
              logger.warn("Falling back to local build", e);
          }
      }

      // Existing local build path
      buildAndInstallLocally();
  }
  ```

- [ ] **3.5.2** Implement `installFromPrebuiltApp()`:
  ```java
  private void installFromPrebuiltApp(String platform) {
      String url = prebuiltAppDetector.getPrebuiltAppUrl(packageJson, platform, publishSource);
      File tarball = downloader.download(url, tempDir);
      downloader.extract(tarball, installDir);
      setupPreferences();
      createShortcuts();
  }
  ```

- [ ] **3.5.3** Add progress reporting for prebuilt app download

- [ ] **3.5.4** Add end-to-end integration test

---

## 4. Registry / Website (Future - Deferred)

Registry support is deferred to future implementation pending paid membership model.

### 4.1 API Design (Future)

**Planned Endpoints:**
- `POST /api/apps/{appId}/prebuilt/{platform}/{version}` - Upload prebuilt app
- `GET /api/apps/{appId}/prebuilt/{platform}/{version}` - Download prebuilt app
- `DELETE /api/apps/{appId}/prebuilt/{platform}/{version}` - Remove prebuilt app

### 4.2 Storage (Future)

- Similar pattern to icon storage
- CDN integration for large file delivery
- Retention policies for old versions

### 4.3 Access Control (Future)

- Require jDeploy API key
- Paid membership for storage/bandwidth costs

---

## 5. Project Editor (`gui/`)

Minimal changes needed since prebuilt apps are automatic.

### 5.1 Status Display (Optional)

**Tasks:**

- [ ] **5.1.1** Add "Prebuilt Apps" indicator to publish status:
  - Show which platforms will have prebuilt apps
  - Based on signing configuration

- [ ] **5.1.2** Show prebuilt app status after publish:
  - Success/failure per platform
  - File sizes uploaded

- [ ] **5.1.3** Add tooltip explaining prebuilt apps:
  - "Prebuilt apps are automatically generated when code signing is enabled"

---

## Implementation Phases

### Phase 1: Core Infrastructure (Foundation)
| Task | Description | Dependencies |
|------|-------------|--------------|
| 1.1.1-1.1.3 | PrebuiltAppRequirementService | None |
| 1.2.1-1.2.5 | JDeployProject model updates | None |
| 1.4.1-1.4.5 | PrebuiltAppPackager | None |

### Phase 2: Bundle Generation & Publishing
| Task | Description | Dependencies |
|------|-------------|--------------|
| 1.3.1-1.3.4 | Verify bundler cross-platform support | Phase 1 |
| 1.5.1-1.5.7 | GitHub Publishing Integration | Phase 1 |
| 1.6.1-1.6.4 | Package.json embedding | 1.5.x |

### Phase 3: Installer Support
| Task | Description | Dependencies |
|------|-------------|--------------|
| 3.1.1-3.1.5 | Prebuilt App Detection | 1.2.x |
| 3.2.1-3.2.5 | Prebuilt App Download | 3.1.x |
| 3.3.1-3.3.4 | Fallback Behavior | 3.2.x |
| 3.4.1-3.4.5 | External Preferences Setup | None |
| 3.5.1-3.5.4 | Installation Flow Integration | 3.1-3.4 |

### Phase 4: Launcher Updates
| Task | Description | Dependencies |
|------|-------------|--------------|
| 2.1.1-2.1.8 | External Preferences Support | None (parallel work) |

### Phase 5: Polish
| Task | Description | Dependencies |
|------|-------------|--------------|
| 5.1.1-5.1.3 | Project Editor Status Display | Phase 2 |
| - | Documentation | All phases |
| - | End-to-end testing | All phases |

---

## Dependencies

- **Windows Signing RFC** (`rfc/windows-signed-exe-support.md`) - Primary consumer of this feature
- **Platform-Specific Bundles RFC** - May affect which content goes in prebuilt apps

## Risks

| Risk | Mitigation |
|------|------------|
| Cross-platform bundling complexity | Start with same-platform bundling, add cross-platform later |
| Large file uploads to GitHub | Implement retry logic, chunked uploads if needed |
| Checksum verification | Use same proven approach as icons/splash images |

## Open Questions for Team

1. Should we support cross-platform bundling in Phase 1, or only bundle for current platform?
2. What's the maximum acceptable publish time increase?
3. Should the installer show progress for prebuilt app downloads?

---

## Task Summary

| Phase | Tasks | New Files | Modified Files |
|-------|-------|-----------|----------------|
| Phase 1 | 13 | 3 | 1 |
| Phase 2 | 15 | 0 | 2 |
| Phase 3 | 19 | 2 | 1+ |
| Phase 4 | 8 | 0 | Native launcher |
| Phase 5 | 3+ | 0 | GUI files |
| **Total** | **58+** | **5** | **5+** |

---

## Next Steps

1. ~~Team review of this plan~~
2. ~~Estimate effort for each phase~~
3. ~~Assign owners to components~~
4. ~~Create detailed implementation tasks~~ ✓
5. Begin Phase 1 implementation
