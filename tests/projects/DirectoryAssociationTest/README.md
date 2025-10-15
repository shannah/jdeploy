# Directory Association Test Project

## Purpose

This test project validates that jDeploy correctly handles directory associations on all platforms (Windows, macOS, Linux).

## Configuration

The `package.json` includes:

```json
"documentTypes": [
  {
    "type": "directory",
    "role": "Editor",
    "description": "Open folder in Directory Test App"
  },
  {
    "extension": "txt",
    "mimetype": "text/plain",
    "editor": true
  }
]
```

This configures the app to:
1. Register as a handler for directories (folders)
2. Also handle `.txt` files (for comparison/testing)

## Test Application

`DirectoryTestApp.java` is a simple Swing application that:
- Displays all command-line arguments received
- Shows details about each argument (exists, is directory, is file, absolute path)
- Helps verify that directory paths are correctly passed to the application

## Manual Testing Checklist

### Windows

After installing with `jdeploy install`:

- [ ] Right-click on a folder → "Open with Directory Test App" appears in context menu
- [ ] Selecting the context menu option launches the app
- [ ] The app receives the correct directory path as an argument
- [ ] The app shows "Is Directory: true" for the passed path
- [ ] Right-click in empty space inside a folder → "Open folder in Directory Test App" appears
- [ ] Icon appears correctly in context menu (if configured)
- [ ] After uninstall, context menu entries are removed

### macOS

After installing with `jdeploy install`:

- [ ] Drag a folder onto the app icon → App opens
- [ ] The app receives the correct directory path as an argument
- [ ] The app shows "Is Directory: true" for the passed path
- [ ] Right-click folder → "Open With" → "Directory Test App" appears
- [ ] Selecting "Open With" launches the app correctly
- [ ] After uninstall, app no longer appears in "Open With"

### Linux

After installing with `jdeploy install`:

- [ ] Right-click folder (GNOME) → "Open With" → "Directory Test App" appears
- [ ] Right-click folder (KDE) → "Open With" → "Directory Test App" appears
- [ ] Selecting "Open With" launches the app
- [ ] The app receives the correct directory path as an argument
- [ ] The app shows "Is Directory: true" for the passed path
- [ ] `update-desktop-database` was run during installation
- [ ] After uninstall, app no longer appears in "Open With"

## Building

```bash
# Compile the Java application
javac -d target src/DirectoryTestApp.java

# Create JAR
cd target
jar cfe DirectoryTestApp.jar DirectoryTestApp DirectoryTestApp.class
cd ..

# Install locally for testing
jdeploy install
```

## Expected Behavior

When opening a directory with this app:

1. **App launches** - A Swing window appears
2. **Arguments displayed** - Shows "Number of arguments: 1"
3. **Path shown** - Displays the full path to the directory
4. **Validation** - Shows:
   - Exists: true
   - Is Directory: true
   - Is File: false
   - Absolute Path: /full/path/to/directory

## Notes

- This test also includes a `.txt` file association to verify mixed associations work
- The app works without arguments (for testing app launch independent of associations)
- Console output also prints arguments for debugging
