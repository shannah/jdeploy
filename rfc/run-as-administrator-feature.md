# Run as Administrator Feature

## Overview

The "Run as Administrator" feature allows jDeploy applications to be configured to run with elevated privileges on Windows, macOS, and Linux platforms. This feature provides developers with control over privilege escalation requirements for their applications.

## Configuration

The feature is configured via the `jdeploy.runAsAdministrator` property in the application's `package.json`:

```json
{
  "jdeploy": {
    "runAsAdministrator": "allowed"
  }
}
```

## Supported Values

### `"disabled"` (Default)
- Applications run with normal user privileges
- No elevated launchers are generated
- This is the default behavior when the property is not specified

### `"allowed"`
- Applications can optionally run with elevated privileges
- The installer generates two launcher variants for each app:
  - Standard launcher (normal privileges)
  - "Run as administrator" launcher (elevated privileges)
- Users can choose which launcher to use based on their needs

### `"required"`
- Applications always run with elevated privileges
- All generated launchers require administrator access
- Standard launchers are configured to automatically request elevation

## Platform Implementation

### Windows
- Uses the native "Run as administrator" option
- Elevated launchers are configured to trigger Windows UAC prompts
- Leverages Windows built-in privilege escalation mechanisms

### macOS
- Creates a wrapper launcher app that launches the main application with elevated permissions
- Utilizes macOS authorization services through the wrapper
- May prompt for administrator password when elevated launchers are used

### Linux
- Creates desktop file entries that use `pkexec` to launch the application
- `pkexec` provides PolicyKit-based privilege escalation
- Follows Linux security guidelines for elevated application execution

## Use Cases

### Development Tools
Applications that need to modify system files, install drivers, or access protected system resources.

### System Utilities
Administrative tools that require elevated access to perform system maintenance or configuration tasks.

### Optional Elevated Features
Applications with some features that benefit from elevated access but can function normally without it.

## Security Considerations

- Applications should follow the principle of least privilege
- Only request elevation when absolutely necessary
- Users should be clearly informed when elevated privileges are required
- Applications should validate the need for elevation before requesting it

## Installation Behavior

When `runAsAdministrator` is set to `"allowed"`:
- The installer creates both standard and elevated launcher shortcuts
- Elevated launchers are clearly labeled (e.g., "MyApp (Run as Administrator)")
- Users can access both variants from the start menu or applications folder

When `runAsAdministrator` is set to `"required"`:
- All launchers are configured for elevation
- Users will see elevation prompts when launching the application
- No standard (non-elevated) launchers are created

## Backward Compatibility

- Existing applications without the `runAsAdministrator` property maintain current behavior
- Default value of `"disabled"` ensures no breaking changes
- Applications can opt-in to elevated privileges as needed

## Future Enhancements

- Fine-grained permission control for specific application features
- Integration with platform-specific permission systems
- Runtime privilege escalation for specific operations