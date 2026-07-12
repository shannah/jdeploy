# Plan: "Update Settings" panel in jDeploy GUI

Expose two launcher-update features (already supported by the client4jgo launcher)
as first-class, GUI-editable settings in jDeploy:

1. **Auto-update mode** — `auto` (silent update on launch, the default) vs `prompt`
   (ask the user on launch when an update is available).
2. **Minimum initial app version** — forces users on an older launcher to download a
   fresh installer + do a full update. Either a hard-coded version or auto-set to the
   published version at publish time.

Plus one companion field confirmed in scope: **Require full launcher update**
(`requireLauncherUpdate`).

Out of scope for this PR (offered, not selected): exposing `minLauncherVersion` in the
panel, and stamping `app-update-mode` into app.xml.

---

## How the launcher consumes these (from client4jgo research)

| Concept | Launcher reads from | Exact key/attr | Values |
|---|---|---|---|
| Auto-update mode | `~/.jdeploy/preferences/<FQPN>/preferences.properties` **and** `app.xml` attr | `app-update-mode` | `auto` / `prompt` (only literal `prompt` => prompt; everything else => auto) |
| Min initial app version | published version's `jdeploy` metadata | `minLauncherInitialAppVersion` | semver string |
| Require launcher update | published `jdeploy` metadata | `requireLauncherUpdate` | bool |
| Min launcher version | published `jdeploy` metadata | `minLauncherVersion` | semver string |
| Initial app version (stamped) | `app.xml` attr | `initial-app-version` | already emitted by `LauncherWriterHelper` |

`<FQPN>` = package name for npm, or `md5(source) + "." + packageName` for GitHub
(jDeploy already computes this identically — `Main.java:466`, and
`InstallerPreferencesService` already writes that exact prefs file).

### Decisions taken
- Auto-update mode is delivered by the **installer writing `preferences.properties`**
  (not baking into app.xml).
- Min-version "auto" mode is a **sentinel in package.json, resolved to the concrete
  version in the *published* package.json at publish time** (developer's source file is
  never rewritten).
- package.json key for the mode is **`appUpdateMode`** (camelCase, consistent with the
  other `jdeploy.*` keys); the installer translates it to the launcher's
  `app-update-mode` prefs key.
- Companion field **`requireLauncherUpdate`** is in scope. `minLauncherVersion` is not.
- We are **not** also stamping `app-update-mode` into app.xml in this PR.

---

## package.json schema (under the `jdeploy` object)

```jsonc
"jdeploy": {
  "appUpdateMode": "prompt",                 // "auto" (default, omitted) | "prompt"
  "minLauncherInitialAppVersion": "1.4.0",   // explicit version
  // OR auto mode (mutually exclusive with an explicit version):
  "minLauncherInitialAppVersionMode": "latest",
  "requireLauncherUpdate": true              // optional
}
```

At publish time, if `minLauncherInitialAppVersionMode == "latest"`, jDeploy writes
`minLauncherInitialAppVersion = <version being published>` into the **published**
package.json and drops the sentinel. The on-disk source package.json keeps the sentinel.

---

## Implementation

### Phase 1 — GUI panel (cli module)

**New file:** `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/UpdateSettingsPanel.java`
Modeled on `CheerpJSettingsPanel` (checkbox + nested fields, raw `JSONObject` load/save,
`SwingUtils.addChangeListenerTo` for text fields, `ItemListener` for toggles/radios).

Controls:
- **Auto-update behaviour** — radio group / combo: "Update automatically on launch
  (default)" vs "Prompt me before updating". A short explanatory label. Maps to
  `appUpdateMode` (`auto` => remove key; `prompt` => `jdeploy.put("appUpdateMode","prompt")`).
- **Minimum initial app version** — radio group:
  - "No minimum" (default) — remove both keys.
  - "Auto-set to latest on publish" — `minLauncherInitialAppVersionMode = "latest"`,
    remove explicit key.
  - "Require at least:" + text field — `minLauncherInitialAppVersion = <text>`,
    remove the mode key.
  Plus a one-paragraph explanation of what it does (forces full launcher reinstall for
  users below the threshold).
- **Require full launcher update** checkbox — `requireLauncherUpdate` (true => set;
  false => remove).

`load(JSONObject jdeploy)` / `save(JSONObject jdeploy)` follow the established
remove-when-default convention; `getRoot()` returns the panel; `addChangeListener`
stores the listener and fires on every edit.

**Register the panel:** in `JDeployProjectEditor.createPanelRegistry()` (~line 506-704),
add a field `private UpdateSettingsPanel updateSettingsPanel;` and a registration block:

```java
updateSettingsPanel = new UpdateSettingsPanel();
registry.register(NavigablePanelAdapter.forJdeployPanel(
    "Updates",
    MenuBarBuilder.JDEPLOY_WEBSITE_URL + "docs/help/#updates",
    FontIcon.of(Material.SYSTEM_UPDATE),
    updateSettingsPanel.getRoot(),
    json -> updateSettingsPanel.load(json),
    json -> updateSettingsPanel.save(json),
    listener -> updateSettingsPanel.addChangeListener(listener)
));
```

Load/save/dirty-tracking/file-write are all automatic via the registry — no changes to
`handleSave` or the JSON I/O are required.

### Phase 2 — Model accessors (shared module, optional but recommended)

Add typed getters to
`shared/src/main/java/ca/weblite/jdeploy/models/JDeployProject.java` for non-GUI
consumers (publish + installer):
`getAppUpdateMode()`, `getMinLauncherInitialAppVersion()`,
`getMinLauncherInitialAppVersionMode()`, `isRequireLauncherUpdate()` — each reading
from the `jdeploy` object with sensible defaults, matching the existing `isSingleton()`
pattern.

### Phase 3 — Publish-time sentinel resolution (cli module)

In `cli/src/main/java/ca/weblite/jdeploy/publishing/BasePublishDriver.java`
`prepareDirectory(...)` (where it already does
`jdeployObj = packageJSON.getJSONObject("jdeploy")` and writes the published
package.json):

```java
if ("latest".equals(jdeployObj.optString("minLauncherInitialAppVersionMode", ""))) {
    jdeployObj.put("minLauncherInitialAppVersion", packageJSON.getString("version"));
    jdeployObj.remove("minLauncherInitialAppVersionMode");
}
```

This only touches the published copy in `publishDir`, exactly like the existing
`commandName` mutation. `appUpdateMode` and `requireLauncherUpdate`
are already present verbatim and need no transformation here — the launcher reads
`minLauncherInitialAppVersion`/`requireLauncherUpdate` straight from the published
metadata.

### Phase 4 — Installer writes `app-update-mode` to preferences (installer + read path)

**a. Read the mode from app.xml/package metadata into AppInfo.**
- Add `appUpdateMode` field + getter/setter to
  `shared/src/main/java/ca/weblite/jdeploy/app/AppInfo.java`.
- Expose it from the published metadata reader
  `installer/.../npm/NPMPackageVersion.java` (e.g. `getAppUpdateMode()` reading
  `jdeploy().optString("appUpdateMode","auto")`).
- In `installer/.../Main.java` (near the `initialAppVersion`/`launcherVersion` block
  ~line 1983-1993), set `appInfo().setAppUpdateMode(npmPackageVersion().getAppUpdateMode())`.

**b. Persist it to preferences at install time.**
Extend `installer/.../services/InstallerPreferencesService.java`:
- Add `KEY_APP_UPDATE_MODE = "app-update-mode"`.
- Add an overload `save(String version, boolean prerelease, String appUpdateMode)` that
  also writes the `app-update-mode` line (only when `prompt`; for `auto`/empty, leave it
  absent or write `auto` — both resolve to auto in the launcher). This service already
  writes to the exact `~/.jdeploy/preferences/<FQPN>/preferences.properties` file the
  launcher reads, so no path/FQPN logic is new.
- Update the existing call site in `Main.java` (~line 2356-2358) to pass
  `appInfo().getAppUpdateMode()`.
- Preserve `app-update-mode` across reinstalls when the published value is unset
  (don't clobber a user's existing pref unnecessarily — read-merge-write).

> Note: the launcher's properties reader is a hand-rolled parser; Java's
> `Properties.store` escapes `=`/`:` and writes a date comment, but plain
> `key=value` lines (and `#` comments) are read fine, so the existing
> `Properties`-based writer is compatible. Keep values simple (`auto`/`prompt`).

### Phase 5 — Tests

- **cli:** unit test for `UpdateSettingsPanel.load/save` round-trips (mode, the three
  min-version states incl. sentinel, requireLauncherUpdate;
  remove-when-default behaviour). Test publish sentinel resolution in
  `BasePublishDriver` (or via an existing mock-network publish test) — `latest` =>
  concrete version in published package.json, source untouched.
- **installer:** unit test that `InstallerPreferencesService` writes
  `app-update-mode=prompt` and that `auto` leaves it absent/auto; round-trip read.
- **shared:** `JDeployProject` getter tests for the new keys.

### Phase 6 — Docs

- Update the jDeploy help docs page anchor referenced by the panel (`#updates`).
- Note the new `jdeploy.*` keys in any package.json reference docs.
- Optionally update `client4jgo/xsd/app.xsd` (out of date — missing `app-update-mode`)
  if app.xml stamping is added later; not required for this PR.

---

## Files touched (summary)

**jdeploy / cli**
- `gui/tabs/UpdateSettingsPanel.java` (new)
- `gui/JDeployProjectEditor.java` (register panel)
- `publishing/BasePublishDriver.java` (sentinel resolution)

**jdeploy / shared**
- `app/AppInfo.java` (appUpdateMode field)
- `models/JDeployProject.java` (typed getters)

**jdeploy / installer**
- `npm/NPMPackageVersion.java` (getAppUpdateMode)
- `Main.java` (read mode, pass to prefs save)
- `services/InstallerPreferencesService.java` (write app-update-mode)

**Tests** across cli/installer/shared as above.

No client4jgo changes are required — it already consumes every key/attribute involved.

## Risks / notes
- `minLauncherInitialAppVersion` is enforced *per published version's* metadata, so it
  only affects users when a **new** version carries the constraint — correct behaviour
  (a future release says "you must be on launcher initial-app-version >= X").
- The XSD in client4jgo lacks `app-update-mode`; harmless today (we use prefs, not the
  attr) but worth fixing if app.xml stamping is added later.
- `appUpdateMode=prompt` only triggers a prompt on GUI/first-launch paths and when an
  update is actually available; headless stays silent — matches launcher logic.
