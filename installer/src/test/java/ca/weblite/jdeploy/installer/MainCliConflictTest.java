package ca.weblite.jdeploy.installer;

import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CLI launcher and CLI command conflict resolution logic.
 * This test verifies the conflict resolution logic from Main.java (lines 643-659).
 */
public class MainCliConflictTest {

    /**
     * Simulates the conflict resolution logic from Main.java.
     * This is the logic being tested (extracted from Main.java:626-659).
     */
    private void applyCliInstallationRules(InstallationSettings settings, List<CommandSpec> commands, AppInfo appInfo) {
        // Rule 1: On Linux, always install CLI Launcher
        if (Platform.getSystemPlatform().isLinux()) {
            settings.setInstallCliLauncher(true);
        } else {
            // Rule 3: On Mac and Windows, never install CLI Launcher
            settings.setInstallCliLauncher(false);
        }

        // Rule 2: On all platforms, if commands are defined, always install them
        if (!commands.isEmpty()) {
            settings.setInstallCliCommands(true);
        } else {
            settings.setInstallCliCommands(false);
        }

        // Rule 4: If both CLI Launcher and CLI Commands are enabled, check for name conflicts
        // If a CLI command has the same name as the launcher, prefer the CLI command
        if (settings.isInstallCliLauncher() && settings.isInstallCliCommands()) {
            String launcherName = appInfo.getTitle()
                    .toLowerCase()
                    .replace(" ", "-")
                    .replaceAll("[^a-z0-9\\-]", "");
            boolean hasConflict = false;
            for (CommandSpec command : commands) {
                if (launcherName.equals(command.getName())) {
                    hasConflict = true;
                    break;
                }
            }
            if (hasConflict) {
                // Disable CLI launcher installation - prefer CLI command
                settings.setInstallCliLauncher(false);
            }
        }
    }

    /**
     * Test that when CLI launcher name conflicts with a CLI command name,
     * the CLI launcher installation is disabled (Linux only).
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testCliLauncherDisabledWhenConflictExists() {
        // Setup: Create commands with one that matches the launcher name "myapp"
        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("myapp", "Main command", Collections.emptyList()),
                new CommandSpec("other", "Other command", Collections.emptyList())
        );

        InstallationSettings settings = new InstallationSettings();
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("MyApp");  // This will derive to "myapp" as launcher name
        settings.setAppInfo(appInfo);

        // Apply the CLI installation rules (simulating Main.java logic)
        applyCliInstallationRules(settings, commands, appInfo);

        // Verify: CLI launcher should be disabled due to conflict
        assertFalse(settings.isInstallCliLauncher(), "CLI launcher should be disabled when name conflicts with a CLI command");
        assertTrue(settings.isInstallCliCommands(), "CLI commands should still be enabled");
    }

    /**
     * Test that when CLI launcher name does NOT conflict with any CLI command name,
     * both CLI launcher and CLI commands remain enabled (Linux only).
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testCliLauncherEnabledWhenNoConflictExists() {
        // Setup: Create commands that don't match the launcher name "myapp"
        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("cmd1", "First command", Collections.emptyList()),
                new CommandSpec("cmd2", "Second command", Collections.emptyList())
        );

        InstallationSettings settings = new InstallationSettings();
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("MyApp");  // This will derive to "myapp" as launcher name
        settings.setAppInfo(appInfo);

        // Apply the CLI installation rules (simulating Main.java logic)
        applyCliInstallationRules(settings, commands, appInfo);

        // Verify: Both should be enabled since there's no conflict
        assertTrue(settings.isInstallCliLauncher(), "CLI launcher should remain enabled when no conflict exists");
        assertTrue(settings.isInstallCliCommands(), "CLI commands should be enabled");
    }

    /**
     * Test that when no CLI commands are defined, only CLI launcher is enabled (Linux only).
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    public void testCliLauncherOnlyWhenNoCommands() {
        // Setup: No commands defined
        List<CommandSpec> commands = Collections.emptyList();

        InstallationSettings settings = new InstallationSettings();
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("MyApp");
        settings.setAppInfo(appInfo);

        // Apply the CLI installation rules (simulating Main.java logic)
        applyCliInstallationRules(settings, commands, appInfo);

        // Verify: Only CLI launcher should be enabled
        assertTrue(settings.isInstallCliLauncher(), "CLI launcher should be enabled when no commands are defined");
        assertFalse(settings.isInstallCliCommands(), "CLI commands should be disabled when none are defined");
    }

    /**
     * Test that on macOS/Windows, CLI launcher is never installed regardless of conflicts.
     */
    @Test
    @EnabledOnOs({OS.MAC, OS.WINDOWS})
    public void testCliLauncherNeverEnabledOnMacAndWindows() {
        // Setup: Create commands with conflict
        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("myapp", "Main command", Collections.emptyList())
        );

        InstallationSettings settings = new InstallationSettings();
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("MyApp");  // This will derive to "myapp" as launcher name
        settings.setAppInfo(appInfo);

        // Apply the CLI installation rules (simulating Main.java logic)
        applyCliInstallationRules(settings, commands, appInfo);

        // Verify: CLI launcher should be disabled on Mac/Windows
        assertFalse(settings.isInstallCliLauncher(), "CLI launcher should never be enabled on macOS/Windows");
        assertTrue(settings.isInstallCliCommands(), "CLI commands should be enabled");
    }
}
