# Custom Windows App Installation Directory (`winAppDir`)

## Overview

The `winAppDir` property allows developers to configure where their application executable is installed on Windows. By default, jDeploy installs app executables into `%USERPROFILE%\.jdeploy\apps`. This property provides the ability to override that location, primarily to address Windows Defender warnings that can occur with the default path.

## Motivation

Windows Defender and other security software may flag executables running from non-standard locations such as `%USERPROFILE%\.jdeploy\apps`. The conventional location for user-level Windows programs is `%USERPROFILE%\AppData\Local\Programs`. Providing a way to install into this standard directory improves compatibility with security software and aligns with Windows conventions.

## Configuration

The feature is configured via the `jdeploy.winAppDir` property in the application's `package.json`:

```json
{
  "jdeploy": {
    "winAppDir": "AppData\\Local\\Programs"
  }
}
```

The value is a relative path resolved against the user's home directory (`%USERPROFILE%`).

## Behavior

### Default (property not set)

- App executables are installed to `%USERPROFILE%\.jdeploy\apps`
- This is the existing behavior for all prior jDeploy versions

### With `winAppDir` set

- App executables are installed to `%USERPROFILE%\<winAppDir>` instead of the default location
- Example: `"winAppDir": "AppData\\Local\\Programs"` results in installation to `%USERPROFILE%\AppData\Local\Programs`

### Platform scope

- This property only affects Windows installations
- macOS and Linux installations are unaffected regardless of the value

## Version

Introduced in jDeploy 6.