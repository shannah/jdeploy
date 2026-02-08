# RFC: Prebuilt Apps Publishing

**Status**: Decisions Complete - Ready for Implementation Planning
**Author**: [Team]
**Created**: 2026-02-07

## Problem Statement

Currently, jDeploy's publishing workflow handles:

- **GitHub Publishing**: Uploads installers to GitHub releases, but not the native app binaries themselves
- **NPM Publishing**: Uploads only the tarball containing the Java application; the jdeploy.com web service builds installers on-demand

The native app (the platform-specific executable wrapper + embedded Java app) is always generated at install time by the installer. This approach has limitations:

1. **Cannot sign executables** - Signing requires the exe to exist before distribution
2. **Installer must have bundling logic** - More complexity in the installer
3. **No pre-built apps available** - Users can't download standalone native apps
4. **Repeated work** - Every installation rebuilds the same native app

## Proposed Solution

Add support for publishing **prebuilt native apps** to:
1. **GitHub Releases** - Alongside existing installer uploads
2. **jdeploy.com Registry** - New asset type for native app binaries

This feature is **automatically enabled** when other features require it (such as Windows exe signing). It serves as infrastructure that other features depend on, rather than a standalone user-configured option.

## Scope

### In Scope (Initial Implementation)
- Publishing prebuilt native apps for supported platforms:
  - Windows x64
  - Windows ARM64
  - macOS x64
  - macOS ARM64
  - Linux x64
  - Linux ARM64
- Publishing to GitHub releases
- Installer changes to download and use prebuilt apps
- Automatic enablement when dependent features require it

### Future Scope
- Publishing to jdeploy.com registry (requires paid membership model)

### Out of Scope (Handled by Other RFCs)
- Code signing of executables (see `windows-signed-exe-support.md`)
- Platform-specific bundle content optimization (see `platform-specific-bundles.md`)

---

## Decisions Made

### Decision 1: Bundle Format

**Decision**: Platform-specific tarballs (`.tgz` format)

---

### Decision 2: Naming Convention

**Decision**: Use `-bin` suffix to denote binary/platform-specific assets

Pattern: `{appname}-{version}-{platform}-bin.tgz`

Examples:
```
myapp-1.0.0-win-x64-bin.tgz
myapp-1.0.0-win-arm64-bin.tgz
myapp-1.0.0-mac-x64-bin.tgz
myapp-1.0.0-mac-arm64-bin.tgz
```

---

### Decision 3: No User Configuration

**Decision**: Prebuilt apps are purely internal infrastructure with no user-facing configuration.

- No `publishBundles` or `prebuiltApps` setting in package.json
- jDeploy determines internally when prebuilt apps are needed via `PrebuiltAppRequirementService`
- URLs are embedded into package.json at publish time (like `iconHash`)

---

### Decision 4: Installer Detection

**Decision**: Installer reads `prebuiltApps` platform list from package.json

The `prebuiltApps` property contains a list of platforms that have prebuilt apps available. The installer determines the download URL based on the publish source and naming convention.

Example:
```json
{
  "jdeploy": {
    "prebuiltApps": ["win-x64", "win-arm64"]
  }
}
```

The installer constructs the URL using:
- The publish source (GitHub releases, npm registry, jdeploy.com)
- The naming convention: `{appname}-{version}-{platform}-bin.tgz`

---

### Decision 5: Which Platforms to Publish

**Decision**: Only platforms required by features

- If Windows signing enabled → only Windows platforms (win-x64, win-arm64)
- If macOS signing enabled → only macOS platforms (mac-x64, mac-arm64)
- Minimal storage/bandwidth usage

---

### Decision 6: GitHub Release Integration

**Decision**: Same release as installers

Prebuilt apps are uploaded to the same GitHub release as installers. They appear alongside installers when generated, consistent with existing workflow.

---

### Decision 7: jdeploy.com Registry Storage

**Decision**: New asset type (future work)

- API: `POST /api/apps/{appId}/prebuilt/{platform}/{version}`
- Dedicated storage and retrieval endpoints
- Similar pattern to how icons are stored
- **Note**: Initial implementation focuses on GitHub releases only. Registry support requires paid membership model to cover costs.

---

### Decision 8: Fallback Behavior

**Decision**: Always fallback to building

If prebuilt app download fails, build the native app locally. This provides maximum compatibility and resilience to network failures.

**Note**: For signed apps, this fallback means the locally-built app will be unsigned. The installer should warn the user in this case.

---

### Decision 9: External Settings Storage

**Decision**: Store under `~/.jdeploy/preferences/{fqn}/preferences.xml`

Since prebuilt apps cannot be modified after creation, user-configurable settings are stored externally.

**Location**: `~/.jdeploy/preferences/{fqn}/preferences.xml`

Where `{fqn}` (fully qualified name) is:
- With source (e.g., npm): `{hash}.{package-name}`
- Without source: `{package-name}`

**Examples**:
```
~/.jdeploy/preferences/abc123.my-app/preferences.xml
~/.jdeploy/preferences/my-app/preferences.xml
```

**Settings Stored**:
- App version (for update checking)
- Prerelease channel preference
- Auto-update settings
- JVM arguments (if user-configurable)

**Launcher Changes Required**:
- Load `preferences.xml` from this location if present
- Overlay settings on embedded app.xml values
- Handle missing/corrupted settings gracefully
- Create directory structure if needed when saving preferences

---

## Architecture

### Internal Service (No User Configuration)

There is **no user-facing configuration** for prebuilt apps. This is purely internal infrastructure.

jDeploy will include a `PrebuiltAppService` (or similar) that determines at publish time whether prebuilt apps are needed:

```java
public interface PrebuiltAppRequirementService {
    /**
     * Returns true if prebuilt apps should be published for the given platform.
     * Decision is based on other project settings (e.g., signing enabled).
     */
    boolean requiresPrebuiltApp(JDeployProject project, Platform platform);

    /**
     * Returns all platforms that require prebuilt apps for this project.
     */
    List<Platform> getRequiredPlatforms(JDeployProject project);
}
```

**Features that trigger prebuilt apps:**
- `windowsSigning.enabled = true` → requires prebuilt apps for Windows platforms
- (Future) macOS code signing / notarization → requires prebuilt apps for macOS platforms
- (Future) Other features requiring pre-processing of native apps

### Publish-Time Embedding

At publish time, when prebuilt apps are uploaded, jDeploy embeds the platform list into `package.json` automatically (similar to how icon hash is embedded):

```json
{
  "jdeploy": {
    "jar": "target/myapp.jar",
    "windowsSigning": {
      "enabled": true
    },
    "prebuiltApps": ["win-x64", "win-arm64"]
  }
}
```

**Important**: The `prebuiltApps` property is:
- Written by jDeploy at publish time only
- Never manually edited by users
- Contains only the list of platforms with prebuilt apps available
- Installer determines download URL based on publish source and naming convention

### Naming Convention

Prebuilt apps use the naming pattern: `{appname}-{version}-{platform}-bin.tgz`

Examples:
```
myapp-1.0.0-win-x64-bin.tgz
myapp-1.0.0-win-arm64-bin.tgz
myapp-1.0.0-mac-x64-bin.tgz
myapp-1.0.0-mac-arm64-bin.tgz
```

---

## Work Streams

### Work Stream 1: PrebuiltAppRequirementService
- Create service interface to determine if prebuilt apps are needed
- Implement checks for Windows signing, future macOS signing, etc.
- Return required platforms based on project configuration

### Work Stream 2: Prebuilt App Generation
- Ensure bundler can generate standalone native apps for all platforms
- Output as `.tgz` files with `-bin` suffix naming
- Generate checksums for integrity verification

### Work Stream 3: GitHub Publishing Integration
- Modify `GitHubPublishDriver` to upload prebuilt apps when required
- Implement platform-specific naming convention
- Embed URLs into package.json after upload
- Handle upload failures gracefully

### Work Stream 4: Registry Publishing Integration (Future)
- Define API for prebuilt app upload to jdeploy.com
- Implement storage and retrieval
- Requires paid membership model
- **Deferred**: Initial implementation focuses on GitHub releases only

### Work Stream 5: Package.json Embedding
- Write `prebuiltApps` platform list to package.json at publish time
- Similar pattern to existing `iconHash` embedding

### Work Stream 6: Installer Updates
- Read `prebuiltApps` platform list from package.json
- Construct download URL based on publish source and naming convention
- Download and extract prebuilt apps when platform is listed
- Fallback to local build if download fails (warn user if app was signed)
- Verify integrity (checksums from separate mechanism if needed)

### Work Stream 7: External Settings Support
- Implement `~/.jdeploy/preferences/{fqn}/preferences.xml` storage
- Implement settings overlay in launcher
- Store user-configured settings (prerelease, auto-update, etc.) externally
- Handle missing/corrupted settings gracefully

---

## Interaction with Other Features

### Windows Signed Exe Support
This feature provides the infrastructure for publishing signed Windows executables. The signing RFC depends on this feature for:
- Storage and distribution of signed apps
- Installer retrieval of prebuilt (signed) apps

When `windowsSigning.enabled = true`, prebuilt apps are automatically enabled for Windows platforms.

### Platform-Specific Bundles
If platform-specific bundles (with native library filtering) are enabled, the published prebuilt apps would use the filtered versions, not universal bundles.

### Future: macOS Signing / Notarization
When macOS code signing is implemented, it would similarly trigger automatic enablement of prebuilt apps for macOS platforms.

---

## Benefits

1. **Enables Signing** - Prebuilt apps can be signed before distribution
2. **Faster Installation** - No need to build native app at install time
3. **Consistency** - All users get identical native app
4. **Direct Downloads** - Users can download native apps directly from releases
5. **Future Features** - Foundation for other pre-processing of native apps
6. **Transparent to Users** - Automatically enabled when needed, no configuration required

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| Increased storage costs | Only enabled when features require it |
| Longer publish times | Parallel generation, only build required platforms |
| Version mismatch | Include version in prebuilt app, verify on install |
| Network failures during install | Always fallback to local build (warn if signed app becomes unsigned) |

---

## Constraints & Scope

### Prebuilt App Contents
The prebuilt app should be essentially the same as the dynamically built app. Composition details (JVM embedding, etc.) are outside the scope of this RFC.

### Size Limits
Prebuilt apps are currently just the launcher, so they should be under 20MB. This keeps storage and bandwidth costs manageable.

### Registry Publishing Requirements
Publishing prebuilt apps to the jdeploy.com registry:
- Requires a jDeploy API key
- Future: Will likely require a paid jDeploy membership to cover storage/bandwidth costs

**For initial implementation**: Focus on GitHub releases only, since publishing there doesn't incur costs for jDeploy. Registry support can be added later with appropriate subscription model.

### Not for Direct Download
Prebuilt apps are internal infrastructure for the installer only. They are not intended to be downloaded directly by end users.

### Checksums
Checksums are handled the same way as for icons and splash images.

---

## Next Steps

1. Resolve open questions above
2. Coordinate with Windows Signed Exe Support RFC
3. Create detailed implementation plan
4. Prioritize work streams
5. Begin implementation
