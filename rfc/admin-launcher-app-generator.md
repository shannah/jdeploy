# Admin Launcher App Generator for macOS

## Overview
Create a service that generates an administrative launcher app for existing macOS .app bundles. The admin launcher will use AppleScript to prompt for administrator privileges before launching the original application.

## Requirements
- Input: Existing .app bundle
- Output: New admin launcher .app bundle with " (Admin)" suffix
- Admin app should use the same icon as the original app
- Admin app should be saved in the same directory as the source app
- Only runs on macOS

## Implementation Plan

### Phase 1: Core Service Implementation
1. Create `AdminLauncherGenerator.java` service class
2. Methods needed:
   - `generateAdminLauncher(File sourceApp)` - Main entry point
   - `createAppleScript(String targetAppPath)` - Generate AppleScript content
   - `compileAppleScript(String scriptContent, File outputApp)` - Compile to .app
   - `copyIcon(File sourceApp, File adminApp)` - Copy icon from source to admin app

### Phase 2: AppleScript Generation
1. Generate AppleScript that:
   - Accepts command-line arguments
   - Locates the original app's executable
   - Requests administrator privileges
   - Launches the original app with elevated permissions
2. Handle different executable names (not just Client4JLauncher)

### Phase 3: App Bundle Creation
1. Use `osacompile` to compile AppleScript into .app bundle
2. Name pattern: "[OriginalName] (Admin).app"
3. Place in same directory as source app

### Phase 4: Icon Copying
1. Extract icon from source app's Info.plist
2. Copy icon file to admin app bundle
3. Update admin app's Info.plist to reference the icon

## Technical Details

### AppleScript Template
```applescript
on run argv
    set launcherPath to "TARGET_APP_EXECUTABLE_PATH"

    set cmd to quoted form of launcherPath
    repeat with a in argv
        set cmd to cmd & " " & quoted form of a
    end repeat

    do shell script cmd with administrator privileges
end run
```

### File Structure
```
src/main/java/ca/weblite/jdeploy/installer/mac/
└── AdminLauncherGenerator.java
```

### Key Methods

#### generateAdminLauncher
- Validate input app exists and is a directory
- Extract executable path from Info.plist
- Generate AppleScript with correct paths
- Compile AppleScript to admin app
- Copy icon from source to admin app
- Return path to generated admin app

#### Finding the Executable
- Parse Info.plist for CFBundleExecutable key
- Construct full path: sourceApp/Contents/MacOS/[executable]

#### Error Handling
- Check if running on macOS
- Verify source app is valid bundle
- Handle osacompile failures
- Handle icon copying failures

## Testing Considerations
- Test with various app bundles
- Test with apps with different executable names
- Test icon copying with different icon formats
- Test command-line argument passing
- Verify admin privileges are properly requested

## Security Considerations
- Generated AppleScript should not expose sensitive information
- Ensure proper quoting of paths to prevent injection
- Admin launcher should only execute the specific target app

## Future Enhancements
- Support for custom admin app names
- Option to specify different icon
- Support for embedding additional metadata
- Localization of admin prompt message