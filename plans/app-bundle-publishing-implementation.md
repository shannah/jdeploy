# Implementation Plan: Prebuilt Apps Publishing

**RFC**: `rfc/app-bundle-publishing.md`
**Status**: High-Level Plan - Awaiting Team Review
**Created**: 2026-02-08

## Overview

This plan outlines the implementation tasks for the Prebuilt Apps Publishing feature, organized by component. The feature enables publishing pre-built native app bundles to GitHub releases, which is required infrastructure for Windows code signing.

## Component Breakdown

---

## 1. CLI / Publishing (`cli/`)

The CLI handles the publishing workflow and needs the most changes.

### 1.1 PrebuiltAppRequirementService

Create a service to determine when prebuilt apps are needed.

**Tasks:**
- [ ] Create `PrebuiltAppRequirementService` interface
- [ ] Implement platform detection logic:
  - If `windowsSigning.enabled` → require win-x64, win-arm64
  - If macOS signing enabled (future) → require mac-x64, mac-arm64
- [ ] Integrate with `JDeployProject` model to read signing configuration
- [ ] Add unit tests for requirement detection logic

**Key Files:**
- New: `cli/src/main/java/ca/weblite/jdeploy/services/PrebuiltAppRequirementService.java`

### 1.2 Native Bundle Generation (Bundler)

The bundler creates native app bundles (exe, .app, etc.). This is existing behavior.

**Tasks:**
- [ ] Ensure bundler can generate native bundles for platforms other than current host (cross-platform bundling)
- [ ] Support all platforms: win-x64, win-arm64, mac-x64, mac-arm64, linux-x64, linux-arm64
- [ ] Verify native bundles are complete and self-contained

**Key Files:**
- `shared/src/main/java/ca/weblite/jdeploy/appbundler/Bundler.java`
- `shared/src/main/java/com/joshondesign/appbundler/win/WindowsBundler.java`
- `shared/src/main/java/com/joshondesign/appbundler/mac/MacBundler.java`

### 1.3 Tarball Packaging (Publisher)

The publisher packages native bundles into `.tgz` tarballs for upload. This is a new publishing step.

**Tasks:**
- [ ] Create tarball packaging logic in publisher
- [ ] Implement naming convention: `{appname}-{version}-{platform}-bin.tgz`
- [ ] Generate checksums for each tarball (same approach as icons)
- [ ] Package the native bundle output from bundler into tarball format

### 1.4 GitHub Publishing Integration

Modify the GitHub publish driver to upload prebuilt apps.

**Tasks:**
- [ ] Query `PrebuiltAppRequirementService` during publish
- [ ] Invoke bundler to generate native bundles for required platforms
- [ ] Package native bundles into tarballs (via 1.3)
- [ ] Upload tarballs to GitHub release alongside installers
- [ ] Handle upload failures gracefully (retry, error reporting)
- [ ] Add integration tests for GitHub upload flow

**Key Files:**
- `cli/src/main/java/ca/weblite/jdeploy/publishing/GitHubPublishDriver.java`

### 1.5 Package.json Embedding

Write the `prebuiltApps` platform list to package.json at publish time.

**Tasks:**
- [ ] After successful upload, write `prebuiltApps` array to package.json
- [ ] Follow same pattern as `iconHash` embedding
- [ ] Only include platforms that were successfully uploaded
- [ ] Add/update `prebuiltApps` field in JDeployProject model

**Key Files:**
- `cli/src/main/java/ca/weblite/jdeploy/services/PackageJsonService.java` (or similar)
- `shared/src/main/java/ca/weblite/jdeploy/models/JDeployProject.java`

---

## 2. Launcher (`launcher/` - native code)

The launcher needs to support external preferences for prebuilt apps.

### 2.1 External Preferences Support

Implement loading of external preferences.xml file.

**Tasks:**
- [ ] Implement preferences file location: `~/.jdeploy/preferences/{fqn}/preferences.xml`
- [ ] Calculate FQN from app info:
  - With source: `{hash}.{package-name}`
  - Without source: `{package-name}`
- [ ] Load preferences.xml at startup if present
- [ ] Overlay external settings on embedded app.xml values
- [ ] Handle missing/corrupted preferences gracefully (use embedded defaults)
- [ ] Support writing preferences when user changes settings

**Settings to Support:**
- App version (for update checking)
- Prerelease channel preference
- Auto-update settings
- JVM arguments

**Key Files:**
- Native launcher source files (platform-specific)

### 2.2 Preferences Priority

Define clear precedence for settings.

**Priority (highest to lowest):**
1. External `preferences.xml` (user-configured at install time)
2. Embedded `app.xml` (from prebuilt app)

---

## 3. Installer (`installer/`)

The installer needs to detect and use prebuilt apps instead of building locally.

### 3.1 Prebuilt App Detection

Detect when prebuilt apps are available.

**Tasks:**
- [ ] Read `prebuiltApps` array from package.json
- [ ] Check if current platform is in the list
- [ ] Determine download URL based on:
  - Publish source (GitHub releases, npm, registry)
  - Naming convention: `{appname}-{version}-{platform}-bin.tgz`

**Key Files:**
- `installer/src/main/java/ca/weblite/jdeploy/installer/...`

### 3.2 Prebuilt App Download

Download and extract prebuilt apps.

**Tasks:**
- [ ] Implement download logic for prebuilt app tarball
- [ ] Verify checksum after download
- [ ] Extract tarball to installation directory
- [ ] Handle download failures with fallback

**Key Files:**
- `installer/src/main/java/ca/weblite/jdeploy/installer/...`

### 3.3 Fallback Behavior

Implement fallback to local building.

**Tasks:**
- [ ] If download fails, fall back to local app building (existing behavior)
- [ ] If app was signed, warn user that locally-built app will be unsigned
- [ ] Log reason for fallback (network error, checksum mismatch, etc.)

### 3.4 External Preferences Setup

Configure external preferences during installation.

**Tasks:**
- [ ] Create `~/.jdeploy/preferences/{fqn}/` directory
- [ ] Write initial `preferences.xml` with user-selected settings
- [ ] Include: prerelease preference, auto-update settings, etc.

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
- [ ] Display which platforms will have prebuilt apps (based on signing config)
- [ ] Show prebuilt app status after publish (success/failure per platform)

---

## Implementation Phases

### Phase 1: Core Infrastructure
1. PrebuiltAppRequirementService
2. Prebuilt app generation in bundler
3. Package.json embedding

### Phase 2: GitHub Publishing
1. GitHub upload integration
2. Integration tests

### Phase 3: Installer Support
1. Prebuilt app detection
2. Download and extraction
3. Fallback behavior

### Phase 4: Launcher Updates
1. External preferences loading
2. Preferences overlay logic

### Phase 5: Polish
1. Error handling and logging
2. Project editor status display
3. Documentation

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

## Next Steps

1. Team review of this plan
2. Estimate effort for each phase
3. Assign owners to components
4. Create detailed implementation tasks
5. Begin Phase 1 implementation
