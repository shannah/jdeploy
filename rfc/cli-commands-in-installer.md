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

## Configuration schema

Add an optional `jdeploy.commands` array to `package.json`. Each entry is an object with the following properties:

- `name` (string, required): the command name that will be added to the user's PATH.
- `args` (array of strings, optional): extra static arguments to pass to the launcher when this command is invoked (commonly JVM properties like `-D...` or app-specific flags).

Example:
```json
"jdeploy": {
  "commands": [
    {
      "name": "myapp-cli",
      "args": [
        "-Dmy.system.prop=foo",
        "--my-app-arg=bar"
      ]
    },
    {
      "name": "myapp-admin",
      "args": [
        "--mode=admin"
      ]
    }
  ]
}
```

Validation rules:
- `name` MUST be present and MUST match the regex: `^[A-Za-z0-9._-]{1,255}$`.
  - This allows letters, digits, dot, underscore, and hyphen.
  - It MUST NOT contain path separators (`/` or `\`) or control characters.
  - This prevents attempts to place commands into arbitrary paths or overwrite paths by containing `../`.
- Command names MUST be unique within the array.
- `args` if present MUST be an array of strings. Empty arrays are allowed.
- The installer should validate this schema at bundle/installer build-time and reject invalid configs (or warn + skip installing bad entries).

Rationale:
- Restricting characters avoids filesystem/path injection and cross-platform portability issues.
- Max length prevents weird OS-specific name truncation behavior.

## Launcher invocation contract

Scripts/wrappers installed by the installer will invoke the packaged launcher with:

- a required argument: `--jdeploy:command=<name>` where `<name>` is the `name` from `jdeploy.commands`.
- the configured `args` from the command entry will be injected before the user's runtime arguments.
- any command-line arguments supplied by the user at runtime will be appended and passed through unchanged.

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

## Platform behaviors

High-level goal: CLI command scripts/wrappers are installed per-user, in a user-writable directory that is on (or added to) the user's PATH.

### Common packaging note
The bundler/publisher must embed `jdeploy.commands` data into the installer metadata so the installer knows what commands to create on install. The installer must then create wrapper scripts mapped to actual installed launcher binaries.

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
    exec "/home/alice/.jdeploy/apps/MyApp/Client4JLauncher" --jdeploy:command=myapp-cli -- "$@"
    ```
- Quoting: pass user args via `-- "$@"` so user args are forwarded safely and unambiguously.
- Uninstall: remove the script file(s) and if the install directory was created by the installer and is now empty, optionally remove it. Do not touch unrelated files or user-owned content.

### macOS

Challenges:
- GUI macOS apps are typically packaged as `.app` bundles. Launching from the terminal should avoid the macOS GUI app life-cycle that would attempt to spawn an AppKit application (or pop UI).
- On macOS, calling the same launcher inside a `.app` bundle can sometimes trigger GUI initialization or behave differently when invoked from the command line.

Proposal (recommended):
- When `jdeploy.commands` is present, the bundler should create a second binary in the app bundle at:
  ```
  MyApp.app/Contents/MacOS/Client4JLauncher-cli
  ```
  This file is a byte-identical copy of the GUI launcher binary (or equivalent wrapper) but named differently. The reason for a second binary is:
  - macOS processes often treat `argv[0]` or the executable name specially (AppKit/NSApplication behavior can be influenced by program name).
  - Avoids accidental GUI initialization caused by frameworks that inspect the process name or bundle context on startup.
  - Keeps the GUI launcher unchanged; the CLI-entry binary can be treated as the CLI entrypoint that purposely avoids GUI initialization.
- Install per-command scripts (shell scripts) in a user-local bin directory (recommendation below) that call `.../MyApp.app/Contents/MacOS/Client4JLauncher-cli`.
  - Example script:
    ```sh
    #!/usr/bin/env sh
    exec "/Users/alice/Applications/MyApp.app/Contents/MacOS/Client4JLauncher-cli" --jdeploy:command=myapp-cli -- "$@"
    ```

Per-user script location recommendations:
- Preferred: `~/.local/bin`
  - Rationale: follows Linux/XDG conventions, is simple, and already used by many tools; users can add it to PATH in their shell config if necessary.
- Alternative: `~/Library/Application Support/jdeploy/bin`
  - Rationale: keeps all jdeploy-provided files fully under `~/Library/Application Support` which is macOS-standard for app data. If using this location, the installer should add it to PATH (via modifying shell rc or by printing instructions), which is intrusive.
Recommendation: use `~/.local/bin` as the default install location for CLI scripts on macOS (and Linux) because it is least surprising and encourages cross-platform consistency. If the user chooses to keep all jdeploy assets under `~/Library/Application Support/jdeploy`, the installer can support that as an opt-in alternative.

PATH management:
- Installer should attempt to detect whether the chosen script directory is already on PATH (by checking shell config or current environment) and if not:
  - Add an informative message to the user with instructions to add it to PATH, and/or
  - Optionally (with consent) append a shell profile snippet to `~/.profile` / `~/.bash_profile` / `~/.zprofile` depending on the user's shell to add the directory.
- The installer must not silently modify all shell configs; prefer a minimal change (e.g., `~/.profile`) or ask user.

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
  - Alternatively, a small native launcher (EXE) could be used, but `.cmd` wrappers are simple and sufficient.
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
- Path collisions:
  - If a command name already exists on PATH and is not owned by this install, the installer should warn the user and offer options:
    - Skip installation of that command.
    - Overwrite (replace) the existing command (not recommended without explicit user consent).
    - Install under a different name.
  - By default, prefer to fail/ask rather than overwrite silently.

## Backward compatibility

- If `jdeploy.commands` is missing, installer behavior remains unchanged.
- Old installers (pre-support) will ignore `jdeploy.commands`; installers built with this change will still function for packages without `jdeploy.commands`.
- Installer metadata format change: bundlers/publishers must embed the `jdeploy.commands` array into installer metadata. Installers that do not understand that metadata will ignore it.

## Open questions and alternatives

1. Dual binary vs single binary with early `--jdeploy:command` check (macOS):
   - Dual-binary approach (recommended):
     - Pros: lowest risk; some frameworks perform behavior based on `argv[0]` or binary name; copying the launcher and naming it `*-cli` is an easy way to avoid unexpected GUI framework interactions.
     - Cons: slightly larger app bundle (extra binary copy), small maintenance cost if the launcher binary is regenerated.
   - Single-binary approach (alternative):
     - Have the launcher check `argv` early and if `--jdeploy:command` is present, avoid importing or initializing GUI frameworks (do minimal startup).
     - Pros: no duplicate binary.
     - Cons: requires discipline to ensure that no static initializers, library loads, or framework calls occur prior to the early check, which can be brittle and platform-dependent. Some frameworks may initialize during static init or via native libraries, which is risky.
   - Recommendation: implement dual binary for macOS to minimize risk and implementation complexity. Consider revisiting single-binary approach if the codebase proves safe after audit.

2. Using symlinks vs scripts on Linux:
   - If the launcher can detect invoked name (`argv[0]`) to determine which command was requested, installing per-command symlinks to the same executable could be an option (symlink named `myapp-cli` pointing to launcher).
   - Pros: simpler; no wrapper script.
   - Cons: requires the launcher to map `argv[0]` to command names and might be less flexible for injecting per-command `args`.
   - Recommendation: prefer explicit shell scripts that call the launcher with `--jdeploy:command` and configured args. Consider symlink support as an optimization only if the launcher can reliably inspect `argv[0]`.

3. Windows PATH update semantics:
   - HKCU\Environment changes may not take effect immediately for existing consoles.
   - Alternative: modify current process environment for the installer, and notify the user to restart their shell.
   - Recommendation: update HKCU\Environment (user PATH) and clearly inform the user that they may need to re-open consoles.

4. Handling name collisions across different installed apps:
   - The installer should detect if a requested `name` is already present on PATH.
   - The behavior could be:
     - Prompt user to skip/rename/overwrite.
     - Automatically install with a suffix (not desirable).
   - Recommendation: ask the user interactively (if GUI installer) or abort with a clear error (if headless), unless a CLI flag indicates "overwrite".

## Implementation notes (non-normative)

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
exec "/home/alice/.jdeploy/apps/MyApp/Client4JLauncher" \
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

## Summary / Recommendation

- Add an optional `jdeploy.commands` array to `package.json` to let package authors declare per-user CLI commands.
- Installer will create per-command wrappers that invoke the packaged launcher with `--jdeploy:command=<name>` and the declared `args`, forwarding user arguments.
- Platform specifics:
  - Linux: install scripts in `~/.local/bin`.
  - macOS: create a second binary `Contents/MacOS/Client4JLauncher-cli` and install scripts in `~/.local/bin` (or optionally `~/Library/Application Support/jdeploy/bin`); recommend `~/.local/bin`.
  - Windows: install `.cmd` wrappers in `%USERPROFILE%\.jdeploy\bin` and add that directory to HKCU\Environment PATH.
- Prefer the macOS dual-binary strategy to avoid GUI init side-effects; revisit later if launcher codebase can guarantee early CLI-only startup.

## Open items for implementation

- Decide exact per-user script directory on macOS (default proposed: `~/.local/bin`).
- Decide interactive vs headless behavior when a command name already exists on PATH.
- Decide whether the installer auto-applies PATH modifications or only prints instructions and asks for consent.

If this RFC is accepted, next steps are:
1. Add model/parser support for `jdeploy.commands` to the bundler and packager.
2. Ensure packaging embeds `jdeploy.commands` into installer metadata.
3. Implement installer logic for creating/removing per-command wrappers and PATH/profile management.
4. Add tests for schema validation, script generation, and uninstall manifest handling.
