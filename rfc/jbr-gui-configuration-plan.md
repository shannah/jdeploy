# RFC: JBR GUI Configuration - Implementation Plan

## Overview

This document outlines the plan to add GUI fields to the JDeployProjectEditor for configuring JetBrains Runtime (JBR) support. This builds on the existing JBR support implementation detailed in `/Users/shannah/projects/client4jgo/rfc/jetbrains-runtime-support.md`.

## Background

JBR support has been implemented in the launcher with the following configuration options in package.json:

```json
{
  "jdeploy": {
    "javaVersion": "21",
    "jdkProvider": "jbr",
    "jbrVariant": "jcef"
  }
}
```

Currently, users must manually edit the package.json file to configure these options. This plan adds GUI fields to make configuration easier and more discoverable.

## Objectives

1. Add a JDK Provider dropdown to select between Auto (default) and JBR
2. Add a JBR Variant dropdown that appears only when JBR is selected
3. Ensure proper validation and user guidance
4. Maintain backward compatibility with existing configurations
5. Follow existing GUI patterns in JDeployProjectEditor

## Design Philosophy

For JDK provider selection, we only expose two options:
- **Auto (Recommended)**: Let the runtime decide the best provider based on platform, architecture, and other constraints (Zulu on most platforms, Adoptium on legacy macOS, Liberica on ARM64)
- **JetBrains Runtime (JBR)**: Use JBR for applications that need JCEF, enhanced rendering, or other JBR-specific features

This simplification is intentional because:
- Users typically don't need to manually choose between Zulu, Adoptium, and Liberica
- The runtime can make better decisions based on platform-specific requirements
- Reduces complexity and potential for misconfiguration
- JBR is the only provider that requires explicit opt-in due to its specialized features

## Design

### UI Components

#### 1. JDK Provider ComboBox

**Location**: DetailsPanel.form (in the South panel, below "Requires Full JDK")

**Properties**:
- **Label**: "JDK Provider"
- **Type**: JComboBox
- **Options**:
  - "Auto (Recommended)"
  - "JetBrains Runtime (JBR)"
- **Default**: "Auto (Recommended)"
- **Tooltip**: "Auto: Automatically selects the best JDK provider for your platform (Zulu, Adoptium, or Liberica). JBR: Use JetBrains Runtime for applications requiring JCEF or enhanced rendering."

**Behavior**:
- When set to "Auto (Recommended)", no `jdkProvider` field is written to package.json (or removes it if present)
- When "JetBrains Runtime (JBR)" is selected, writes "jbr" to `jdeploy.jdkProvider`
- Selecting JBR shows the JBR Variant field; selecting Auto hides it

**Note**: Advanced users who need to explicitly set Zulu, Adoptium, or Liberica can still do so by manually editing package.json and setting the `JDEPLOY_JDK_PROVIDER` environment variable, but this is not recommended for typical use cases.

#### 2. JBR Variant ComboBox

**Location**: DetailsPanel.form (in the South panel, below JDK Provider)

**Properties**:
- **Label**: "JBR Variant"
- **Type**: JComboBox
- **Options**:
  - "Default"
  - "JCEF"
- **Default**: "Default"
- **Tooltip**: "JBR variant to use. Default uses standard or standard+SDK based on whether JDK is required. JCEF includes Chromium Embedded Framework for embedded browsers."
- **Visibility**: Only visible when JDK Provider is set to "JetBrains Runtime (JBR)"

**Behavior**:
- When "Default" is selected, the `jbrVariant` field is not written to package.json (or removed if present). The launcher will automatically choose between standard and standard+SDK based on whether "jdk" is selected.
- When "JCEF" is selected, writes "jcef" to `jdeploy.jbrVariant`
- If JDK Provider is not JBR, this field is hidden and the `jbrVariant` property is removed from package.json

### Layout

The fields will be added to the South panel of DetailsPanel.form in the following order:

```
JAR File                 [text field]              [Select...]
Java Version             [combo: 25, 24, 21, ...]
Requires JavaFX          [checkbox]
Requires Full JDK        [checkbox]
JDK Provider             [combo: Auto (Recommended), JetBrains Runtime (JBR)]
JBR Variant              [combo: JCEF, Standard, SDK, SDK+JCEF]  (conditional)
Homepage                 [text field]              [Verify]
Repository               [text field]
Directory:               [text field]
```

## Implementation Steps

### Phase 1: Update DetailsPanel.form

**File**: `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/DetailsPanel.form`

1. Open the form in IntelliJ IDEA GUI Designer
2. Locate the South panel (grid id="b960a")
3. Add new row specifications for the new fields:
   - After row 8 (Requires Full JDK), add:
     - Row 9: `top:4dlu:noGrow` (spacing)
     - Row 10: `center:max(d;4px):noGrow` (JDK Provider)
     - Row 11: `top:4dlu:noGrow` (spacing)
     - Row 12: `center:max(d;4px):noGrow` (JBR Variant)
4. Shift existing rows down (Homepage, Repository, etc.)
5. Add JDK Provider components:
   - Label at (row=10, col=0): "JDK Provider"
   - JComboBox at (row=10, col=2): binding="jdkProvider"
6. Add JBR Variant components:
   - Label at (row=12, col=0): "JBR Variant"
   - JComboBox at (row=12, col=2): binding="jbrVariant"

### Phase 2: Update DetailsPanel.java

**File**: `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/DetailsPanel.java`

1. Add private fields for the new components:
```java
private JComboBox jdkProvider;
private JComboBox jbrVariant;
```

2. Add getter methods:
```java
public JComboBox getJdkProvider() {
    return jdkProvider;
}

public JComboBox getJbrVariant() {
    return jbrVariant;
}
```

3. Regenerate the GUI code by rebuilding the form in IntelliJ IDEA
   - The `$$$setupUI$$$()` method will be automatically updated with the new components

### Phase 3: Update JDeployProjectEditor.java

**File**: `cli/src/main/java/ca/weblite/jdeploy/gui/JDeployProjectEditor.java`

#### 3.1 Add Fields to MainFields Inner Class

Around line 118-126, add to the MainFields class:

```java
private JComboBox jdkProvider, jbrVariant;
```

#### 3.2 Initialize JDK Provider Field

After line 1148 (after javaVersion initialization), add:

```java
// JDK Provider field
mainFields.jdkProvider = detailsPanel.getJdkProvider();
mainFields.jdkProvider.setModel(new DefaultComboBoxModel(new String[] {
    "Auto (Recommended)",
    "JetBrains Runtime (JBR)"
}));

// Load existing value from package.json
if (jdeploy.has("jdkProvider")) {
    String provider = jdeploy.getString("jdkProvider");
    if ("jbr".equals(provider)) {
        mainFields.jdkProvider.setSelectedItem("JetBrains Runtime (JBR)");
    } else {
        // For any other explicit provider (zulu, adoptium, liberica),
        // show as Auto in the GUI but preserve the value in package.json
        // This allows advanced users to set providers manually
        mainFields.jdkProvider.setSelectedItem("Auto (Recommended)");
    }
} else {
    mainFields.jdkProvider.setSelectedItem("Auto (Recommended)");
}

mainFields.jdkProvider.addItemListener(evt -> {
    String selected = (String) mainFields.jdkProvider.getSelectedItem();

    // Update package.json
    if ("Auto (Recommended)".equals(selected)) {
        // Remove jdkProvider to use automatic selection
        jdeploy.remove("jdkProvider");
    } else if ("JetBrains Runtime (JBR)".equals(selected)) {
        jdeploy.put("jdkProvider", "jbr");
    }

    // Show/hide JBR variant field
    updateJbrVariantVisibility();
    setModified();
});
```

#### 3.3 Initialize JBR Variant Field

After the JDK Provider initialization, add:

```java
// JBR Variant field
mainFields.jbrVariant = detailsPanel.getJbrVariant();
mainFields.jbrVariant.setModel(new DefaultComboBoxModel(new String[] {
    "JCEF (Recommended)",
    "Standard",
    "SDK",
    "SDK + JCEF"
}));

// Load existing value from package.json
if (jdeploy.has("jbrVariant")) {
    String variant = jdeploy.getString("jbrVariant");
    switch(variant) {
        case "jcef":
            mainFields.jbrVariant.setSelectedItem("JCEF (Recommended)");
            break;
        case "standard":
            mainFields.jbrVariant.setSelectedItem("Standard");
            break;
        case "sdk":
            mainFields.jbrVariant.setSelectedItem("SDK");
            break;
        case "sdk_jcef":
            mainFields.jbrVariant.setSelectedItem("SDK + JCEF");
            break;
        default:
            mainFields.jbrVariant.setSelectedItem("JCEF (Recommended)");
    }
} else {
    mainFields.jbrVariant.setSelectedItem("JCEF (Recommended)");
}

mainFields.jbrVariant.addItemListener(evt -> {
    String selected = (String) mainFields.jbrVariant.getSelectedItem();
    String variantValue = null;

    switch(selected) {
        case "JCEF (Recommended)":
            variantValue = "jcef";
            break;
        case "Standard":
            variantValue = "standard";
            break;
        case "SDK":
            variantValue = "sdk";
            break;
        case "SDK + JCEF":
            variantValue = "sdk_jcef";
            break;
    }

    if (variantValue != null) {
        jdeploy.put("jbrVariant", variantValue);
    }
    setModified();
});

// Set initial visibility
updateJbrVariantVisibility();
```

#### 3.4 Add Helper Method for Visibility

Add a new private method to JDeployProjectEditor:

```java
private void updateJbrVariantVisibility() {
    String selected = (String) mainFields.jdkProvider.getSelectedItem();
    boolean isJbr = "JetBrains Runtime (JBR)".equals(selected);

    // Find the label and field in the panel
    Container parent = mainFields.jbrVariant.getParent();
    if (parent != null) {
        // Find the label component (assumes standard layout where label precedes field)
        Component[] components = parent.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (components[i] == mainFields.jbrVariant) {
                // The label should be a few components before the combobox
                // This depends on the FormLayout structure
                // We'll make both the label and field visible/invisible
                mainFields.jbrVariant.setVisible(isJbr);

                // Find and toggle the label
                for (int j = i - 1; j >= 0; j--) {
                    if (components[j] instanceof JLabel) {
                        JLabel label = (JLabel) components[j];
                        if ("JBR Variant".equals(label.getText())) {
                            label.setVisible(isJbr);
                            break;
                        }
                    }
                }
                break;
            }
        }
        parent.revalidate();
        parent.repaint();
    }

    // If not JBR, remove jbrVariant from package.json
    if (!isJbr && jdeploy.has("jbrVariant")) {
        jdeploy.remove("jbrVariant");
    }
}
```

### Phase 4: Testing

#### 4.1 Build Testing ✅ COMPLETED

- ✅ Project compiles without errors
- ✅ No compilation warnings related to new code
- ✅ All dependencies resolved correctly
- ✅ JAR artifacts created successfully

#### 4.2 Manual Testing Checklist ⏭️ DEFERRED

**Note**: Manual GUI testing has been deferred and should be performed when the application is run. The following checklist can be used for verification:

- [ ] Open JDeployProjectEditor with a project that has no jdkProvider set
  - Verify JDK Provider defaults to "Auto (Recommended)"
  - Verify JBR Variant field is hidden

- [ ] Select each JDK Provider option
  - [ ] Auto (Recommended) → verify no jdkProvider in package.json (or removes it if present)
  - [ ] JBR → verify "jdkProvider": "jbr" in package.json and JBR Variant field appears

- [ ] With JBR selected, test each JBR Variant option
  - [ ] JCEF (Recommended) → verify "jbrVariant": "jcef" in package.json
  - [ ] Standard → verify "jbrVariant": "standard" in package.json
  - [ ] SDK → verify "jbrVariant": "sdk" in package.json
  - [ ] SDK + JCEF → verify "jbrVariant": "sdk_jcef" in package.json

- [ ] Switch from JBR to another provider
  - Verify JBR Variant field is hidden
  - Verify jbrVariant is removed from package.json

- [ ] Open a project with existing jdkProvider configuration
  - [ ] With "jdkProvider": "jbr" and "jbrVariant": "jcef" → verify both fields show correct values
  - [ ] With "jdkProvider": "zulu" (manually set) → verify "Auto (Recommended)" is shown in GUI, but value is preserved in package.json until user changes it
  - [ ] With no jdkProvider field → verify "Auto (Recommended)" is selected

- [ ] Test file watching
  - Manually edit package.json to change jdkProvider
  - Verify GUI updates to reflect the change

- [ ] Test modified flag
  - Verify changing JDK Provider marks the project as modified
  - Verify changing JBR Variant marks the project as modified

#### 4.3 Edge Cases ⏭️ DEFERRED

- [ ] Project with invalid jdkProvider value in package.json
  - Should default to "Auto (Recommended)"

- [ ] Project with jbrVariant but no jdkProvider set to "jbr"
  - Should remove jbrVariant when loading

- [ ] Rapidly switching between providers
  - Verify no race conditions or visual glitches

#### 4.4 Cross-platform Testing ⏭️ DEFERRED

- [ ] Test on macOS
- [ ] Test on Windows
- [ ] Test on Linux
- [ ] Verify form layout looks correct on all platforms
- [ ] Verify dropdown rendering is correct on all platforms

## Alternative Approaches Considered

### Alternative 1: Expose All Providers (Zulu, Adoptium, Liberica, JBR)

**Pros**:
- Gives users full control over provider selection
- Explicit about what's being used

**Cons**:
- Most users don't need this level of control
- Can lead to suboptimal choices (e.g., selecting Zulu on ARM64 Windows)
- More complex UI
- Runtime already has logic to select the best provider

**Decision**: Rejected. Only expose Auto and JBR options. Advanced users can still manually edit package.json if needed.

### Alternative 2: Add to Advanced Tab

**Pros**:
- Keeps the Details panel less cluttered
- Groups "advanced" configuration together

**Cons**:
- Less discoverable for users
- JDK Provider is a fundamental configuration, not "advanced"
- Would require creating a new tab or expanding existing structure

**Decision**: Rejected. JDK Provider is a core configuration option and should be easily accessible.

### Alternative 3: Use Text Fields Instead of Dropdowns

**Pros**:
- More flexible for future providers
- Smaller UI footprint

**Cons**:
- Error-prone (typos, invalid values)
- Less user-friendly
- No validation or guidance

**Decision**: Rejected. Dropdowns provide better UX and validation.

### Alternative 4: Single Dropdown Combining Provider and Variant

**Pros**:
- Simpler UI with one field
- Less vertical space

**Cons**:
- Confusing for non-JBR providers
- Doesn't scale well (4 JBR variants × N providers)
- Mixing concepts (provider vs variant)

**Decision**: Rejected. Two separate fields provide clearer mental model.

### Alternative 5: Use Checkbox Instead of Dropdown for JBR

**Pros**:
- Could automatically suggest JBR when JavaFX is checked
- Potentially helpful automation

**Cons**:
- Surprising behavior
- JBR is useful for more than just JavaFX
- Reduces user control

**Pros**:
- Simpler UI (single checkbox "Use JetBrains Runtime")
- Clear binary choice

**Cons**:
- Less extensible if we want to add more providers later
- Checkboxes are typically for boolean features, not provider selection
- Dropdown is more consistent with "Java Version" field above it

**Decision**: Rejected. Dropdown is more appropriate and extensible.

### Alternative 6: Auto-show JBR Variant Based on JavaFX Checkbox

**Pros**:
- Could automatically suggest JBR when JavaFX is checked
- Potentially helpful automation

**Cons**:
- Surprising behavior
- JBR is useful for more than just JavaFX
- Reduces user control

**Decision**: Rejected. Keep provider selection explicit and user-controlled.

## Implementation Timeline

### Phase 1: Form Design ✅ COMPLETED (30 minutes)
- ✅ Updated DetailsPanel.form to add new components
- ✅ Added 4 new row specifications (2 fields + 2 spacing)
- ✅ Added JDK Provider label and ComboBox at row 10
- ✅ Added JBR Variant label and ComboBox at row 12
- ✅ Shifted existing Homepage and Repository fields to rows 14-20
- ✅ Regenerated DetailsPanel.java with new fields

### Phase 2: Wiring ✅ COMPLETED (45 minutes)
- ✅ Added `jdkProvider` and `jbrVariant` to MainFields inner class
- ✅ Initialized JDK Provider ComboBox with proper model and options
- ✅ Initialized JBR Variant ComboBox with proper model and options
- ✅ Wired up ItemListener event handlers for both fields
- ✅ Implemented `updateJbrVariantVisibility()` helper method
- ✅ Added proper loading of existing values from package.json
- ✅ Added tooltips for both fields

### Phase 3: Build Verification ✅ COMPLETED (15 minutes)
- ✅ Compiled project successfully with no errors
- ✅ Verified all changes compile cleanly
- ✅ Build artifacts created successfully
- ⏭️ Manual GUI testing deferred (requires running the GUI application)

**Total Actual Time**: ~1.5 hours (faster than estimated)

## Implementation Summary

### UPDATE (2025-10-26): Simplified JBR Variant Options

The JBR variant dropdown has been simplified from four options to two:
- **"Default"**: No `jbrVariant` field in package.json. The launcher will automatically choose between standard and standard+SDK based on whether "jdk" is required.
- **"JCEF"**: Explicitly sets `"jbrVariant": "jcef"` for applications requiring the Chromium Embedded Framework.

This simplification reflects the most common use cases and makes the UI less complex while maintaining flexibility for advanced users who can still manually edit package.json.

### What Was Implemented

The implementation successfully added JBR configuration GUI fields to the JDeployProjectEditor with the following components:

#### 1. Form Changes (`DetailsPanel.form`)
- Added 4 new row specifications to accommodate the new fields
- Added JDK Provider label and ComboBox at row 10
- Added JBR Variant label and ComboBox at row 12
- Shifted existing Homepage and Repository fields down to rows 14-20
- ComboBox models include appropriate options with default values

#### 2. Generated Code (`DetailsPanel.java`)
- Added private fields: `jdkProvider` and `jbrVariant`
- Added public getter methods for both fields
- Updated `$$$setupUI$$$()` method with initialization code for:
  - JDK Provider ComboBox with DefaultComboBoxModel containing "Auto (Recommended)" and "JetBrains Runtime (JBR)"
  - JBR Variant ComboBox with DefaultComboBoxModel containing "JCEF (Recommended)", "Standard", "SDK", and "SDK + JCEF"
  - Proper FormLayout positioning for both components

#### 3. Editor Integration (`JDeployProjectEditor.java`)
- Added fields to MainFields inner class
- Implemented initialization logic that:
  - Loads existing values from package.json
  - Maps between GUI display names and package.json values
  - Handles backward compatibility (existing provider values shown as "Auto (Recommended)")
- Implemented event handlers (ItemListeners) that:
  - Update package.json when values change
  - Trigger modified flag
  - Call visibility update for JBR Variant field
- Implemented `updateJbrVariantVisibility()` helper method that:
  - Shows JBR Variant field only when JBR is selected
  - Hides field and label when Auto is selected
  - Removes jbrVariant from package.json when switching away from JBR
  - Handles null safety checks
- Added helpful tooltips for both fields

### Key Implementation Details

**Value Mapping:**
- GUI "Auto (Recommended)" ↔ package.json: field not present or removed
- GUI "JetBrains Runtime (JBR)" ↔ package.json: `"jdkProvider": "jbr"`
- GUI "Default" ↔ package.json: `jbrVariant` field not present or removed (launcher will use standard or standard+SDK based on "jdk" selection)
- GUI "JCEF" ↔ package.json: `"jbrVariant": "jcef"`

**Conditional Visibility:**
The JBR Variant field and its label are dynamically shown/hidden based on JDK Provider selection. This is implemented by:
1. Finding the parent container
2. Locating both the field and its associated label
3. Setting visibility on both components
4. Calling revalidate() and repaint() to update the UI

**Backward Compatibility:**
- Projects with no jdkProvider field → Shows "Auto (Recommended)"
- Projects with `"jdkProvider": "jbr"` → Shows "JetBrains Runtime (JBR)"
- Projects with other providers (zulu, adoptium, liberica) → Shows "Auto (Recommended)" but preserves value until user changes it

### Build Verification

- ✅ Project compiles successfully with `mvn clean compile`
- ✅ No new compilation errors or warnings
- ✅ JAR artifacts created successfully
- ✅ All dependencies resolved correctly

### Files Modified

1. `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/DetailsPanel.form` - Added GUI components
2. `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/DetailsPanel.java` - Generated code with new fields
3. `cli/src/main/java/ca/weblite/jdeploy/gui/JDeployProjectEditor.java` - Wiring and logic

### Next Steps

The implementation is functionally complete and compiles successfully. The following should be done when the application is next run:

1. **Manual GUI Testing**: Run the JDeployProjectEditor GUI and verify:
   - Fields appear correctly in the Details panel
   - Default values are correct
   - Selecting JBR shows the JBR Variant field
   - Selecting Auto hides the JBR Variant field
   - Changes are written to package.json correctly
   - Existing configurations load correctly

2. **Cross-Platform Verification**: Test the GUI on:
   - macOS (to verify FormLayout rendering)
   - Windows (to verify FormLayout rendering)
   - Linux (to verify FormLayout rendering)

3. **Integration Testing**: Verify that:
   - The modified flag works correctly
   - File watching picks up external package.json changes
   - The fields integrate well with save/load operations

## Risks and Mitigations

### Risk 1: FormLayout Complexity

**Risk**: The FormLayout in DetailsPanel may make dynamic visibility challenging.

**Mitigation**:
- Test visibility toggling early
- If FormLayout doesn't support dynamic visibility well, consider alternative layouts
- Fallback: Always show both fields, disable JBR Variant when not applicable

### Risk 2: GUI Designer Regeneration

**Risk**: Changes to the form might conflict with manual code changes.

**Mitigation**:
- Make all manual changes in JDeployProjectEditor, not in DetailsPanel
- Only use DetailsPanel for the GUI Designer-generated code
- Follow existing patterns in the codebase

### Risk 3: Backward Compatibility

**Risk**: Existing projects with manual jdkProvider configurations might not load correctly.

**Mitigation**:
- Handle missing values gracefully (default to "Default (Platform-specific)")
- Handle unknown values gracefully (fallback to default)
- Test with various existing configurations

## Future Enhancements

### Enhancement 1: Help Documentation Links

Add help buttons next to JDK Provider and JBR Variant fields linking to:
- JDK Provider: Documentation explaining when to use each provider
- JBR Variant: Documentation explaining JCEF and variant differences

### Enhancement 2: Smart Defaults

Could potentially auto-select JBR when certain conditions are met:
- Project has dependencies on JCEF
- Project explicitly requires embedded browser capabilities

### Enhancement 3: Provider Status Indicators

Show icons or status indicators for:
- Which provider is currently installed
- Which provider would be downloaded
- Download size estimates for each provider

### Enhancement 4: JavaFX + JBR Integration Warning

When JavaFX checkbox is enabled with JBR provider, show an info tooltip:
"JavaFX will be downloaded separately via Maven when using JBR"

## References

- JBR Support RFC: `/Users/shannah/projects/client4jgo/rfc/jetbrains-runtime-support.md`
- JDeployProjectEditor: `cli/src/main/java/ca/weblite/jdeploy/gui/JDeployProjectEditor.java`
- DetailsPanel Form: `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/DetailsPanel.form`
- DetailsPanel Java: `cli/src/main/java/ca/weblite/jdeploy/gui/tabs/DetailsPanel.java`

## Status

**Status**: ✅ COMPLETED

**Created**: 2025-10-23

**Implementation Started**: 2025-10-23

**Implementation Completed**: 2025-10-23

**Implementation Status**: All phases completed successfully

## Appendix A: JSON Schema Changes

No changes to JSON schema required. The fields already exist:

```json
{
  "jdeploy": {
    "jdkProvider": "string (optional)",
    "jbrVariant": "string (optional, only when jdkProvider=jbr)"
  }
}
```

Valid values:
- `jdkProvider`: "jbr" | undefined (for GUI users)
  - Advanced users can still manually set "zulu", "adoptium", or "liberica" in package.json
  - The GUI will show these as "Auto (Recommended)" but preserve the value
- `jbrVariant`: "standard" | "jcef" | "sdk" | "sdk_jcef" | undefined

## Appendix B: Code Patterns Reference

### Example: Existing ComboBox Initialization

From JDeployProjectEditor.java (lines 1138-1148):

```java
mainFields.javaVersion = detailsPanel.getJavaVersion();
mainFields.javaVersion.setEditable(true);
if (jdeploy.has("javaVersion")) {
    mainFields.javaVersion.setSelectedItem(String.valueOf(jdeploy.get("javaVersion")));
} else {
    mainFields.javaVersion.setSelectedItem("21");
}
mainFields.javaVersion.addItemListener(evt -> {
    jdeploy.put("javaVersion", mainFields.javaVersion.getSelectedItem());
    setModified();
});
```

### Example: Conditional Field Visibility

From JDeployProjectEditor.java (directory associations):

```java
directoryAssociationFields.enableCheckbox.addItemListener(evt -> {
    boolean enabled = directoryAssociationFields.enableCheckbox.isSelected();
    directoryAssociationFields.roleComboBox.setEnabled(enabled);
    directoryAssociationFields.descriptionField.setEnabled(enabled);
    // ... update package.json
});
```

## Appendix C: UI Mockup

```
┌─────────────────────────────────────────────────────────┐
│ Details Tab                                             │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ [Icon]  Name:         [my-app                        ] │
│         Path:         [/path/to/project              ] │
│         Version:      [1.0.0                         ] │
│         Title:        [My Application                ] │
│         Author:       [John Doe                      ] │
│         Description:  [A sample Java application     ] │
│                       [                              ] │
│         License:      [MIT                           ] │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│ JAR File:             [build/libs/app.jar  ] [Select...] │
│ Java Version:         [21 ▼                          ] │
│ ☐ Requires JavaFX                                      │
│ ☐ Requires Full JDK                                    │
│ JDK Provider:         [JetBrains Runtime (JBR) ▼     ] │
│ JBR Variant:          [JCEF (Recommended) ▼          ] │ ← Only visible when JBR selected
│ Homepage:             [https://example.com ] [Verify  ] │
│ Repository:           [https://github.com/user/repo  ] │
│ Directory:            [                              ] │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

When JDK Provider is set to "Auto (Recommended)":

```
┌─────────────────────────────────────────────────────────┐
│ JAR File:             [build/libs/app.jar  ] [Select...] │
│ Java Version:         [21 ▼                          ] │
│ ☐ Requires JavaFX                                      │
│ ☐ Requires Full JDK                                    │
│ JDK Provider:         [Auto (Recommended) ▼          ] │
│ Homepage:             [https://example.com ] [Verify  ] │
│ Repository:           [https://github.com/user/repo  ] │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

Note: JBR Variant field is hidden when JDK Provider is set to "Auto (Recommended)".
