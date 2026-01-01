# RFC: Unified CLI Bin Directory Structure

## Overview / Motivation

Currently, the jdeploy installer places CLI command wrappers in different locations depending on the platform:

- **macOS**: `~/bin` (see `MacCliCommandInstaller.getBinDir()`)
- **Linux**: `~/.local/bin` (see `AbstractUnixCliCommandInstaller.getBinDir()`)
- **Windows**: `~/.jdeploy/bin` (see `WindowsCliCommandInstaller.installCommands()`)

This platform-specific, shared directory approach creates several problems:

1. **Command name collisions**: All apps on a system share the same bin directory, so two different apps cannot both install a command named `cli` or `admin` without one overwriting the other.
2. **Complex uninstallation**: When uninstalling an app, the installer must track which commands belong to which app, making cleanup logic fragile and error-prone.
3. **No per-app PATH management**: The same bin directory is shared globally, so PATH cannot be managed independently per application. Adding or removing one app's PATH entry affects the entire directory.
4. **Platform inconsistency**: Different directory structures on each platform complicate documentation, testing, and cross-platform maintenance. Users get different experiences depending on their OS.
5. **Scalability**: As more apps are installed, a single shared bin directory becomes a bottleneck for command discovery and conflict resolution.

## Proposed Solution

Unify all platforms to install CLI commands in a unified, architecture-specific, per-app directory structure:

```
~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/
```

Where:

- `{arch}` is the architecture suffix from `ArchitectureUtil.getArchitectureSuffix()`, which returns either `-arm64` or `-x64` (empty string for legacy/single-arch systems if needed).
- `{fullyQualifiedPackageName}` is computed as follows:
  - If `source` is null or empty (NPM registry package): use `packageName` directly
  - If `source` is set (GitHub or custom source): use `MD5.getMd5(source) + "." + packageName`

This structure mirrors the existing architecture-specific package path pattern used in `PackagePathResolver` for consistency.

### Examples

**NPM package "myapp" on ARM64 macOS:**
```
~/.jdeploy/bin-arm64/myapp/
  myapp-cli
  myapp-admin
```

**GitHub-sourced package "myapp" from "https://github.com/user/myapp-repo" on x64 Linux:**
```
~/.jdeploy/bin-x64/a1b2c3d4e5f6.myapp/
  myapp-cli
  myapp-admin
```

Where `a1b2c3d4e5f6` is the first 12 characters of the MD5 hash of the source URL.

## Key Benefits

1. **Independent PATH management**: Each app's bin directory is isolated. On install, only that app's directory is added to PATH. On uninstall, only that specific directory is removed. Apps do not interfere with each other's PATH entries.

2. **Simplified uninstallation**: Since each app has its own directory, removal is trivial: delete `~/.jdeploy/bin-{arch}/{fqpn}/`. No need to track individual command files or parse shared bin directories.

3. **No command name collisions**: Each app has completely isolated commands. Two apps can both install a command named `cli` or `admin` without any conflict; they live in separate directories.

4. **Platform consistency**: macOS, Linux, and Windows all use the same directory structure, making documentation clearer, testing more uniform, and maintenance simpler. Users get a consistent experience across OSes.

5. **Architecture coexistence**: ARM64 and x64 versions of the same app can coexist side-by-side (e.g., `~/.jdeploy/bin-arm64/myapp/` and `~/.jdeploy/bin-x64/myapp/`), matching the existing package installation behavior. This is valuable on macOS with Rosetta 2 or in containerized/multi-architecture environments.

6. **Future extensibility**: The per-app directory structure makes it easier to add app-specific configuration, metadata, or additional tooling in the future without polluting a shared namespace.

## Implementation Changes

### Platform-Specific Behavior Changes

| Platform | Current Location | New Location | Impact |
|----------|-----------------|--------------|--------|
| macOS | `~/bin` | `~/.jdeploy/bin-{arch}/{fqpn}/` | Changed from home bin to .jdeploy; consistent with Linux |
| Linux | `~/.local/bin` | `~/.jdeploy/bin-{arch}/{fqpn}/` | Changed from .local/bin to .jdeploy; consistent with macOS |
| Windows | `%USERPROFILE%\.jdeploy\bin` | `%USERPROFILE%\.jdeploy\bin-{arch}\{fqpn}\` | Changed from shared bin to per-app bin |

### Code Changes Required

The following classes/methods must be modified:

1. **`AbstractUnixCliCommandInstaller.getBinDir(InstallationSettings settings)`**
   - Currently returns `~/.local/bin` by default
   - Must compute and return `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/` instead
   - Must obtain `packageName` and `source` from `InstallationSettings` or passed parameters

2. **`MacCliCommandInstaller.getBinDir(InstallationSettings settings)`**
   - Currently returns `~/bin` by default
   - Must compute and return `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/` instead
   - Aligns macOS behavior with Linux and Windows

3. **`WindowsCliCommandInstaller.installCommands(...)`**
   - Currently uses `~/.jdeploy/bin` as the shared bin directory
   - Must compute and use `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/` instead
   - PATH registry updates must target the per-app directory

4. **`InstallationSettings` class (if not already present)**
   - Must expose `packageName` field (obtained from bundle metadata)
   - Must expose `source` field (obtained from bundle metadata)
   - These fields enable `getBinDir()` implementations to compute the fully qualified package name

5. **New utility class: `CliCommandBinDirResolver`** (optional but recommended)
   - Centralize the computation of `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/`
   - Similar to how `PackagePathResolver` centralizes package path computation
   - Reduces code duplication across platform installers

### Fully Qualified Package Name Computation

Implement a helper method (either in a new `CliCommandBinDirResolver` or inline in each installer) to compute the fully qualified package name:

```java
public static String computeFullyQualifiedPackageName(String packageName, String source) {
    if (source == null || source.isEmpty()) {
        return packageName;
    } else {
        String sourceHash = MD5.getMd5(source);
        return sourceHash + "." + packageName;
    }
}
```

### PATH Management Changes

With the new per-app directory structure:

- **On install**: Add `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/` to PATH (once per app)
- **On uninstall**: Remove that specific directory from PATH (only affects that app)
- **Multiple apps**: Each app's directory is managed independently in PATH

This is simpler than the current approach where all commands in a shared bin directory must be tracked individually.

### Metadata and Tracking

The per-app bin directory itself serves as a natural boundary. The metadata file (`.jdeploy-cli.json`) should be stored in the app's installation directory (not in the bin directory), as is currently done:

- Linux/macOS: `~/.jdeploy/apps/{fullyQualifiedPackageName}/.jdeploy-cli.json`
- Windows: `%USERPROFILE%\.jdeploy\apps\{AppName}\.jdeploy-cli.json`

The metadata tracks:
- List of created wrapper files (relative names only)
- Whether PATH was updated
- The bin directory path (for uninstall)

## Backward Compatibility

### Existing Installations

Existing installations in old locations (`~/bin`, `~/.local/bin`, `~/.jdeploy/bin`) will continue to function. However, they are not automatically migrated.

**Migration Strategy:**
- When a user reinstalls or updates an app, the installer creates commands in the new location (`~/.jdeploy/bin-{arch}/{fqpn}/`)
- Old commands in legacy locations remain on disk but are not used (assuming the user's PATH is updated to prefer the new location)
- The uninstaller should check both old and new locations for cleanup

### Uninstall Behavior

The uninstaller should:
1. Remove commands from the new location (`~/.jdeploy/bin-{arch}/{fqpn}/`)
2. Check and remove commands from legacy locations if they still exist
3. Update PATH to remove entries from both old and new locations

This ensures backward compatibility while smoothly migrating to the new structure.

### API Compatibility

The `CliCommandInstaller` interface remains unchanged:
```java
List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings)
void uninstallCommands(File appDir)
boolean addToPath(File binDir)
```

Implementations simply change where `binDir` points.

## Relationship to Existing RFC

This RFC complements **`rfc/cli-commands-in-installer.md`**, which defines:
- The `jdeploy.commands` schema (command names, args, descriptions)
- Command validation rules (regex patterns, sanitization)
- Platform-specific script generation (shebang, quoting, escaping)
- Collision handling (same-app overwrite, different-app skip/overwrite)

The existing RFC addresses *what* commands are installed and *how* they are generated. This RFC addresses *where* they are installed and how to manage directories and PATH. The two RFCs are orthogonal.

**Collision Handling Simplification:**
With per-app directories, collision handling becomes simpler:
- **Same-app collisions**: Impossible by definition (each app has its own directory)
- **Different-app collisions**: Eliminated entirely (different apps use different directories)

The collision handler can be removed or simplified significantly.

## Implementation Approach

### Phase 1: Utility Class (Foundation)

Create a new `CliCommandBinDirResolver` utility class to centralize bin directory computation:

```java
package ca.weblite.jdeploy.installer.util;

public class CliCommandBinDirResolver {
    /**
     * Computes the fully qualified package name.
     * @param packageName the package name
     * @param source the source URL (null/empty for NPM registry packages)
     * @return the fully qualified package name
     */
    public static String computeFullyQualifiedPackageName(String packageName, String source) {
        if (source == null || source.isEmpty()) {
            return packageName;
        }
        String sourceHash = MD5.getMd5(source);
        return sourceHash + "." + packageName;
    }

    /**
     * Resolves the CLI bin directory for installing commands.
     * @param packageName the package name
     * @param source the source URL (null/empty for NPM registry packages)
     * @return the bin directory path
     */
    public static File getCliCommandBinDir(String packageName, String source) {
        String archSuffix = ArchitectureUtil.getArchitectureSuffix();
        String fqpn = computeFullyQualifiedPackageName(packageName, source);
        return new File(
            PackagePathResolver.getJDeployHome(),
            "bin" + archSuffix + File.separator + fqpn
        );
    }
}
```

### Phase 2: Update Platform Installers

Update `MacCliCommandInstaller`, `AbstractUnixCliCommandInstaller`, and `WindowsCliCommandInstaller` to use the new utility and compute bin directories based on `packageName` and `source` from `InstallationSettings`.

### Phase 3: Update InstallationSettings

Ensure `InstallationSettings` exposes `packageName` and `source` fields needed to compute the fully qualified package name.

### Phase 4: Update Uninstaller

Update uninstall logic to check both old and new locations and clean up accordingly.

## Security Considerations

The unified approach maintains the same security properties as the existing implementation:

1. **Directory permissions**: Each per-app bin directory is owned by the user, so no privilege escalation is possible.
2. **Command name validation**: Command names are still validated with the same regex pattern (`^[A-Za-z0-9._-]{1,255}$`), preventing path traversal or injection.
3. **Script generation**: Scripts continue to use the same safe quoting and escaping patterns (e.g., `exec` with `"$@"` on Unix, `%*` on Windows).
4. **No shared directory**: Eliminating the shared bin directory eliminates opportunities for one app to interfere with another's commands.

## Verification Checklist

| Criterion | Verification Method |
|-----------|---------------------|
| Commands installed in unified location | Check `~/.jdeploy/bin-{arch}/{fqpn}/` exists and contains wrapper scripts on all platforms |
| Architecture suffix applied correctly | Verify `-arm64` or `-x64` suffix appears in path; test on both architectures if possible |
| Fully qualified name computed correctly | NPM packages use name only; GitHub packages use `MD5.source.name` format |
| PATH entries are app-specific | Each app's directory is added/removed independently; verify with `echo $PATH` or registry inspection |
| Uninstall cleans up correctly | App's bin directory is removed; verify with `ls ~/.jdeploy/bin-{arch}/` |
| Old installations still work | Legacy paths (`~/bin`, `~/.local/bin`, `~/.jdeploy/bin`) still function if not deleted |
| Metadata file correctly references bin dir | `.jdeploy-cli.json` contains correct `binDir` path |
| No command collisions across apps | Install two apps with same command name; both work independently |
| PATH restoration on uninstall | Uninstall removes only the app's PATH entry; other apps' entries remain |
| Multiple architectures coexist | Both `bin-arm64` and `bin-x64` directories can exist for same app |

## Recommended Test Cases

1. **Single app installation**: Install app with multiple commands; verify all are in `~/.jdeploy/bin-{arch}/{fqpn}/`
2. **Multiple app installation**: Install two apps; verify each has separate directories with no conflicts
3. **Architecture detection**: Run on both ARM64 and x64 systems (or use mocking); verify correct suffix applied
4. **PATH management**: Verify PATH is updated for each app and restored on uninstall
5. **Backward compatibility**: Install old version, then upgrade; verify commands work from both old and new locations during transition
6. **Uninstall cleanup**: Uninstall app; verify bin directory is removed and PATH is updated
7. **Partial uninstall**: Remove some files manually; uninstall should handle gracefully
8. **GitHub vs NPM packages**: Install both types; verify FQPN computed correctly for each
9. **Metadata persistence**: Verify metadata file tracks correct bin directory and can be read on uninstall

## Summary / Recommendation

- Introduce a unified, architecture-specific, per-app CLI command bin directory structure: `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/`
- Apply this structure consistently across macOS, Linux, and Windows
- Implement a utility class (`CliCommandBinDirResolver`) to centralize bin directory computation
- Update all platform-specific installers to use the new structure
- Maintain backward compatibility by checking legacy locations during uninstall
- Simplify collision handling since each app has its own directory
- Improve testability and maintainability through consistent directory structure

This change improves user experience (no command conflicts), simplifies installer logic (no collision detection needed), and provides a more scalable foundation for future enhancements.

## Implementation Roadmap

If this RFC is accepted, the implementation sequence should be:

1. Create `CliCommandBinDirResolver` utility class
2. Update `InstallationSettings` to expose `packageName` and `source` (if not already present)
3. Update `AbstractUnixCliCommandInstaller.getBinDir()` to use new utility
4. Update `MacCliCommandInstaller.getBinDir()` to use new utility
5. Update `WindowsCliCommandInstaller.installCommands()` to use new utility
6. Update uninstall logic in all installers to check both old and new locations
7. Add/update tests for all platform-specific installers
8. Document migration path in installer help/UI
9. Update user-facing documentation with new paths

## Appendix: Comparison with Existing Package Path Structure

This RFC mirrors the existing `PackagePathResolver` structure for packages:

- **Packages**: `~/.jdeploy/packages-{arch}/{packageName}` (NPM) or `~/.jdeploy/gh-packages-{arch}/{fqpn}` (GitHub)
- **CLI Bins**: `~/.jdeploy/bin-{arch}/{fqpn}` (unified)

Both:
- Use `-{arch}` suffix for architecture-specific isolation
- Use fully qualified package names (with MD5 hash for non-NPM sources)
- Provide clean boundaries for install/uninstall operations
- Support coexistence of multiple architectures

This consistency is a key design principle of this RFC.
