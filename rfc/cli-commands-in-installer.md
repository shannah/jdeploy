# RFC: Installer Support for CLI Commands (jdeploy.commands)

## Overview / Motivation

Currently the jdeploy installer produces and installs desktop GUI applications (per-user app bundles on macOS, binaries on Linux/Windows). Many apps built with jDeploy also ship a CLI/command-line surface. This RFC proposes adding optional installer-driven installation of CLI commands defined in the app's `package.json` under a new `jdeploy.commands` key.

Goals:
- Allow package authors to declare one or more CLI commands that should be installed and placed on the user's PATH (user-scope only).
- Ensure the installed command launches the same packaged runtime as the GUI app but in CLI mode, avoiding GUI initialization.
- Make the feature predictable, secure, and reversible (uninstallable).

Non-goals:
- System-wide installations (this RFC targets per-user installs).
- Changing runtime behavior beyond a simple invocation contract (`--jdeploy:command=...`).

## CLI Launcher vs CLI Commands

This RFC addresses two distinct but related features:

**CLI Launcher** (`jdeploy.command` - singular):
- A single command derived from the package configuration that launches the app in CLI mode
- Pre-dates this RFC; originally used on Linux to support launching GUI apps from the command-line
- The command name is derived in this order:
  1. The `jdeploy.command` property (if specified)
  2. The key from the `bin` object that maps to `jdeploy-bundle/jdeploy.js`
  3. The package `name` property
- Controlled by the `installCliLauncher` setting

**CLI Commands** (`jdeploy.commands` - plural):
- Additional named commands defined in the `jdeploy.commands` object
- Each command can have custom static arguments
- Controlled by the `installCliCommands` setting
- This is the primary focus of this RFC

Both features share the same underlying mechanism (wrapper scripts invoking the launcher with `--jdeploy:command=<name>`), but serve different purposes.

## Configuration schema

Add an optional `jdeploy.commands` object to `package.json` where command names are keys and each command spec is an object with the following properties:

- `args` (array of strings, optional): extra static arguments to pass to the launcher when this command is invoked (commonly JVM properties like `-D...` or app-specific flags).
- `description` (string, optional): a short description of what the command does, displayed in the installer UI to help users understand the command's purpose.
- `implements` (array of strings, optional): specifies special behaviors for this command. Each string must be one of: `"updater"`, `"service_controller"`, `"launcher"`. See "Command Implementations" section below for detailed behavior specifications.

Example:
```json
"jdeploy": {
  "commands": {
    "myapp-cli": {
      "args": [
        "-Dmy.system.prop=foo",
        "--my-app-arg=bar"
      ],
      "implements": ["updater"]
    },
    "myapp-admin": {
      "args": [
        "--mode=admin"
      ]
    },
    "myapp": {
      "description": "Open files in MyApp",
      "implements": ["launcher"]
    },
    "myappctl": {
      "description": "Control MyApp services",
      "implements": ["service_controller", "updater"]
    }
  }
}
```

Validation rules:
- Command names (object keys) MUST match the regex: `^[A-Za-z0-9._-]{1,255}$`.
  - This allows letters, digits, dot, underscore, and hyphen.
  - They MUST NOT contain path separators (`/` or `\`) or control characters.
  - This prevents attempts to place commands into arbitrary paths or overwrite paths by containing `../`.
- `args` if present MUST be an array of strings. Empty arrays are allowed.
- `description` if present MUST be a string. It is used to display context about the command in the installer UI. The description should be concise (recommended: under 80 characters) and provide a brief explanation of what the command does.
- `implements` if present MUST be an array of strings. Each string MUST be one of: `"updater"`, `"service_controller"`, `"launcher"`. Empty arrays are allowed. Invalid values should be rejected at build-time.
- The `commands` object itself may be empty (`"commands": {}` is valid and results in no CLI command wrappers being installed).
- The installer should validate this schema at bundle/installer build-time and reject invalid configs (or warn + skip installing bad entries).

Rationale:
- Using an object (instead of an array) makes command lookup more efficient and prevents name duplication by design.
- Restricting characters avoids filesystem/path injection and cross-platform portability issues.
- Max length prevents weird OS-specific name truncation behavior.

## Launcher invocation contract

Scripts/wrappers installed by the installer will invoke the packaged launcher with:

- a required argument: `--jdeploy:command=<name>` where `<name>` is the `name` from `jdeploy.commands`.
- the configured `args` from the command entry will be injected before the user's runtime arguments by the launcher.  How those are injected is outside the scope of this RFC.  The launcher, which is developed in a different project, will have its own direct access to the package.json file and will know how to load and inject the arguments for the provided command name into the JVM launch.
- any command-line arguments supplied by the user at runtime will be appended and passed through unchanged to the launcher after a `--` separator to avoid ambiguity.

Examples:

Linux/macOS script (pseudo):
```sh
exec "/path/to/Client4JLauncher-cli" --jdeploy:command=myapp-cli -- "$@"
```

Windows `.cmd` wrapper (pseudo):
```cmd
@"C:\Users\alice\.jdeploy\MyApp\Client4JLauncher.exe" --jdeploy:command=myapp-cli %*
```

The launcher (binary) MUST interpret the presence of `--jdeploy:command=<name>` as an instruction to run in *CLI mode*:
- Skip GUI initialization (no AWT/Swing/JavaFX or AppKit/NSApplication activation).
- Execute the CLI entry point of the application (the app's main or designated CLI handler).
- Return exit codes as appropriate and forward stdout/stderr to the calling terminal.

If the launcher receives `--jdeploy:command` it must stop any GUI bootstrap early to avoid creating GUI resources on headless invocations.

NOTE: The launcher is a black box to this project.  We include these specifications here to ensure the installer and packaging work correctly with the launcher behavior.

## Command Implementations

Commands may declare special behaviors via the optional `implements` array. Each implementation type modifies how the command wrapper script behaves:

### Updater Implementation

When a command includes `"updater"` in its `implements` array, the wrapper script checks for a single `update` argument:

- **If called with exactly one argument `update`**: The wrapper calls the app's CLI binary with `--jdeploy:update` (omitting the usual `--jdeploy:command={commandName}`).
  - Example: `myapp-cli update` → `launcher --jdeploy:update`
- **If called with no arguments or with additional arguments**: The wrapper behaves like a normal command, passing `--jdeploy:command={commandName}`.
  - Example: `myapp-cli` → `launcher --jdeploy:command=myapp-cli --`
  - Example: `myapp-cli foo bar` → `launcher --jdeploy:command=myapp-cli -- foo bar`

**Use case**: Allows users to trigger application updates from the command line using a dedicated CLI command.

### Launcher Implementation

When a command includes `"launcher"` in its `implements` array, the wrapper script launches the desktop application instead of invoking CLI mode:

- **Behavior**: All arguments are passed through to the binary and are assumed to be URLs or file paths to open.
- **Platform-specific handling**:
  - **macOS**: Use the `open` command to launch the `.app` bundle with arguments.
    - Example: `myapp file.txt` → `open -a MyApp.app file.txt`
  - **Windows/Linux**: Call the app binary script directly with arguments.
    - Example: `myapp file.txt` → `launcher file.txt`
- **No `--jdeploy:command` flag**: The launcher implementation does NOT pass the `--jdeploy:command={commandName}` flag.

**Use case**: Provides a convenient CLI shortcut to open files or URLs in the GUI application.

### Service Controller Implementation

When a command includes `"service_controller"` in its `implements` array, the wrapper script intercepts calls where the first argument is `service`:

- **If the first argument is `service`**: The wrapper calls the app's CLI binary with `--jdeploy:command={commandName}` and `--jdeploy:service` followed by the remaining arguments.
  - Example: `myappctl service start` → `launcher --jdeploy:command=myappctl --jdeploy:service start`
  - Example: `myappctl service stop` → `launcher --jdeploy:command=myappctl --jdeploy:service stop`
  - Example: `myappctl service status` → `launcher --jdeploy:command=myappctl --jdeploy:service status`
- **If the first argument is NOT `service`**: The wrapper processes the call like a normal command.
  - Example: `myappctl version` → `launcher --jdeploy:command=myappctl -- version`

**Use case**: Provides a standardized interface for controlling background services or daemons managed by the application.

### Multiple Implementations

A command may include multiple implementation types in its `implements` array. The wrapper script MUST check for special behaviors in the following order:

1. Check for `launcher` implementation (if present, launch desktop app and exit)
2. Check for `updater` implementation and single `update` argument (if match, call with `--jdeploy:update` and exit)
3. Check for `service_controller` implementation and first argument `service` (if match, call with `--jdeploy:service` and exit)
4. If no special behavior matches, invoke as a normal command with `--jdeploy:command={commandName}`

**Note**: In practice, `launcher` is mutually exclusive with other implementations since it does not use `--jdeploy:command`. Combining `updater` and `service_controller` is valid and allows a single command to handle both updates and service control.

## Platform behaviors

High-level goal: CLI command scripts/wrappers are installed per-user, in a per-app, architecture-specific directory that is added to the user's PATH.

### Unified Directory Structure

All platforms install CLI commands in a unified, architecture-specific, per-app directory:

```
~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/
```

Where:
- `{arch}` is the architecture suffix (`-arm64` or `-x64`) from `ArchitectureUtil.getArchitectureSuffix()`
- `{fullyQualifiedPackageName}` is computed as:
  - For NPM registry packages (source is null/empty): use `packageName` directly
  - For GitHub/custom source packages: use `{MD5(source)}.{packageName}`

**Benefits of this structure:**
- **No command name collisions**: Each app has its own isolated directory
- **Independent PATH management**: Each app's directory is added/removed from PATH independently
- **Simplified uninstallation**: Remove the entire app directory; no need to track individual files
- **Platform consistency**: All platforms use the same structure
- **Architecture coexistence**: ARM64 and x64 versions can coexist (e.g., for Rosetta 2 on macOS)

**Examples:**

NPM package "myapp" on ARM64 macOS:
```
~/.jdeploy/bin-arm64/myapp/
  myapp-cli
  myapp-admin
```

GitHub-sourced "myapp" from "https://github.com/user/myapp-repo" on x64 Linux:
```
~/.jdeploy/bin-x64/356a192b7913b04c54574d18c28d46e6.myapp/
  myapp-cli
  myapp-admin
```

### Linux

- Location: `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/`
- Script format:
  - A POSIX shell script with the executable bit set (0o755).
  - It should `exec` the launcher binary to replace the script process, preserving signals and exit codes.
  - Example:
    ```sh
    #!/usr/bin/env sh
    exec "/home/alice/.jdeploy/apps/my-npm-package/my-app" --jdeploy:command=myapp-cli -- "$@"
    ```
- Quoting: pass user args via `-- "$@"` so user args are forwarded safely and unambiguously.
- Uninstall: remove the entire app bin directory `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/`. Do not remove parent directories even if empty.

### macOS

- Location: `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/`
- Dual-binary approach:
  - When `jdeploy.commands` is present, the bundler creates a second binary in the app bundle:
    ```
    MyApp.app/Contents/MacOS/Client4JLauncher-cli
    ```
  - This is a byte-identical copy of the GUI launcher but named differently to avoid GUI initialization triggered by AppKit/NSApplication inspecting the process name.
- Script format:
  - Shell scripts that call `Client4JLauncher-cli`:
    ```sh
    #!/usr/bin/env sh
    exec "/Users/alice/Applications/MyApp.app/Contents/MacOS/Client4JLauncher-cli" --jdeploy:command=myapp-cli -- "$@"
    ```
- PATH management:
  - Add the app's bin directory to PATH via shell profile (`~/.profile`, `~/.bash_profile`, `~/.zprofile`)
  - Detect and respect user override marker (`# jdeploy:no-auto-path`) to prevent automatic PATH modification
- Uninstall:
  - Remove the app's bin directory
  - Remove the `Client4JLauncher-cli` file in the `.app` bundle
  - Remove the app's PATH entry from shell profiles

### Windows

- Location: `%USERPROFILE%\.jdeploy\bin-{arch}\{fullyQualifiedPackageName}\`
- Dual-binary approach:
  - When `jdeploy.commands` is present, the bundler creates a CLI-specific executable:
    ```
    %USERPROFILE%\.jdeploy\apps\MyApp\MyApp-cli.exe
    ```
  - This CLI variant has its PE subsystem modified from GUI (2) to Console (3), ensuring proper console behavior
- Wrapper format:
  - Create a `.cmd` file for each command for CMD/PowerShell:
    ```cmd
    @echo off
    set "LAUNCHER=%USERPROFILE%\.jdeploy\apps\MyApp\MyApp-cli.exe"
    "%LAUNCHER%" --jdeploy:command=myapp-cli -- %*
    ```
- Git Bash / MSYS2 Support:
  - Create an extensionless POSIX shell script for each command for Git Bash compatibility
  - Convert Windows paths to MSYS2 format (e.g., `/c/Users/` instead of `C:\Users\`)
  - Update Git Bash configuration (`.bash_profile` or `.bashrc`) with PATH entry using POSIX-style paths
- PATH management:
  - Add the app's bin directory to user PATH via `HKCU\Environment` registry value
  - Detect and respect user override marker (`# jdeploy:no-auto-path`) in Git Bash config
  - Note: PATH changes require logout/login or shell restart to take effect
- Uninstall:
  - Remove the app's bin directory (containing both `.cmd` and shell script files)
  - Remove the app's PATH entry from Windows registry
  - Remove the app's PATH export line from Git Bash configuration

## Uninstall behavior (cross-platform)

With the per-app directory structure, uninstallation is simplified:

- Remove the app's bin directory: `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/`
- Remove CLI-specific launcher binaries:
  - macOS: `Client4JLauncher-cli` in the `.app` bundle
  - Windows: `{AppName}-cli.exe` in the app directory
- Remove the app's PATH entry from:
  - Unix: shell profile files (`~/.profile`, `~/.bash_profile`, `~/.zprofile`)
  - Windows: `HKCU\Environment` registry and Git Bash configuration files
- Remove metadata file: `~/.jdeploy/manifests/{arch}/{fullyQualifiedPackageName}/.jdeploy-cli.json`
- Do NOT remove parent directories (`~/.jdeploy/bin-{arch}/`, `~/.jdeploy/manifests/`) even if empty
- Uninstall operations should be idempotent: missing files are skipped without error

## Security considerations

- Sanitize command names:
  - Reject names containing path separators or shell metacharacters during install-time validation.
  - Use a strict regex (recommended: `^[A-Za-z0-9._-]{1,255}$`) and treat any deviation as a validation failure.
- Sanitize command args:
  - Reject args containing dangerous shell metacharacters that could enable command injection.
  - Forbidden characters: `;` (command chaining), `|` (piping), `&` (background execution), `` ` `` (backtick command substitution), `$(` (command substitution).
  - Use regex pattern: `[;|&`]|\$\(` to detect and reject dangerous args.
- Avoid shell injection:
  - Installer-generated shell scripts should avoid composing commands via unescaped string concatenation. Each configured arg must be written as a separate literal in the script (or quoted properly).
  - On Linux/macOS, prefer to use `exec` with quoted arguments and `"$@"` to pass user args safely.
  - On Windows, supply the launcher path and args as separate tokens and use `%*` to forward user args. Avoid using `cmd /c` to concatenate complex strings.
- File permissions:
  - Ensure scripts are created with mode 0o755 (executable) on POSIX systems.
  - On Windows, ensure wrapper `.cmd` files are writable by the user only as necessary.
- Least privilege:
  - Stick to per-user locations to avoid requiring elevated privileges that could otherwise be exploited.
- Command name collisions:
  - The per-app directory structure eliminates command name collisions between different apps
  - Reinstalling the same app silently overwrites commands in its own directory

## Backward compatibility

- If `jdeploy.commands` is missing, installer behavior remains unchanged.
- Old installers (pre-support) will ignore `jdeploy.commands`; installers built with this change will still function for packages without `jdeploy.commands`.
- Installer metadata format change: bundlers/publishers must embed the `jdeploy.commands` array into installer metadata. Installers that do not understand that metadata will ignore it.

## Open questions and alternatives

(No open questions at this time. Key design decisions have been resolved and documented in the main RFC body.)

## Metadata Format

The installer persists metadata about installed CLI commands in `~/.jdeploy/manifests/{arch}/{fullyQualifiedPackageName}/.jdeploy-cli.json`:

```json
{
  "createdWrappers": ["myapp-cli", "myapp-admin"],
  "pathUpdated": true,
  "binDir": "/Users/alice/.jdeploy/bin-arm64/myapp"
}
```

Fields:
- `createdWrappers`: Array of created wrapper script/file names (relative names only)
- `pathUpdated`: Boolean indicating if the installer modified the user's PATH
- `binDir`: The bin directory path for this app (used during uninstall)

## Example artifacts

### Standard Command (no special implementations)

Linux shell script:
```sh
#!/usr/bin/env sh
# Installed at: ~/.jdeploy/bin-x64/myapp/myapp-cli
exec "/home/alice/.jdeploy/apps/myapp/my-app" \
  --jdeploy:command=myapp-cli \
  -- \
  "$@"
```

macOS script:
```sh
#!/usr/bin/env sh
# Installed at: ~/.jdeploy/bin-arm64/myapp/myapp-cli
exec "/Users/alice/Applications/MyApp.app/Contents/MacOS/Client4JLauncher-cli" \
  --jdeploy:command=myapp-cli \
  -- \
  "$@"
```

Windows `.cmd` wrapper:
```cmd
@echo off
REM Installed at: %USERPROFILE%\.jdeploy\bin-x64\myapp\myapp-cli.cmd
set "LAUNCHER=%USERPROFILE%\.jdeploy\apps\MyApp\MyApp-cli.exe"
"%LAUNCHER%" --jdeploy:command=myapp-cli -- %*
```

### Command with Updater Implementation

Linux/macOS script with `"implements": ["updater"]`:
```sh
#!/usr/bin/env sh
# Check if single argument is "update"
if [ "$#" -eq 1 ] && [ "$1" = "update" ]; then
  exec "/home/alice/.jdeploy/apps/myapp/my-app" --jdeploy:update
else
  exec "/home/alice/.jdeploy/apps/myapp/my-app" --jdeploy:command=myapp-cli -- "$@"
fi
```

Windows `.cmd` wrapper with `"implements": ["updater"]`:
```cmd
@echo off
REM Check if single argument is "update"
if "%~1"=="update" if "%~2"=="" (
  "%LAUNCHER%" --jdeploy:update
) else (
  "%LAUNCHER%" --jdeploy:command=myapp-cli -- %*
)
```

### Command with Launcher Implementation

macOS script with `"implements": ["launcher"]`:
```sh
#!/usr/bin/env sh
# Launch the desktop app using the open command
exec open -a "/Users/alice/Applications/MyApp.app" "$@"
```

Linux script with `"implements": ["launcher"]`:
```sh
#!/usr/bin/env sh
# Launch the desktop app directly
exec "/home/alice/.jdeploy/apps/myapp/my-app" "$@"
```

Windows `.cmd` wrapper with `"implements": ["launcher"]`:
```cmd
@echo off
set "LAUNCHER=%USERPROFILE%\.jdeploy\apps\MyApp\MyApp.exe"
"%LAUNCHER%" %*
```

### Command with Service Controller Implementation

Linux/macOS script with `"implements": ["service_controller"]`:
```sh
#!/usr/bin/env sh
# Check if first argument is "service"
if [ "$1" = "service" ]; then
  shift
  exec "/home/alice/.jdeploy/apps/myapp/my-app" --jdeploy:command=myappctl --jdeploy:service "$@"
else
  exec "/home/alice/.jdeploy/apps/myapp/my-app" --jdeploy:command=myappctl -- "$@"
fi
```

Windows `.cmd` wrapper with `"implements": ["service_controller"]`:
```cmd
@echo off
set "LAUNCHER=%USERPROFILE%\.jdeploy\apps\MyApp\MyApp-cli.exe"
if "%~1"=="service" (
  shift
  "%LAUNCHER%" --jdeploy:command=myappctl --jdeploy:service %*
) else (
  "%LAUNCHER%" --jdeploy:command=myappctl -- %*
)
```

### Command with Multiple Implementations

Linux/macOS script with `"implements": ["service_controller", "updater"]`:
```sh
#!/usr/bin/env sh
LAUNCHER="/home/alice/.jdeploy/apps/myapp/my-app"

# Check for updater: single "update" argument
if [ "$#" -eq 1 ] && [ "$1" = "update" ]; then
  exec "$LAUNCHER" --jdeploy:update
fi

# Check for service_controller: first argument is "service"
if [ "$1" = "service" ]; then
  shift
  exec "$LAUNCHER" --jdeploy:command=myappctl --jdeploy:service "$@"
fi

# Default: normal command
exec "$LAUNCHER" --jdeploy:command=myappctl -- "$@"
```

## Auto-Update Behavior

CLI command scripts reference the launcher by absolute path. The auto-update mechanism works as follows:

- **Scripts continue working after auto-update**: The location of installed apps does not change during auto-update, so existing scripts remain valid.
- **No script reinstallation needed**: Auto-updates replace the launcher binary in-place; wrapper scripts pointing to it continue to function.
- **Publisher changes to commands**: If a publisher modifies the `jdeploy.commands` configuration (e.g., adds new commands), they should set the `minInitialAppVersion` property in the package version. This signals that users need a full launcher update, which triggers the full installer to run and recreate/update CLI command scripts.
- **Admin mode**: CLI commands will support admin mode in the future but do not currently. This is planned for a future RFC update.

## Edge Cases and Error Handling

**Bin directory creation:**
The installer MUST create the bin directory (e.g., `~/.local/bin`) if it does not exist.

**Empty commands object:**
An empty `commands` object (`"commands": {}`) is valid and results in no CLI command wrappers being installed.

**Script creation failure:**
On script creation failure (permission denied, disk full, path too long), the installer MUST:
1. Log the error with the specific command name and path
2. Continue attempting to install remaining commands
3. Report all failures to the user at the end
4. NOT mark the overall installation as failed if only CLI command installation fails (the GUI app should still work)

**Partial uninstall state:**
Uninstall operations SHOULD be idempotent. If a file is already missing, skip it without error. The uninstaller should process all entries in the manifest and report any failures at the end.

**Launcher binary naming:**
The launcher binary names (`Client4JLauncher`, `Client4JLauncher-cli`) are defined by constants in the codebase. Implementations should reference these constants rather than hardcoding names.

## UI Specification

When `jdeploy.commands` contains at least one command, the installation form SHOULD include:

**Checkbox:**
- Label: "Add command-line tools to PATH"
- Default state: Selected (checked)
- Info icon with tooltip listing the commands that will be added. The tooltip should include command names and their descriptions if available (e.g., "Commands: myapp-cli (Run the application in CLI mode), myapp-admin (Start the admin console)")

**Command descriptions:**
- If a command has a `description` field, it SHOULD be displayed in the installer UI (in the tooltip, a list, or expandable details) to help users understand what each command does.
- Descriptions help users make informed decisions about which commands to install or configure.

**Behavior:**
- If unchecked, CLI command scripts are still created but PATH is not modified
- The checkbox controls both CLI Launcher and CLI Commands PATH addition

**Command descriptions:**
- If a command has a `description` field, it SHOULD be displayed in the installer UI (in the tooltip, a list, or expandable details) to help users understand what each command does.
- Descriptions help users make informed decisions about which commands to install or configure.

**Error handling:**
- If PATH modification fails, log the error to STDERR
- Do not display an error dialog to the user (the GUI app installation succeeded)
- The installer is a GUI application; terminal-specific instructions (like "restart your terminal") are not appropriate

**Installation feedback:**
- CLI command installation is fast enough that progress feedback is not required
- No need for "Installing CLI commands..." progress messages

## Verification Checklist

| Criterion | Verification Method |
|-----------|---------------------|
| Command scripts created in correct location | Check `~/.jdeploy/bin-{arch}/{fqpn}/` on all platforms |
| Architecture suffix applied correctly | Verify `-arm64` or `-x64` suffix in path |
| Fully qualified package name computed correctly | NPM packages use name only; GitHub packages use `{MD5}.{name}` |
| Dual-scripts on Windows | Verify both `.cmd` and extensionless files exist in bin directory |
| MSYS2 Path Conversion | Verify Windows shell scripts use `/c/path` style paths |
| Scripts are executable | `stat` shows 0755 permissions on Unix |
| Commands invoke launcher with `--jdeploy:command=<name>` | Inspect script content |
| User args passed after `--` separator | Test: `myapp-cli foo bar` passes `foo bar` to app |
| Uninstall removes app bin directory | Entire `~/.jdeploy/bin-{arch}/{fqpn}/` is removed |
| PATH modifications are reversible | Uninstall removes app's PATH entry; other apps unaffected |
| No command collisions across apps | Install two apps with same command name; both work independently |
| Empty commands object results in no scripts | Install with `"commands": {}` creates no wrappers |
| Invalid command names rejected | Names with `/`, `\`, or control chars fail validation |

### Recommended Test Cases

1. ✅ Script creation with valid commands
2. ✅ Script content escaping (quotes, special chars in paths)
3. ✅ `addToPath` for bash/zsh/fish shells
4. ✅ Windows Git Bash `.bashrc` / `.bash_profile` modification
5. ✅ Windows MSYS2 path conversion logic (`C:\` -> `/c/`)
6. ✅ Uninstall removes scripts (including Windows shell wrappers)
7. ✅ Metadata persistence and loading
8. ✅ Command name collision detection
9. ✅ PATH restoration on uninstall
10. ✅ Handling of missing bin directory (should create it)
11. ✅ Partial failure handling (some commands fail, others succeed)
12. ✅ Idempotent uninstall (missing files don't cause errors)

## Summary / Recommendation

- Add an optional `jdeploy.commands` object to `package.json` to let package authors declare per-user CLI commands.
- Installer will create per-command wrappers in a unified, architecture-specific, per-app directory: `~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/`
- Wrappers invoke the packaged launcher with `--jdeploy:command=<name>` and the declared `args`, forwarding user arguments.
- Platform specifics:
  - All platforms use the unified directory structure for consistency
  - macOS: create a second binary `Contents/MacOS/Client4JLauncher-cli` to avoid GUI initialization
  - Windows: create a CLI-specific executable with Console subsystem and dual wrappers (`.cmd` + shell script) for Git Bash support
- Benefits: eliminates command name collisions, simplifies PATH management, enables clean per-app uninstallation

## Backward Compatibility

The `jdeploy.command` property (singular) predates this RFC and controls the CLI Launcher feature. It is unaffected by the addition of `jdeploy.commands` (plural). Packages can use both properties independently.
