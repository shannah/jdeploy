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

Example:
```json
"jdeploy": {
  "commands": {
    "myapp-cli": {
      "args": [
        "-Dmy.system.prop=foo",
        "--my-app-arg=bar"
      ]
    },
    "myapp-admin": {
      "args": [
        "--mode=admin"
      ]
    }
  ]
}
```

Validation rules:
- Command names (object keys) MUST match the regex: `^[A-Za-z0-9._-]{1,255}$`.
  - This allows letters, digits, dot, underscore, and hyphen.
  - They MUST NOT contain path separators (`/` or `\`) or control characters.
  - This prevents attempts to place commands into arbitrary paths or overwrite paths by containing `../`.
- `args` if present MUST be an array of strings. Empty arrays are allowed.
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

## Platform behaviors

High-level goal: CLI command scripts/wrappers are installed per-user, in a user-writable directory that is on (or added to) the user's PATH.

### Linux

- Location:
  - Preferred: `~/.local/bin` (per XDG spec, user-local executables). This location is commonly on the PATH in modern distros/shells or can be added if missing.
  - Alternative (if current implementation already uses a different directory for binary symlinks): install the scripts alongside the binary symlink (same directory).
- Script format:
  - A POSIX shell script with the executable bit set (0o755).
  - It should `exec` the launcher binary to replace the script process, preserving signals and exit codes.
  - Example:
    ```sh
    #!/usr/bin/env sh
    exec "/home/alice/.jdeploy/apps/my-npm-package/my-app" --jdeploy:command=myapp-cli -- "$@"
    ```
- Path construction:
  - App directory: `~/.jdeploy/apps/{fullyQualifiedPackageName}/`
    - For NPM-released apps: `fullyQualifiedPackageName` = the npm package name (e.g., `my-npm-package`)
    - For GitHub-released apps: `fullyQualifiedPackageName` = `{MD5(npmSource)}.{npmPackageName}` (e.g., `a1b2c3d4.my-app`)
  - Binary name: derived from the app title by converting to lowercase, replacing spaces with hyphens, and removing non-alphanumeric characters (except hyphens)
    - Example: app title "My App" → binary name `my-app`
  - Branch suffix: for `0.0.0-{branch}` versions (e.g., GitHub branch builds), the branch name is appended with a hyphen
    - Example: version `0.0.0-main` with title "My App" → binary name `my-app-main`
- Quoting: pass user args via `-- "$@"` so user args are forwarded safely and unambiguously.
- Uninstall: remove the script file(s) and if the install directory was created by the installer and is now empty, optionally remove it. Do not touch unrelated files or user-owned content.

### macOS

Challenges:
- GUI macOS apps are typically packaged as `.app` bundles. Launching from the terminal should avoid the macOS GUI app life-cycle that would attempt to spawn an AppKit application (or pop UI).
- On macOS, calling the same launcher inside a `.app` bundle can sometimes trigger GUI initialization or behave differently when invoked from the command line.

Solution (dual-binary approach):
- When `jdeploy.commands` is present, the bundler creates a second binary in the app bundle at:
  ```
  MyApp.app/Contents/MacOS/Client4JLauncher-cli
  ```
  This file is a byte-identical copy of the GUI launcher binary (or equivalent wrapper) but named differently. The reasons for a second binary are:
  - macOS processes often treat `argv[0]` or the executable name specially (AppKit/NSApplication behavior can be influenced by program name).
  - Avoids accidental GUI initialization caused by frameworks that inspect the process name or bundle context on startup.
  - Keeps the GUI launcher unchanged; the CLI-entry binary can be treated as the CLI entrypoint that purposely avoids GUI initialization.
- Install per-command scripts (shell scripts) in `~/.local/bin` that call `.../MyApp.app/Contents/MacOS/Client4JLauncher-cli`.
  - Example script:
    ```sh
    #!/usr/bin/env sh
    exec "/Users/alice/Applications/MyApp.app/Contents/MacOS/Client4JLauncher-cli" --jdeploy:command=myapp-cli -- "$@"
    ```

Per-user script location:
- Scripts are installed in `~/.local/bin` to follow Linux/XDG conventions and encourage cross-platform consistency. This location is simple and already used by many tools; users can add it to PATH in their shell config if necessary.

PATH management:
- Installer detects whether the script directory (`~/.local/bin`) is already on PATH (by checking shell config or current environment).
- If not on PATH, the installer appends a shell profile snippet to `~/.profile` / `~/.bash_profile` / `~/.zprofile` (depending on the user's shell) to add the directory. This is a minimal, non-intrusive change.
- The installer does not ask for user consent for this modification (it is a standard practice and reversal is straightforward).

Uninstall:
- Remove the scripts and the `Client4JLauncher-cli` file in the `.app` bundle (if the app bundle itself is being removed).
- If installer added PATH entries or profile snippets, revert those changes or notify the user to revert; the uninstall should attempt to undo edits made during install.

### Windows

- Location:
  - Recommend: `%USERPROFILE%\.jdeploy\bin` (or `%USERPROFILE%\.local\bin`), a per-user directory the installer controls.
  - Rationale: per-user installs do not require elevation and keep crates under user control. This directory is easy to reference in CMD wrappers.
- Wrapper format:
  - Create a `.cmd` file for each command. `.cmd` wrappers are recognized by `cmd.exe` and PowerShell too.
  - Wrapper should forward all user args (`%*`).
  - Example `.cmd` wrapper:
    ```cmd
    @echo off
    REM Path to installed exe
    set "LAUNCHER=%USERPROFILE%\.jdeploy\apps\MyApp\Client4JLauncher.exe"
    "%LAUNCHER%" --jdeploy:command=myapp-cli %*
    ```
- PATH management:
  - Installer should add the per-user bin directory to the user's PATH via the HKCU\Environment `PATH` registry value.
  - When adding an entry to PATH, preserve the rest of the user's PATH value and only append/prepend the directory. Record that the installer modified PATH so the uninstaller can revert.
  - Note: Updating HKCU\Environment PATH does not immediately change PATH in existing console windows; the user may need to log out/log in or restart shells. The installer should notify the user about this.
- Uninstall:
  - Remove the generated `.cmd` files.
  - Remove the PATH modification that the installer added. If PATH was modified to include only the per-user bin, remove that segment and restore the previous PATH value stored during install.
  - Ensure the uninstaller only removes entries it created (do not remove the entire PATH).

## Uninstall behavior (cross-platform)

- The uninstaller must remove only files created by the installer:
  - Remove per-command script/wrapper files.
  - Remove CLI-specific binaries added (e.g., `Client4JLauncher-cli` in macOS app bundle) if the app bundle is removed.
  - Revert any PATH/profile modifications the installer itself made. The installer should record the changes (e.g., a small manifest in the installation directory) so the uninstaller can undo them reliably.
- The uninstaller should not remove user-created files or modifications outside the scope of what the installer created.

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
- Integrity:
  - Consider recording metadata about the created scripts (e.g., manifest file with checksums) so the uninstaller can detect tampering; optional for initial implementation.
- Command name collisions:
  - **Same-app collisions** (reinstalling the same app): if a command name is already present from a previous installation of the same app, it is silently overwritten. The installer records which files it creates, enabling clean removal during uninstall.
  - **Different-app collisions** (different app claims the same command name): the installer detects this and prompts the user interactively (if GUI installer) to skip installation of that command, overwrite the existing command, or install under a different name. Headless installers abort with a clear error message unless a CLI flag indicates "overwrite".

## Backward compatibility

- If `jdeploy.commands` is missing, installer behavior remains unchanged.
- Old installers (pre-support) will ignore `jdeploy.commands`; installers built with this change will still function for packages without `jdeploy.commands`.
- Installer metadata format change: bundlers/publishers must embed the `jdeploy.commands` array into installer metadata. Installers that do not understand that metadata will ignore it.

## Open questions and alternatives

(No open questions at this time. Key design decisions have been resolved and documented in the main RFC body.)

## Implementation notes (non-normative)

### Dependency Chain

The CLI commands feature requires coordination between multiple components:

1. **Packager** (at publish time):
   - Author defines `commands` in `package.json`
   - Packager validates the schema and embeds `jdeploy.commands` into installer metadata

2. **Bundler** (at install time, invoked by installer):
   - Creates the app bundle including launcher binaries
   - On macOS: Creates `Client4JLauncher-cli` when `BundlerSettings.isCliCommandsEnabled()` is true

3. **Installer** (at install time, after bundler):
   - Reads command specs from installer metadata
   - Creates per-command wrapper scripts in the user's bin directory
   - Updates PATH if needed and records changes in install manifest

4. **Uninstaller**:
   - Reads install manifest
   - Removes created scripts
   - Reverts PATH modifications

### Bundler/Installer Relationship

**Important**: The bundler is run **at install time** by the installer. The installer:
1. Invokes the bundler to create the app (including `Client4JLauncher-cli` on macOS when CLI commands are enabled)
2. Creates wrapper shell scripts pointing to the launcher binary built by the bundler

The `Client4JLauncher-cli` binary on macOS is created by the bundler (`MacBundler.maybeCreateCliLauncher()`) when `BundlerSettings.isCliCommandsEnabled()` is true.

### Install Manifest Format

The installer persists metadata about installed CLI commands in a JSON file located in the app directory:

```json
{
  "createdFiles": ["/home/user/.local/bin/myapp-cli", "/home/user/.local/bin/myapp-admin"],
  "pathUpdated": true,
  "binDir": "/home/user/.local/bin",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

Fields:
- `createdFiles`: Array of absolute paths to created script/wrapper files
- `pathUpdated`: Boolean indicating if the installer modified the user's PATH
- `binDir`: The bin directory where scripts were installed
- `timestamp`: ISO 8601 timestamp of installation

### General Implementation Notes

- Bundler/packager must include `jdeploy.commands` verbatim in installer metadata.
- Installer runtime must:
  - Read command specs from installer metadata.
  - Create per-command wrappers in the chosen per-user bin directory.
  - Ensure executability and proper quoting.
  - Record created entries in an install manifest so uninstall can remove them.
- The packaged launcher should implement a reliable early check for `--jdeploy:command`. When present, it must run CLI-only initialization (avoid GUI).

## Example artifacts

Linux shell script (example):
```sh
#!/usr/bin/env sh
# Installed at: ~/.local/bin/myapp-cli
exec "/home/alice/.jdeploy/apps/my-npm-package/my-app" \
  --jdeploy:command=myapp-cli \
  -- \
  "$@"
```

macOS script (example):
```sh
#!/usr/bin/env sh
# Installed at: ~/.local/bin/myapp-cli
exec "/Users/alice/Applications/MyApp.app/Contents/MacOS/Client4JLauncher-cli" \
  --jdeploy:command=myapp-cli \
  -- \
  "$@"
```

Windows `.cmd` wrapper (example):
```cmd
@echo off
REM Installed at: %USERPROFILE%\.jdeploy\bin\myapp-cli.cmd
set "LAUNCHER=%USERPROFILE%\.jdeploy\apps\MyApp\Client4JLauncher.exe"
"%LAUNCHER%" --jdeploy:command=myapp-cli %*
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
- Info icon with tooltip listing the commands that will be added (e.g., "Commands: myapp-cli, myapp-admin")

**Behavior:**
- If unchecked, CLI command scripts are still created but PATH is not modified
- The checkbox controls both CLI Launcher and CLI Commands PATH addition

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
| Command scripts created in correct location | Check `~/.local/bin/` (Unix) or `%USERPROFILE%\.jdeploy\bin\` (Windows) |
| Scripts are executable | `stat` shows 0755 permissions on Unix |
| Commands invoke launcher with `--jdeploy:command=<name>` | Inspect script content |
| User args passed after `--` separator | Test: `myapp-cli foo bar` passes `foo bar` to app |
| Uninstall removes only installer-created files | Metadata file tracks created files |
| PATH modifications are reversible | Uninstall restores original PATH |
| Empty commands object results in no scripts | Install with `"commands": {}` creates no wrappers |
| Invalid command names rejected | Names with `/`, `\`, or control chars fail validation |

### Recommended Test Cases

1. ✅ Script creation with valid commands
2. ✅ Script content escaping (quotes, special chars in paths)
3. ✅ `addToPath` for bash/zsh/fish shells
4. ✅ Uninstall removes scripts
5. ✅ Metadata persistence and loading
6. ✅ Command name collision detection
7. ✅ PATH restoration on uninstall
8. ✅ Handling of missing bin directory (should create it)
9. ✅ Partial failure handling (some commands fail, others succeed)
10. ✅ Idempotent uninstall (missing files don't cause errors)

## Summary / Recommendation

- Add an optional `jdeploy.commands` object to `package.json` to let package authors declare per-user CLI commands.
- Installer will create per-command wrappers that invoke the packaged launcher with `--jdeploy:command=<name>` and the declared `args`, forwarding user arguments.
- Platform specifics:
  - Linux: install scripts in `~/.local/bin`.
  - macOS: create a second binary `Contents/MacOS/Client4JLauncher-cli` and install scripts in `~/.local/bin` (or optionally `~/Library/Application Support/jdeploy/bin`); recommend `~/.local/bin`.
  - Windows: install `.cmd` wrappers in `%USERPROFILE%\.jdeploy\bin` and add that directory to HKCU\Environment PATH.
- Prefer the macOS dual-binary strategy to avoid GUI init side-effects; revisit later if launcher codebase can guarantee early CLI-only startup.

## Backward Compatibility

The `jdeploy.command` property (singular) predates this RFC and controls the CLI Launcher feature. It is unaffected by the addition of `jdeploy.commands` (plural). Packages can use both properties independently.

## Implementation roadmap

If this RFC is accepted, next steps are:

1. Add model/parser support for `jdeploy.commands` to the bundler and packager.
2. Ensure packaging embeds `jdeploy.commands` into installer metadata.
3. Implement bundler logic:
   - On macOS: create `Client4JLauncher-cli` when `BundlerSettings.isCliCommandsEnabled()` is true.
4. Implement installer logic for creating/removing per-command wrappers and PATH/profile management:
   - Linux: create shell scripts in `~/.local/bin`.
   - macOS: create shell scripts in `~/.local/bin` that reference `Client4JLauncher-cli`.
   - Windows: create `.cmd` wrappers in `%USERPROFILE%\.jdeploy\bin` and update HKCU\Environment PATH.
   - Implement collision detection and handling (same-app silent overwrite, different-app user prompt).
5. Add tests for schema validation, script generation, uninstall manifest handling, and PATH management.
