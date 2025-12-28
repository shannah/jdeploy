package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.cli.CollisionAction;
import ca.weblite.jdeploy.app.AppInfo;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

public class MacCliCommandInstallerTest {

    private MacCliCommandInstaller installer;
    private File tempDir;
    private File launcherDir;
    private File launcherPath;
    private File binDir;
    private File homeDir;

    @BeforeEach
    public void setUp() throws IOException {
        installer = new MacCliCommandInstaller();
        tempDir = Files.createTempDirectory("jdeploy-mac-test-").toFile();
        launcherDir = new File(tempDir, "app");
        launcherDir.mkdirs();
        launcherPath = new File(launcherDir, "launcher");
        launcherPath.createNewFile();
        launcherPath.setExecutable(true);

        binDir = new File(new File(tempDir, ".local"), "bin");
        binDir.mkdirs();

        homeDir = new File(tempDir, "home");
        homeDir.mkdirs();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempDir != null && tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void testInstallCommandsWithCliCommands() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));
        commands.add(new CommandSpec("othercmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(2, created.size());
        assertTrue(new File(binDir, "mycmd").exists());
        assertTrue(new File(binDir, "othercmd").exists());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testInstallCommandsWithCliLauncher() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(false);
        settings.setInstallCliLauncher(true);
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("My App");
        settings.setAppInfo(appInfo);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Should create at least one symlink for the launcher
        assertTrue(created.size() >= 1);
        assertTrue(settings.isCommandLineSymlinkCreated());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testInstallCommandsWithBothOptions() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(true);
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("My App");
        settings.setAppInfo(appInfo);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Should create command script + launcher symlink
        assertTrue(created.size() >= 2);
    }

    @Test
    public void testInstallCommandsWithEmptyList() {
        List<CommandSpec> commands = new ArrayList<>();

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(0, created.size());
    }

    @Test
    public void testInstallCommandsNullLauncherPath() {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);

        List<File> created = installer.installCommands(null, commands, settings);

        assertEquals(0, created.size());
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testCommandScriptEscapeDoubleQuotes() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        // Create launcher with double quotes in path
        File specialDir = new File(tempDir, "My \"Special\" App");
        specialDir.mkdirs();
        File specialLauncher = new File(specialDir, "launcher");
        specialLauncher.createNewFile();
        specialLauncher.setExecutable(true);

        installer.installCommands(specialLauncher, commands, settings);

        File scriptFile = new File(binDir, "mycmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        // Should have escaped quotes
        assertTrue(content.contains("\\\""));
    }

    @Test
    public void testCommandScriptWithArguments() throws IOException {
        List<String> args = new ArrayList<>();
        args.add("--flag");
        args.add("value");

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", args));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File scriptFile = new File(binDir, "mycmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX + "mycmd"));
        assertTrue(content.contains("\"$@\""));
    }

    @Test
    public void testAddToPathBash() {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(bashrc.exists(), "bashrc was not created");
    }

    @Test
    public void testAddToPathZsh() {
        String shell = "/bin/zsh";
        String pathEnv = "/usr/bin:/bin";
        File zshrc = new File(homeDir, ".zshrc");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(zshrc.exists());
    }

    @Test
    public void testAddToPathAlreadyInPath() {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:" + binDir.getAbsolutePath() + ":/bin";
        File bashrc = new File(homeDir, ".bashrc");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
    }

    @Test
    public void testAddToPathAlreadyInConfig() throws IOException {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");
        bashrc.createNewFile();

        // Pre-populate bashrc with the path
        Files.write(bashrc.toPath(), ("export PATH=\"$HOME/.local/bin:$PATH\"\n").getBytes(StandardCharsets.UTF_8));

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
    }

    @Test
    public void testAddToPathFish() {
        String shell = "/usr/bin/fish";
        String pathEnv = "/usr/bin:/bin";
        File profile = new File(homeDir, ".profile");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(profile.exists(), "Fish shell should use .profile for POSIX compatibility");
    }

    @Test
    public void testAddToPathUnknownShell() {
        String shell = "/bin/unknown";
        String pathEnv = "/usr/bin:/bin";
        File profile = new File(homeDir, ".profile");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(profile.exists(), "Unknown shell should use .profile for POSIX compatibility");
    }

    @Test
    public void testAddToPathNullShell() {
        String shell = null;
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
    }

    @Test
    public void testUninstallCommandsRemovesScripts() throws IOException {
        // First install some commands
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("cmd1", new ArrayList<>()));
        commands.add(new CommandSpec("cmd2", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);
        assertEquals(2, created.size());

        // Verify files exist
        assertTrue(new File(binDir, "cmd1").exists());
        assertTrue(new File(binDir, "cmd2").exists());

        // Now uninstall - pass launcherDir since metadata is stored there
        installer.uninstallCommands(launcherDir);

        // Verify files are removed
        assertFalse(new File(binDir, "cmd1").exists());
        assertFalse(new File(binDir, "cmd2").exists());
    }

    @Test
    public void testUninstallCommandsWithNullDir() {
        // Should not throw exception
        assertDoesNotThrow(() -> installer.uninstallCommands(null));
    }

    @Test
    public void testUninstallCommandsWithNonExistentDir() {
        File nonExistent = new File(tempDir, "does-not-exist");
        // Should not throw exception
        assertDoesNotThrow(() -> installer.uninstallCommands(nonExistent));
    }

    @Test
    public void testMetadataFileCreated() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File metadataFile = new File(launcherDir, CliInstallerConstants.CLI_METADATA_FILE);
        assertTrue(metadataFile.exists());

        String content = new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8);
        JSONObject metadata = new JSONObject(content);

        assertTrue(metadata.has(CliInstallerConstants.CREATED_WRAPPERS_KEY));
        JSONArray wrappers = metadata.getJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);
        assertEquals(1, wrappers.length());
        assertEquals("mycmd", wrappers.getString(0));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testDeriveCommandNameFromAppInfo() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(false);
        settings.setInstallCliLauncher(true);
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("My Awesome App");
        settings.setAppInfo(appInfo);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Should derive command name from title
        assertTrue(created.stream().anyMatch(f -> f.getName().contains("my") && f.getName().contains("awesome")));
    }

    @Test
    public void testCommandScriptRemovesExisting() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        // Create initial script
        File scriptFile = new File(binDir, "mycmd");
        scriptFile.createNewFile();
        Files.write(scriptFile.toPath(), "old content".getBytes());

        // Install again, should replace
        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(1, created.size());
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(content.contains("old content"));
        assertTrue(content.contains(launcherPath.getAbsolutePath()));
    }

    @Test
    public void testCommandScriptExecutable() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File scriptFile = new File(binDir, "mycmd");
        assertTrue(scriptFile.canExecute());
    }

    @Test
    public void testMetadataContainsTimestamp() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File metadataFile = new File(launcherDir, CliInstallerConstants.CLI_METADATA_FILE);
        String content = new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8);
        JSONObject metadata = new JSONObject(content);

        assertTrue(metadata.has("installedAt"));
        assertTrue(metadata.getLong("installedAt") > 0);
    }

    @Test
    public void testPathUpdatePersistsInMetadata() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File metadataFile = new File(launcherDir, CliInstallerConstants.CLI_METADATA_FILE);
        String content = new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8);
        JSONObject metadata = new JSONObject(content);

        assertTrue(metadata.has(CliInstallerConstants.PATH_UPDATED_KEY));
    }

    @Test
    public void testInstallCommandsUpdateSettings() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(true);
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("Test App");
        settings.setAppInfo(appInfo);

        installer.installCommands(launcherPath, commands, settings);

        assertTrue(settings.isCommandLineSymlinkCreated() || settings.isAddedToPath());
    }

    @Test
    public void testMetadataSavedToAppDirNotBinDir() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        // Metadata should be in launcherDir (appDir), not binDir
        File metadataInAppDir = new File(launcherDir, CliInstallerConstants.CLI_METADATA_FILE);
        File metadataInBinDir = new File(binDir, CliInstallerConstants.CLI_METADATA_FILE);
        
        assertTrue(metadataInAppDir.exists(), "Metadata should be saved to appDir (launcher parent)");
        // Note: binDir might also have metadata if appDir equals binDir, but in this test they differ
        assertFalse(metadataInBinDir.exists(), "Metadata should NOT be saved to binDir when appDir differs");
    }

    @Test
    public void testUninstallFromAppDirRemovesScriptsFromBinDir() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("cmd1", new ArrayList<>()));
        commands.add(new CommandSpec("cmd2", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        // Verify scripts are in binDir
        assertTrue(new File(binDir, "cmd1").exists());
        assertTrue(new File(binDir, "cmd2").exists());

        // Verify metadata is in launcherDir (appDir)
        File metadataFile = new File(launcherDir, CliInstallerConstants.CLI_METADATA_FILE);
        assertTrue(metadataFile.exists(), "Metadata should exist in appDir");

        // Uninstall using appDir (where metadata lives)
        installer.uninstallCommands(launcherDir);

        // Scripts in binDir should be removed
        assertFalse(new File(binDir, "cmd1").exists(), "Script cmd1 should be removed from binDir");
        assertFalse(new File(binDir, "cmd2").exists(), "Script cmd2 should be removed from binDir");
        
        // Metadata file should also be removed
        assertFalse(metadataFile.exists(), "Metadata file should be removed after uninstall");
    }

    @Test
    public void testTemplateMethodProducesEquivalentBehaviorToLinux() throws IOException {
        // This test documents that Mac and Linux installers follow the same pattern:
        // 1. Scripts go to binDir
        // 2. Metadata goes to appDir (launcher parent)
        // 3. Uninstall from appDir removes scripts from binDir
        
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Verify: script created in binDir
        assertEquals(1, created.size());
        assertEquals(binDir, created.get(0).getParentFile());
        
        // Verify: metadata in appDir with binDir reference
        File metadataFile = new File(launcherDir, CliInstallerConstants.CLI_METADATA_FILE);
        assertTrue(metadataFile.exists());
        
        String content = new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8);
        JSONObject metadata = new JSONObject(content);
        
        // Metadata should contain binDir location for uninstall
        assertTrue(metadata.has("binDir"));
        assertEquals(binDir.getAbsolutePath(), metadata.getString("binDir"));
        
        // Metadata should list created wrappers
        assertTrue(metadata.has(CliInstallerConstants.CREATED_WRAPPERS_KEY));
    }

    @Test
    public void testSameAppCollision_silentOverwrite() throws IOException {
        // Create existing script pointing to the same launcher
        File scriptFile = new File(binDir, "mycmd");
        String existingContent = "#!/bin/bash\n\"" + launcherPath.getAbsolutePath() + "\" --jdeploy:command=mycmd -- \"$@\"\n";
        Files.write(scriptFile.toPath(), existingContent.getBytes(StandardCharsets.UTF_8));
        scriptFile.setExecutable(true);

        // Track if collision handler is called
        final boolean[] handlerCalled = {false};
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            handlerCalled[0] = true;
            return CollisionAction.SKIP;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Handler should NOT be called for same-app collision
        assertFalse(handlerCalled[0], "CollisionHandler should not be called for same-app collision");
        // File should still be created (overwritten)
        assertEquals(1, created.size());
        assertTrue(scriptFile.exists());
    }

    @Test
    public void testDifferentAppCollision_skip() throws IOException {
        // Create existing script pointing to a DIFFERENT launcher
        File differentLauncher = new File(tempDir, "other-app/launcher");
        differentLauncher.getParentFile().mkdirs();
        differentLauncher.createNewFile();
        
        File scriptFile = new File(binDir, "mycmd");
        String existingContent = "#!/bin/bash\n\"" + differentLauncher.getAbsolutePath() + "\" --jdeploy:command=mycmd -- \"$@\"\n";
        Files.write(scriptFile.toPath(), existingContent.getBytes(StandardCharsets.UTF_8));
        scriptFile.setExecutable(true);

        // Handler returns SKIP
        final String[] capturedParams = new String[3];
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            capturedParams[0] = cmdName;
            capturedParams[1] = existingPath;
            capturedParams[2] = newPath;
            return CollisionAction.SKIP;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Handler should be called with correct parameters
        assertEquals("mycmd", capturedParams[0]);
        assertEquals(differentLauncher.getAbsolutePath(), capturedParams[1]);
        assertEquals(launcherPath.getAbsolutePath(), capturedParams[2]);
        
        // File should NOT be in created list (was skipped)
        assertEquals(0, created.size());
        
        // Original file should be preserved
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains(differentLauncher.getAbsolutePath()), "Original script should be preserved");
    }

    @Test
    public void testDifferentAppCollision_overwrite() throws IOException {
        // Create existing script pointing to a DIFFERENT launcher
        File differentLauncher = new File(tempDir, "other-app/launcher");
        differentLauncher.getParentFile().mkdirs();
        differentLauncher.createNewFile();
        
        File scriptFile = new File(binDir, "mycmd");
        String existingContent = "#!/bin/bash\n\"" + differentLauncher.getAbsolutePath() + "\" --jdeploy:command=mycmd -- \"$@\"\n";
        Files.write(scriptFile.toPath(), existingContent.getBytes(StandardCharsets.UTF_8));
        scriptFile.setExecutable(true);

        // Handler returns OVERWRITE
        final boolean[] handlerCalled = {false};
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            handlerCalled[0] = true;
            return CollisionAction.OVERWRITE;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Handler should be called
        assertTrue(handlerCalled[0], "CollisionHandler should be called for different-app collision");
        
        // File should be in created list (was overwritten)
        assertEquals(1, created.size());
        
        // File should now point to new launcher
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
        assertTrue(content.contains(launcherPath.getAbsolutePath()), "Script should now point to new launcher");
        assertFalse(content.contains(differentLauncher.getAbsolutePath()), "Script should not contain old launcher path");
    }
}
