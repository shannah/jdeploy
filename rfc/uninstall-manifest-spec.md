# RFC: Uninstall Manifest Specification

## Status
Proposed

## Author
jDeploy Team

## Overview / Motivation

Currently, the jDeploy installer creates various artifacts during installation (files, directories, registry entries, PATH modifications) but lacks a unified manifest for clean uninstallation. This makes it difficult to:

1. Track what was actually installed
2. Remove artifacts created by the installer without leaving behind orphaned files
3. Restore registry values that were modified (not just deleted)
4. Clean up PATH modifications across different platforms and shell configurations
5. Provide users with transparency about what will be removed during uninstall

The existing approaches are fragmented:
- **CliCommandManifest** (`FileCliCommandManifestRepository`): JSON-based manifest for CLI commands only
- **InstallWindowsRegistry**: Backup log in Windows `.reg` format for registry rollback
- **UninstallWindows**: Ad-hoc uninstall logic that doesn't track all created artifacts

This RFC proposes a unified XML-based **Uninstall Manifest** format that consolidates all installation artifacts into a single, machine-readable, platform-agnostic specification.

## Goals

1. **Remove files created during installation**: Track and remove all binaries, scripts, icons, and metadata files
2. **Clean up directories**: Remove installer-created directories while respecting runtime-created content (deepest-first cleanup)
3. **Restore Windows registry**: Remove created keys and restore modified values to their previous state
4. **Remove PATH modifications**: Track and reverse PATH changes across Windows registry, shell profiles, and Git Bash configurations
5. **Support cross-platform uninstallation**: Single manifest format works on Windows, macOS, and Linux
6. **Provide auditability**: Log all uninstall actions for debugging and user transparency

## Non-Goals

- Supporting system-wide (administrator) installations
- Tracking user-created content (application data, configuration, documents)
- Partial uninstallation (all-or-nothing approach for simplicity and safety)

---

## Manifest Location and Directory Structure

### Location

Uninstall manifests are stored at:

```
~/.jdeploy/manifests/{arch}/{fullyQualifiedPackageName}/uninstall-manifest.xml
```

Where:
- `{arch}` is the system architecture: `x64` or `arm64`
- `{fullyQualifiedPackageName}` is computed as follows:
  - **NPM packages** (source is null/empty): use `packageName` directly (e.g., `myapp`)
  - **GitHub packages** (source provided): use `{MD5(source)}.{packageName}` (e.g., `356a192b7913b04c54574d18c28d46e6.myapp`)

This matches the directory structure used by `CliCommandBinDirResolver` and `FileCliCommandManifestRepository`.

### Example Paths

**macOS ARM64 (NPM package):**
```
~/.jdeploy/manifests/arm64/myapp/uninstall-manifest.xml
```

**Windows x64 (GitHub package):**
```
C:\Users\alice\.jdeploy\manifests\x64\356a192b7913b04c54574d18c28d46e6.myapp\uninstall-manifest.xml
```

---

## Manifest XML Structure

### Root Element: `<uninstallManifest>`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<uninstallManifest version="1.0" xmlns="http://jdeploy.ca/uninstall-manifest/1.0">
    <packageInfo>...</packageInfo>
    <files>...</files>
    <directories>...</directories>
    <registry>...</registry>
    <pathModifications>...</pathModifications>
</uninstallManifest>
```

### 1. Package Info Section

Records metadata about the installed package.

```xml
<packageInfo>
    <name>myapp</name>
    <source>https://github.com/user/myapp</source>
    <version>1.2.3</version>
    <fullyQualifiedName>356a192b7913b04c54574d18c28d46e6.myapp</fullyQualifiedName>
    <architecture>x64</architecture>
    <installedAt>2024-01-15T10:30:45Z</installedAt>
    <installerVersion>2.1.0</installerVersion>
</packageInfo>
```

**Fields:**

- `name` (string, required): The package name as defined in `package.json`
- `source` (string, optional): GitHub repository URL, null/empty for NPM packages
- `version` (string, required): The installed version (semantic version)
- `fullyQualifiedName` (string, required): The computed FQPN for this package
- `architecture` (string, required): One of `x64` or `arm64`
- `installedAt` (dateTime, required): ISO 8601 timestamp when the package was installed
- `installerVersion` (string, required): Version of the jDeploy installer that created this manifest

### 2. Files Section

Tracks individual files created during installation.

```xml
<files>
    <file>
        <path>${APP_DIR}/myapp.exe</path>
        <type>binary</type>
        <description>Main application executable</description>
    </file>
    <file>
        <path>${JDEPLOY_HOME}/apps/myapp/runtime.jar</path>
        <type>binary</type>
        <description>Packaged Java runtime library</description>
    </file>
    <file>
        <path>${JDEPLOY_HOME}/apps/myapp/app.properties</path>
        <type>config</type>
        <description>Application configuration file</description>
    </file>
    <file>
        <path>${USER_HOME}/Desktop/MyApp.lnk</path>
        <type>link</type>
        <description>Desktop shortcut</description>
    </file>
    <file>
        <path>${JDEPLOY_HOME}/bin-x64/myapp/myapp-cli</path>
        <type>script</type>
        <description>CLI command wrapper script</description>
    </file>
</files>
```

**File Attributes:**

- `path` (string, required): Absolute path to the file. May contain variable references like `${USER_HOME}`, `${JDEPLOY_HOME}`, or `${APP_DIR}`
- `type` (string, required): One of:
  - `binary`: Executable files, libraries, application binaries
  - `script`: Shell scripts, batch files, command wrappers
  - `link`: Symbolic links, shortcuts, desktop links
  - `config`: Configuration files, INI, properties
  - `icon`: Image files used for icons
  - `metadata`: Manifest files, backup logs, other metadata
- `description` (string, optional): Human-readable description of the file's purpose

**Processing:**
- Files are removed in the order listed (typically unrelated order)
- If a file doesn't exist, skip it without error (idempotent)
- Remove individual files before cleaning up directories

### 3. Directories Section

Tracks directories created during installation. Order is important: **list directories in reverse depth order (deepest first)** to avoid conflicts when cleaning.

```xml
<directories>
    <directory>
        <path>${JDEPLOY_HOME}/bin-x64/myapp</path>
        <cleanup>always</cleanup>
        <description>CLI command wrappers directory</description>
    </directory>
    <directory>
        <path>${JDEPLOY_HOME}/apps/myapp</path>
        <cleanup>always</cleanup>
        <description>Installed application directory</description>
    </directory>
    <directory>
        <path>${JDEPLOY_HOME}/apps</path>
        <cleanup>ifEmpty</cleanup>
        <description>Applications directory (parent)</description>
    </directory>
    <directory>
        <path>${JDEPLOY_HOME}/bin-x64</path>
        <cleanup>ifEmpty</cleanup>
        <description>CLI binaries directory (architecture-specific parent)</description>
    </directory>
</directories>
```

**Directory Attributes:**

- `path` (string, required): Absolute path to the directory. May contain variable references.
- `cleanup` (string, required): One of:
  - `always`: Delete the directory and all its contents unconditionally. Used for installer-created directories.
  - `ifEmpty`: Delete only if the directory is empty after file removal. Used for parent directories that may contain other apps' files.
  - `contentsOnly`: Delete all contents recursively but keep the directory itself. Used for directories that should exist but be empty.
- `description` (string, optional): Human-readable description

**Processing:**
- Process directories in the order listed (which should be deepest-first for safety)
- For `always`: delete directory unconditionally
- For `ifEmpty`: delete directory only if empty; log a warning if it's not empty
- For `contentsOnly`: recursively delete all contents but don't delete the directory itself
- Skip non-existent directories without error

### 4. Registry Section (Windows Only)

Tracks Windows registry modifications. This section is empty/omitted on non-Windows platforms.

```xml
<registry>
    <createdKeys>
        <createdKey>
            <root>HKEY_CURRENT_USER</root>
            <path>Software\jdeploy\myapp</path>
            <description>Application registry key</description>
        </createdKey>
        <createdKey>
            <root>HKEY_CURRENT_USER</root>
            <path>Software\Classes\myapp.file</path>
            <description>File association prog ID</description>
        </createdKey>
    </createdKeys>
    <modifiedValues>
        <modifiedValue>
            <root>HKEY_CURRENT_USER</root>
            <path>Environment</path>
            <name>Path</name>
            <previousValue>C:\Windows\System32</previousValue>
            <previousType>REG_EXPAND_SZ</previousType>
            <description>User PATH environment variable</description>
        </modifiedValue>
        <modifiedValue>
            <root>HKEY_CURRENT_USER</root>
            <path>Software\RegisteredApplications</path>
            <name>myapp</name>
            <previousValue></previousValue>
            <previousType>REG_SZ</previousType>
            <description>Registered application entry (empty = didn't exist before)</description>
        </modifiedValue>
    </modifiedValues>
</registry>
```

**Created Key Attributes:**

- `root` (string, required): Registry root: `HKEY_CURRENT_USER` or `HKEY_LOCAL_MACHINE`
- `path` (string, required): Full registry key path (without root prefix), e.g., `Software\jdeploy\myapp`
- `description` (string, optional): Description of the key

**Action on uninstall:** Delete the entire key tree recursively.

**Modified Value Attributes:**

- `root` (string, required): Registry root: `HKEY_CURRENT_USER` or `HKEY_LOCAL_MACHINE`
- `path` (string, required): Registry key path (without root prefix)
- `name` (string, required): Value name. Use empty string for the default value (unnamed entry)
- `previousValue` (string, optional): The original value before modification. If empty or omitted, the value didn't exist before.
- `previousType` (string, required): Registry value type: `REG_SZ`, `REG_EXPAND_SZ`, `REG_DWORD`, `REG_QWORD`, `REG_BINARY`, `REG_MULTI_SZ`
- `description` (string, optional): Description of the value

**Action on uninstall:**
- If `previousValue` is present and non-empty: restore to the previous value
- If `previousValue` is empty or omitted: delete the value (it didn't exist before)

**Platform Note:** This section applies to Windows only. On macOS and Linux, it should be empty or omitted.

### 5. Path Modifications Section

Tracks PATH modifications across different platforms and shells.

```xml
<pathModifications>
    <windowsPaths>
        <windowsPath>
            <addedEntry>C:\Users\alice\.jdeploy\bin-x64\myapp</addedEntry>
            <description>CLI commands directory added to user PATH</description>
        </windowsPath>
    </windowsPaths>
    <shellProfiles>
        <shellProfile>
            <file>${USER_HOME}/.bashrc</file>
            <exportLine>export PATH="${PATH}:/home/alice/.jdeploy/bin-x64/myapp"</exportLine>
            <description>Added PATH entry for CLI commands (Bash)</description>
        </shellProfile>
        <shellProfile>
            <file>${USER_HOME}/.zprofile</file>
            <exportLine>export PATH="${PATH}:/Users/alice/.jdeploy/bin-arm64/myapp"</exportLine>
            <description>Added PATH entry for CLI commands (Zsh)</description>
        </shellProfile>
    </shellProfiles>
    <gitBashProfiles>
        <gitBashProfile>
            <file>${USER_HOME}/.bash_profile</file>
            <exportLine>export PATH="${PATH}:/c/Users/alice/.jdeploy/bin-x64/myapp"</exportLine>
            <description>Added PATH entry for CLI commands (Git Bash)</description>
        </gitBashProfile>
    </gitBashProfiles>
</pathModifications>
```

#### Windows Registry PATH (`<windowsPath>`)

- `addedEntry` (string, required): The absolute path that was added to `HKCU\Environment\Path`

**Action on uninstall:** Remove this entry from `HKCU\Environment\Path`. If the PATH becomes empty, delete the value.

**Platform Note:** Windows only.

#### Unix Shell Profile (`<shellProfile>`)

- `file` (string, required): Path to the shell configuration file (e.g., `~/.bashrc`, `~/.zprofile`). May contain variable references.
- `exportLine` (string, required): The exact line that was added to the file (for removal). Should be the full export statement as it appears in the file.

**Action on uninstall:** Find and remove this exact line from the specified file. If the file becomes empty after removal, optionally delete it.

**Platform Note:** Unix/Linux/macOS only. Typically `~/.bashrc`, `~/.bash_profile`, `~/.zprofile`, `~/.profile`.

#### Git Bash Profile (`<gitBashProfile>`)

- `file` (string, required): Path to Git Bash configuration file (typically `~/.bash_profile` or `~/.bashrc`). May contain variable references.
- `exportLine` (string, required): The exact line that was added (for removal).

**Action on uninstall:** Find and remove this exact line from the specified file.

**Platform Note:** Windows only (Git Bash / MSYS2). Paths in `exportLine` should use MSYS2 format (`/c/Users/...`).

---

## Variable Expansion

Paths in the manifest may contain platform-agnostic variable references that are expanded at uninstall time:

- `${USER_HOME}`: User's home directory (expanduser `~`)
  - Windows: `C:\Users\alice`
  - Unix/macOS: `/home/alice` or `/Users/alice`

- `${JDEPLOY_HOME}`: The `.jdeploy` directory in the user's home
  - Windows: `C:\Users\alice\.jdeploy`
  - Unix/macOS: `/home/alice/.jdeploy`

- `${APP_DIR}`: The installed application directory
  - Windows: `C:\Users\alice\.jdeploy\apps\myapp`
  - Unix/macOS: `~/.jdeploy/apps/myapp`

**Benefits:**
- Platform-agnostic manifest format
- Paths are human-readable in the manifest
- Prevents hardcoded absolute paths that become stale if user home moves

**Expansion Rules:**
1. All variables use `${VARNAME}` syntax
2. Variables are expanded at uninstall time (not install time)
3. If a variable cannot be expanded, log a warning and skip that entry
4. Nested variables are not supported (e.g., `${JDEPLOY_HOME}/${USER_HOME}` is invalid)

---

## Processing Order

The uninstaller should process the manifest in this order:

1. **Remove files** (in listed order)
   - Skip non-existent files without error
   - Log each removal

2. **Clean up directories** (in listed order, which should be deepest-first)
   - Apply the appropriate cleanup strategy for each directory
   - Log successes and skip non-existent directories without error

3. **Restore/remove registry entries** (Windows only)
   - Process created keys first (delete recursively)
   - Then process modified values (restore or delete)
   - Continue on individual failures

4. **Remove PATH modifications** (cross-platform)
   - Windows: Update `HKCU\Environment\Path` registry value
   - Unix/macOS: Remove lines from shell profiles
   - Git Bash: Remove lines from Git Bash configuration
   - Continue on individual failures

5. **Delete the manifest file** itself
   - Remove the uninstall manifest XML file
   - Attempt to remove parent manifest directories if empty

---

## Complete Example Manifest

A typical Windows installation with GUI app and CLI commands:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<uninstallManifest version="1.0" xmlns="http://jdeploy.ca/uninstall-manifest/1.0">
    <packageInfo>
        <name>myapp</name>
        <source>https://github.com/user/myapp-repo</source>
        <version>1.5.2</version>
        <fullyQualifiedName>356a192b7913b04c54574d18c28d46e6.myapp</fullyQualifiedName>
        <architecture>x64</architecture>
        <installedAt>2024-01-15T10:30:45Z</installedAt>
        <installerVersion>2.1.0</installerVersion>
    </packageInfo>

    <files>
        <file>
            <path>${JDEPLOY_HOME}/apps/myapp/myapp.exe</path>
            <type>binary</type>
            <description>Main application executable</description>
        </file>
        <file>
            <path>${JDEPLOY_HOME}/apps/myapp/runtime.jar</path>
            <type>binary</type>
            <description>Packaged Java runtime library</description>
        </file>
        <file>
            <path>${JDEPLOY_HOME}/apps/myapp/myapp-cli.exe</path>
            <type>binary</type>
            <description>CLI launcher executable</description>
        </file>
        <file>
            <path>${JDEPLOY_HOME}/apps/myapp/myapp.properties</path>
            <type>config</type>
            <description>Application configuration</description>
        </file>
        <file>
            <path>${JDEPLOY_HOME}/uninstallers/356a192b7913b04c54574d18c28d46e6.myapp/myapp-uninstall.exe</path>
            <type>binary</type>
            <description>Uninstaller executable</description>
        </file>
        <file>
            <path>${USER_HOME}/Desktop/MyApp.lnk</path>
            <type>link</type>
            <description>Desktop shortcut</description>
        </file>
        <file>
            <path>${USER_HOME}/AppData/Roaming/Microsoft/Windows/Start Menu/Programs/MyApp.lnk</path>
            <type>link</type>
            <description>Start menu shortcut</description>
        </file>
        <file>
            <path>${JDEPLOY_HOME}/bin-x64/356a192b7913b04c54574d18c28d46e6.myapp/myapp-cli.cmd</path>
            <type>script</type>
            <description>CLI command wrapper (CMD)</description>
        </file>
        <file>
            <path>${JDEPLOY_HOME}/bin-x64/356a192b7913b04c54574d18c28d46e6.myapp/myapp-cli</path>
            <type>script</type>
            <description>CLI command wrapper (Git Bash)</description>
        </file>
        <file>
            <path>${JDEPLOY_HOME}/manifests/x64/356a192b7913b04c54574d18c28d46e6.myapp/.registry-backup.reg</path>
            <type>metadata</type>
            <description>Windows registry backup log</description>
        </file>
    </files>

    <directories>
        <directory>
            <path>${JDEPLOY_HOME}/bin-x64/356a192b7913b04c54574d18c28d46e6.myapp</path>
            <cleanup>always</cleanup>
            <description>CLI commands directory for this app</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/uninstallers/356a192b7913b04c54574d18c28d46e6.myapp</path>
            <cleanup>always</cleanup>
            <description>Uninstaller directory for this app</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/apps/myapp</path>
            <cleanup>always</cleanup>
            <description>Application installation directory</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/manifests/x64/356a192b7913b04c54574d18c28d46e6.myapp</path>
            <cleanup>always</cleanup>
            <description>Manifest directory for this app</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/bin-x64</path>
            <cleanup>ifEmpty</cleanup>
            <description>Architecture-specific CLI directory (parent)</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/apps</path>
            <cleanup>ifEmpty</cleanup>
            <description>Applications directory (parent)</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/uninstallers</path>
            <cleanup>ifEmpty</cleanup>
            <description>Uninstallers directory (parent)</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/manifests/x64</path>
            <cleanup>ifEmpty</cleanup>
            <description>Manifests directory for x64 (parent)</description>
        </directory>
        <directory>
            <path>${JDEPLOY_HOME}/manifests</path>
            <cleanup>ifEmpty</cleanup>
            <description>Manifests directory (parent)</description>
        </directory>
    </directories>

    <registry>
        <createdKeys>
            <createdKey>
                <root>HKEY_CURRENT_USER</root>
                <path>Software\jdeploy\356a192b7913b04c54574d18c28d46e6.myapp</path>
                <description>Application capabilities registry key</description>
            </createdKey>
            <createdKey>
                <root>HKEY_CURRENT_USER</root>
                <path>Software\Classes\356a192b7913b04c54574d18c28d46e6.myapp.file</path>
                <description>File association prog ID</description>
            </createdKey>
            <createdKey>
                <root>HKEY_CURRENT_USER</root>
                <path>Software\Classes\.myappfile</path>
                <description>File extension association</description>
            </createdKey>
            <createdKey>
                <root>HKEY_CURRENT_USER</root>
                <path>Software\Microsoft\Windows\CurrentVersion\Uninstall\jdeploy.356a192b7913b04c54574d18c28d46e6.myapp</path>
                <description>Uninstall registry entry</description>
            </createdKey>
        </createdKeys>
        <modifiedValues>
            <modifiedValue>
                <root>HKEY_CURRENT_USER</root>
                <path>Environment</path>
                <name>Path</name>
                <previousValue>C:\Windows\System32;C:\Program Files\Git\cmd</previousValue>
                <previousType>REG_EXPAND_SZ</previousType>
                <description>User PATH environment variable</description>
            </modifiedValue>
        </modifiedValues>
    </registry>

    <pathModifications>
        <windowsPaths>
            <windowsPath>
                <addedEntry>C:\Users\alice\.jdeploy\bin-x64\356a192b7913b04c54574d18c28d46e6.myapp</addedEntry>
                <description>CLI commands directory</description>
            </windowsPath>
        </windowsPaths>
        <gitBashProfiles>
            <gitBashProfile>
                <file>${USER_HOME}/.bash_profile</file>
                <exportLine>export PATH="${PATH}:/c/Users/alice/.jdeploy/bin-x64/356a192b7913b04c54574d18c28d46e6.myapp"</exportLine>
                <description>Git Bash PATH configuration</description>
            </gitBashProfile>
        </gitBashProfiles>
    </pathModifications>
</uninstallManifest>
```

---

## Verification and Validation

Implementations must:

1. **Validate against XSD schema**
   - Use an XML parser with XSD validation to ensure the manifest conforms to the schema
   - Reject invalid manifests and log the validation errors

2. **Log all actions**
   - Log each file removal, directory cleanup, registry operation, and PATH modification
   - Include timestamps and status (success/skip/warning/error) for each action
   - Maintain a detailed uninstall log for debugging and auditing

3. **Continue on individual failures**
   - Do not abort the entire uninstall if a single file or directory removal fails
   - Continue processing remaining entries and report all failures at the end
   - This maximizes cleanup even if some entries cannot be removed

4. **Report summary**
   - After uninstall completes, display a summary:
     - Number of files removed
     - Number of directories cleaned up
     - Number of registry entries processed
     - Number of PATH modifications reversed
     - Number of failures/warnings (if any)

---

## Security Considerations

### Path Injection Prevention

- **Use variable expansion**: Manifest paths use `${USER_HOME}`, `${JDEPLOY_HOME}`, `${APP_DIR}` to prevent arbitrary path injection
- **Validate paths**: Before removal, verify that expanded paths are within expected locations:
  - Files should be under `${JDEPLOY_HOME}` or recognized user directories (Desktop, Documents)
  - Directories should be under `${JDEPLOY_HOME}`
  - Reject paths containing `..` or suspicious patterns
- **White-list approach**: Only allow removal of paths that were explicitly created during installation

### Registry Operation Safety

- **HKCU only**: All registry operations use `HKEY_CURRENT_USER`, requiring no administrator privileges
- **Limit scope**: Only remove/modify registry entries under the app's own registry path
- **Validate before deletion**: Check that created keys actually belong to this app before deletion
- **Backup**: Maintain a backup of modified registry values for recovery

### File Permissions

- **User ownership**: Ensure manifest files are only writable by the installing user
- **No elevated privileges**: Uninstall should not require administrator access (per-user install)
- **Safe deletion**: Use secure deletion if available; ensure file content is not recoverable

---

## Implementation Notes

### Path Handling

- Internally, all paths should use forward slashes `/` for consistency
- At read time, convert paths to platform-native separators:
  - Windows: `\`
  - Unix/macOS: `/`
- Implement variable expansion with proper error handling for missing variables

### Error Recovery

- If uninstall fails partway through, the manifest remains on disk (don't delete it)
- User can attempt uninstall again; the installer will process all entries again (idempotent)
- Failed entries can be manually cleaned up if necessary

### Backward Compatibility

- Manifest version `1.0` can be extended in future versions
- Implementations should ignore unknown elements and attributes gracefully
- Reserve the ability to add new sections (`<extensibility>`) for future features

---

## References

- **CliCommandManifest.java**: JSON-based CLI command manifest (to be superseded for full uninstall)
- **FileCliCommandManifestRepository.java**: Storage location logic (`~/.jdeploy/manifests/{arch}/{fqpn}/`)
- **InstallWindowsRegistry.java**: Registry backup log format and PATH modification logic
- **UninstallWindows.java**: Current uninstall implementation (to be extended with manifest support)
- **cli-commands-in-installer.md**: Unified directory structure and PATH management

---

## Appendix: MIME Type

```
application/vnd.jdeploy+xml or application/xml with .xml extension
```

## Summary

The Uninstall Manifest provides a comprehensive, machine-readable specification of all installation artifacts, enabling:
- **Complete cleanup**: All files, directories, registry entries, and PATH modifications are tracked
- **Cross-platform support**: Single format works on Windows, macOS, and Linux
- **Auditability**: Detailed logging of all uninstall actions
- **Safety**: Variable expansion and validation prevent path injection attacks
- **Idempotency**: Uninstall can be safely re-run if it fails partway through
