# Installer Preferences Override

## Status
Implemented (installer side) — Launcher integration pending

## Overview

When a user installs a jDeploy application, they can configure update preferences (auto-update policy and prerelease opt-in) through the installer UI. These selections are embedded into the `app.xml` file at install time. However, app.xml is regenerated on every install/update, meaning the user's preferences are lost if the app is reinstalled or the installer is run again.

This RFC introduces a **preferences override file** that persists the user's selections independently of app.xml. The launcher should read this file and use its values to override the corresponding attributes from app.xml at runtime.

## Motivation

- On reinstall or update, the installer can pre-populate the UI with the user's previous choices.
- The launcher can respect user overrides without requiring a full reinstall to change update behavior.
- Separating user preferences from app.xml keeps the developer-defined defaults distinct from user overrides.

## File Location

```
~/.jdeploy/preferences/{fqpn}/preferences.properties
```

Where `{fqpn}` is the **fully qualified package name**, computed as:
- **NPM packages** (source is null/empty): the `package` name as-is (e.g., `my-app`)
- **GitHub packages** (source provided): `{MD5(source)}.{packageName}` (e.g., `356a192b7913b04c54574d18c28d46e6.my-app`)

This matches the convention used by other per-app state directories (`~/.jdeploy/manifests/`, `~/.jdeploy/ai-integrations/`, etc.), but **without** an architecture suffix since preferences are user-level, not platform-specific.

## File Format

Standard Java `.properties` format:

```properties
# jDeploy Installer Preferences
version=latest
prerelease=false
```

## Properties

### `version`

The version constraint string, identical in format to the `version` attribute in `app.xml`. Possible values:

| Value | Meaning | Installer UI label |
|-------|---------|-------------------|
| `latest` | Always update to the latest stable release | Stable |
| `^{major}` (e.g., `^1`) | Update within the same major version (minor + patch) | Minor Only |
| `~{major}.{minor}` (e.g., `~1.2`) | Update within the same major.minor (patches only) | Patches Only |
| `{major}.{minor}.{patch}` (e.g., `1.2.3`) | Pinned to exact version, no auto-updates | Off |

These values are produced by the installer's `createSemVerForVersion()` method (in `Main.java`) and are the same values written to the `version` attribute of `app.xml`.

**Special case:** Branch installations (versions starting with `0.0.0-`, e.g., `0.0.0-main`) are passed through as-is and are not overridden by preferences.

### `prerelease`

Boolean string (`true` or `false`). When `true`, the launcher should include prerelease versions when checking for updates. Corresponds to the `prerelease` attribute in `app.xml`.

## Launcher Behavior

When the launcher starts and reads `app.xml`, it should:

1. Compute the `{fqpn}` from the `package` and `source` attributes in `app.xml`.
2. Check if `~/.jdeploy/preferences/{fqpn}/preferences.properties` exists.
3. If it exists, load the properties and override:
   - The `version` attribute with the `version` property value.
   - The `prerelease` attribute with the `prerelease` property value.
4. If the file does not exist or cannot be read, use the values from `app.xml` as-is (no change to current behavior).
5. Missing keys in the properties file should not override — only explicitly present keys take effect.

## Installer Behavior (Implemented)

### Save (after successful install)

At the end of `Main.install()`, after the version and prerelease values have been computed and written to app.xml, the installer saves them to the preferences file:

```java
new InstallerPreferencesService(fullyQualifiedPackageName)
        .save(appInfo().getNpmVersion(), appInfo().isNpmAllowPrerelease());
```

### Load (on startup, to pre-populate UI)

In `Main.buildUI()` and `Main.runHeadlessInstall()`, the installer loads any previously saved preferences and applies them to `InstallationSettings` before the UI is shown. The version string is reverse-mapped to the `AutoUpdateSettings` enum:

- `latest` -> `Stable`
- `^...` -> `MinorOnly`
- `~...` -> `PatchesOnly`
- exact version -> `Off`

### Delete (on uninstall)

In `Main.performUninstall()`, the preferences file and its directory are deleted.

## Relationship to app.xml

The `app.xml` `<app>` element contains these attributes (among others):

```xml
<app
  name="My App"
  package="my-app"
  source="https://github.com/user/repo"
  version="latest"
  prerelease="false"
  icon="data:image/png;base64,..."
  splash="data:image/png;base64,..."
  registry-url="..."
  fork="false"
  launcher-version="..."
  initial-app-version="..."
/>
```

The preferences file overrides **only** `version` and `prerelease`. All other attributes remain as defined in app.xml.

## Implementation Files

- **Service class:** `installer/src/main/java/ca/weblite/jdeploy/installer/services/InstallerPreferencesService.java`
- **Integration points in installer:** `installer/src/main/java/ca/weblite/jdeploy/installer/Main.java` (save in `install()`, load in `buildUI()` and `runHeadlessInstall()`, delete in `performUninstall()`)
- **UI fix:** `installer/src/main/java/ca/weblite/jdeploy/installer/views/DefaultInstallationForm.java` (combo box reads from settings instead of hardcoded index)
