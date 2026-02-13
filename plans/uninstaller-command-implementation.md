# Implementation Plan: Uninstaller Command Support

## Overview

Add support for an "uninstaller" implementation type for CLI commands, similar to the existing "updater" implementation. When a command implements "uninstaller", it will intercept calls with the first argument "uninstall" and call the launcher binary with `--jdeploy:uninstall`.

This relies on the launcher uninstall support described in `/Users/shannah/projects/client4jgo/rfc/launcher-uninstall-support.md`.

## Reference Implementation: Updater

The updater implementation serves as the pattern to follow:

- **Constant**: `CommandSpecParser.IMPL_UPDATER = "updater"`
- **GUI Checkbox**: `CliCommandsPanel.updaterCheckbox`
- **Shim intercept**: Checks for single argument `"update"` and calls `launcher --jdeploy:update`

## Changes Required

### 1. Add IMPL_UNINSTALLER Constant

**File**: `shared/src/main/java/ca/weblite/jdeploy/models/CommandSpecParser.java`

Add constant alongside existing implementation constants:
```java
public static final String IMPL_UPDATER = "updater";
public static final String IMPL_SERVICE_CONTROLLER = "service_controller";
public static final String IMPL_LAUNCHER = "launcher";
public static final String IMPL_UNINSTALLER = "uninstaller";  // NEW
```

Update the `validateImplementation()` method to include "uninstaller" as a valid implementation value.

### 2. Add Uninstaller Checkbox to GUI

**File**: `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/CliCommandsPanel.java`

Add checkbox declaration alongside existing checkboxes:
```java
private JCheckBox updaterCheckbox;
private JCheckBox launcherCheckbox;
private JCheckBox serviceControllerCheckbox;
private JCheckBox uninstallerCheckbox;  // NEW
```

Create checkbox in panel creation (around line 205-210):
```java
uninstallerCheckbox = new JCheckBox("Uninstaller");
uninstallerCheckbox.setOpaque(false);
uninstallerCheckbox.setToolTipText("<html>Intercepts 'uninstall' argument to trigger app uninstallation.<br>" +
        "Example: <code>myapp-cli uninstall</code> → calls launcher with --jdeploy:uninstall</html>");
uninstallerCheckbox.addActionListener(e -> onFieldChanged());
checkboxPanel.add(uninstallerCheckbox);
```

Update loading code (around line 522-538):
```java
if (CommandSpecParser.IMPL_UNINSTALLER.equals(impl)) {
    uninstallerCheckbox.setSelected(true);
}
```

Update saving code (around line 737-745):
```java
if (uninstallerCheckbox.isSelected()) {
    implArray.put(CommandSpecParser.IMPL_UNINSTALLER);
}
```

### 3. Update Command Shim Generation

Update all three platform-specific CLI command installers to intercept "uninstall" argument:

#### Linux
**File**: `installer/src/main/java/ca/weblite/jdeploy/installer/cli/LinuxCliCommandInstaller.java`

After the updater check (around line 275), add uninstaller check:
```java
boolean hasUninstaller = implementations.contains("uninstaller");

// After updater check block...
if (hasUninstaller) {
    sb.append("# Check if single argument is \"uninstall\"\n");
    sb.append("if [ \"$#\" -eq 1 ] && [ \"$1\" = \"uninstall\" ]; then\n");
    sb.append("  exec \"").append(escapedLauncher).append("\" --jdeploy:uninstall\n");
    sb.append("fi\n\n");
}
```

#### macOS
**File**: `installer/src/main/java/ca/weblite/jdeploy/installer/cli/MacCliCommandInstaller.java`

Same pattern as Linux (around line 251):
```java
boolean hasUninstaller = implementations.contains("uninstaller");

if (hasUninstaller) {
    sb.append("# Check if single argument is \"uninstall\"\n");
    sb.append("if [ \"$#\" -eq 1 ] && [ \"$1\" = \"uninstall\" ]; then\n");
    sb.append("  exec \"").append(escapedLauncher).append("\" --jdeploy:uninstall\n");
    sb.append("fi\n\n");
}
```

#### Windows
**File**: `installer/src/main/java/ca/weblite/jdeploy/installer/cli/WindowsCliCommandInstaller.java`

Two places need updates:

1. **CMD wrapper** (around line 551):
```java
boolean hasUninstaller = implementations.contains("uninstaller");

if (hasUninstaller) {
    sb.append("REM Check if single argument is \"uninstall\"\r\n");
    sb.append("if \"%~1\"==\"uninstall\" if \"%~2\"==\"\" (\r\n");
    sb.append("  \"").append(launcherPathStr).append("\" --jdeploy:uninstall\r\n");
    sb.append("  exit /b !errorlevel!\r\n");
    sb.append(")\r\n\r\n");
}
```

2. **Git Bash wrapper** (around line 638):
```java
if (hasUninstaller) {
    sb.append("# Check if single argument is \"uninstall\"\n");
    sb.append("if [ \"$#\" -eq 1 ] && [ \"$1\" = \"uninstall\" ]; then\n");
    sb.append("  exec \"").append(msysLauncherPath).append("\" --jdeploy:uninstall\n");
    sb.append("fi\n\n");
}
```

### 4. Add Configurable Trigger (Optional - Phase 2)

Add `jdeploy.uninstallTrigger` property to customize the argument that triggers uninstallation.

**Note**: The existing updater implementation does NOT have a `jdeploy.updateTrigger` property - this would be a new feature for both updater and uninstaller. This could be deferred to a future enhancement.

If implementing now:

**File**: `shared/src/main/java/ca/weblite/jdeploy/models/JDeployProject.java` or similar

Add methods to read trigger configuration:
```java
public String getUpdateTrigger() {
    return jdeploy.optString("updateTrigger", "update");
}

public String getUninstallTrigger() {
    return jdeploy.optString("uninstallTrigger", "uninstall");
}
```

Then update the shim generation code to use these values instead of hardcoded strings.

### 5. Add Tests

**File**: `cli/src/test/java/ca/weblite/jdeploy/gui/tabs/CliCommandsPanelTest.java`

Add tests following the pattern of existing updater tests (lines 188-259):
- Test loading uninstaller implementation from JSON
- Test saving uninstaller implementation to JSON
- Test mixing uninstaller with other implementations
- Test roundtrip preservation

**File**: `shared/src/test/java/ca/weblite/jdeploy/models/CommandSpecParserTest.java`

Add validation test for "uninstaller" implementation value.

## Implementation Order

1. **Phase 1: Core Support** (MVP)
   - [ ] Add `IMPL_UNINSTALLER` constant to `CommandSpecParser`
   - [ ] Update `validateImplementation()` method
   - [ ] Add uninstaller checkbox to `CliCommandsPanel`
   - [ ] Update shim generation in `LinuxCliCommandInstaller`
   - [ ] Update shim generation in `MacCliCommandInstaller`
   - [ ] Update shim generation in `WindowsCliCommandInstaller` (CMD + Git Bash)
   - [ ] Add unit tests for GUI panel
   - [ ] Add unit tests for parser

2. **Phase 2: Configurable Triggers** (Optional Enhancement)
   - [ ] Add `uninstallTrigger` property support
   - [ ] Add `updateTrigger` property support (for consistency)
   - [ ] Update shim generation to use configurable triggers
   - [ ] Add tests for configurable triggers

## Testing Strategy

1. **Unit Tests**: Test CommandSpecParser validation and CliCommandsPanel save/load
2. **Integration Tests**: Create a test project in `tests/projects/` that uses the uninstaller implementation and verify the generated shims
3. **Manual Testing**: Install an app with uninstaller command and verify `myapp-cli uninstall` triggers the launcher uninstall

## package.json Example

After implementation, users can configure commands like:

```json
{
  "jdeploy": {
    "commands": {
      "myapp-cli": {
        "description": "MyApp CLI",
        "implements": ["updater", "uninstaller"]
      }
    }
  }
}
```

When installed, running `myapp-cli uninstall` will execute `launcher --jdeploy:uninstall`.

## Dependencies

- Requires launcher support for `--jdeploy:uninstall` flag (from client4jgo)
- The launcher must have the uninstall manifest present at the expected location

## Risks and Mitigations

1. **Risk**: Uninstall is destructive - accidental invocation could cause data loss
   - **Mitigation**: The launcher requires interactive confirmation by default

2. **Risk**: Uninstall manifest might not exist
   - **Mitigation**: Launcher handles missing manifest gracefully (exits with error)

3. **Risk**: User confusion between updater and uninstaller
   - **Mitigation**: Clear tooltip descriptions in GUI; distinct argument names