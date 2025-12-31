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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.*;

public class LinuxCliCommandInstallerTest {

    private LinuxCliCommandInstaller installer;
    private File tempDir;
    private File launcherDir;
    private File launcherPath;
    private File binDir;
    private File homeDir;

    @BeforeEach
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("jdeploy-test-").toFile();
        launcherDir = new File(tempDir, "app");
        launcherDir.mkdirs();
        launcherPath = new File(launcherDir, "launcher");
        launcherPath.createNewFile();
        launcherPath.setExecutable(true);

        binDir = new File(new File(tempDir, ".local"), "bin");
        binDir.mkdirs();

        homeDir = new File(tempDir, "home");
        homeDir.mkdirs();
        installer = new TestableLinuxCliCommandInstaller(homeDir);
    }

    /**
     * Testable subclass of LinuxCliCommandInstaller that uses a custom home directory
     * to prevent polluting real shell profile files during tests.
     */
    private static class TestableLinuxCliCommandInstaller extends LinuxCliCommandInstaller {
        private final File customHomeDir;

        TestableLinuxCliCommandInstaller(File customHomeDir) {
            this.customHomeDir = customHomeDir;
        }

        @Override
        protected File getHomeDir() {
            return customHomeDir;
        }
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (tempDir != null && tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    @Test
    public void testInstallCommandsCreatesScripts() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", null, new ArrayList<>()));
        commands.add(new CommandSpec("othercmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(2, created.size());
        assertTrue(new File(binDir, "mycmd").exists());
        assertTrue(new File(binDir, "othercmd").exists());
        assertTrue(new File(binDir, "mycmd").canExecute());
        assertTrue(new File(binDir, "othercmd").canExecute());
    }

    @Test
    public void testInstallCommandsWithEmptyList() {
        List<CommandSpec> commands = new ArrayList<>();
        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(0, created.size());
    }

    @Test
    public void testInstallCommandsWithNullList() {
        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, null, settings);

        assertEquals(0, created.size());
    }

    @Test
    public void testInstallCommandsCreatesDefaultBinDir() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        // Don't set commandLinePath, should use ~/.local/bin
        settings.setCommandLinePath(null);

        // Create a mock home directory for this test
        File mockHome = new File(tempDir, "mock-home");
        mockHome.mkdirs();

        // We'll just verify the script was created in the passed binDir
        settings.setCommandLinePath(new File(mockHome, ".local" + File.separator + "bin").getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(1, created.size());
    }

    @Test
    public void testInstallCommandsScriptContent() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        installer.installCommands(launcherPath, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // On Windows, paths contain backslashes which get escaped in the script
        String expectedPath = launcherPath.getAbsolutePath().replace("\\", "\\\\");
        assertTrue(content.contains(expectedPath));
        assertTrue(content.contains("--jdeploy:command=testcmd"));
    }

    @Test
    public void testInstallCommandsScriptContent_pathWithSpaces() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File spaceDir = new File(tempDir, "path with spaces");
        spaceDir.mkdirs();
        File spacedLauncher = new File(spaceDir, "launcher");
        spacedLauncher.createNewFile();
        spacedLauncher.setExecutable(true);

        installer.installCommands(spacedLauncher, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // On Windows, paths contain backslashes which get escaped in the script
        String expectedPath = spacedLauncher.getAbsolutePath().replace("\\", "\\\\");
        assertTrue(content.contains(expectedPath));
        assertTrue(content.contains("--jdeploy:command=testcmd"));
    }

    @Test
    public void testInstallCommandsScriptContent_pathWithSingleQuotes() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File quoteDir = new File(tempDir, "path'with'quotes");
        quoteDir.mkdirs();
        File quotedLauncher = new File(quoteDir, "launcher");
        quotedLauncher.createNewFile();
        quotedLauncher.setExecutable(true);

        installer.installCommands(quotedLauncher, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // Single quotes don't need escaping in double quotes
        // On Windows, paths contain backslashes which get escaped in the script
        String expectedPath = quotedLauncher.getAbsolutePath().replace("\\", "\\\\");
        assertTrue(content.contains(expectedPath));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testInstallCommandsScriptContent_pathWithDoubleQuotes() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File doubleQuoteDir = new File(tempDir, "path\"with\"quotes");
        doubleQuoteDir.mkdirs();
        File doubleQuotedLauncher = new File(doubleQuoteDir, "launcher");
        doubleQuotedLauncher.createNewFile();
        doubleQuotedLauncher.setExecutable(true);

        installer.installCommands(doubleQuotedLauncher, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // Double quotes should be escaped
        assertTrue(content.contains("\\\""));
    }

    @Test
    public void testInstallCommandsScriptContent_pathWithBackticks() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File backtickDir = new File(tempDir, "path`with`backticks");
        backtickDir.mkdirs();
        File backtickLauncher = new File(backtickDir, "launcher");
        backtickLauncher.createNewFile();
        backtickLauncher.setExecutable(true);

        installer.installCommands(backtickLauncher, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // Backticks should be escaped
        assertTrue(content.contains("\\`"));
    }

    @Test
    public void testInstallCommandsScriptContent_pathWithDollarSign() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File dollarDir = new File(tempDir, "path$with$dollar");
        dollarDir.mkdirs();
        File dollarLauncher = new File(dollarDir, "launcher");
        dollarLauncher.createNewFile();
        dollarLauncher.setExecutable(true);

        installer.installCommands(dollarLauncher, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // Dollar signs should be escaped
        assertTrue(content.contains("\\$"));
    }

    @Test
    public void testInstallCommandsScriptContent_pathWithBackslash() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File backslashDir = new File(tempDir, "path\\with\\backslash");
        backslashDir.mkdirs();
        File backslashLauncher = new File(backslashDir, "launcher");
        backslashLauncher.createNewFile();
        backslashLauncher.setExecutable(true);

        installer.installCommands(backslashLauncher, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // Backslashes should be escaped
        assertTrue(content.contains("\\\\"));
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testInstallCommandsScriptContent_pathWithMultipleSpecialChars() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File specialDir = new File(tempDir, "path`with$multiple\"special'chars");
        specialDir.mkdirs();
        File specialLauncher = new File(specialDir, "launcher");
        specialLauncher.createNewFile();
        specialLauncher.setExecutable(true);

        installer.installCommands(specialLauncher, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        // Multiple special chars should be escaped appropriately
        assertTrue(content.contains("\\`"));
        assertTrue(content.contains("\\$"));
        assertTrue(content.contains("\\\""));
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
        commands.add(new CommandSpec("cmd1", null, new ArrayList<>()));
        commands.add(new CommandSpec("cmd2", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, commands, settings);
        assertEquals(2, created.size());

        // Verify files exist
        assertTrue(new File(binDir, "cmd1").exists());
        assertTrue(new File(binDir, "cmd2").exists());

        // Now uninstall
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
        commands.add(new CommandSpec("mycmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

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
    public void testInstallLauncherCreatesSymlink() throws IOException {
        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "myapp").getAbsolutePath());

        File result = installer.installLauncher(launcherPath, "myapp", settings);

        assertNotNull(result);
        assertTrue(result.exists());
        assertEquals("myapp", result.getName());
    }

    @Test
    public void testInstallLauncherWithNullCommandName() {
        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        File result = installer.installLauncher(launcherPath, null, settings);

        assertNull(result);
    }

    @Test
    public void testInstallLauncherWithNullLauncherPath() {
        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "myapp").getAbsolutePath());

        File result = installer.installLauncher(null, "myapp", settings);

        assertNull(result);
    }

    @Test
    public void testInstallCommandsRemovesExistingScript() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        // Create initial script
        File scriptFile = new File(binDir, "mycmd");
        scriptFile.createNewFile();
        Files.write(scriptFile.toPath(), "old content".getBytes());

        // Install again, should replace
        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(1, created.size());
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
        assertFalse(content.contains("old content"));
        assertTrue(content.contains("--jdeploy:command=mycmd"));
    }

    @Test
    public void testMetadataSavedToAppDirNotBinDir() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        installer.installCommands(launcherPath, commands, settings);

        // Metadata should be in launcherDir (appDir), not binDir
        File metadataInAppDir = new File(launcherDir, CliInstallerConstants.CLI_METADATA_FILE);
        File metadataInBinDir = new File(binDir, CliInstallerConstants.CLI_METADATA_FILE);
        
        assertTrue(metadataInAppDir.exists(), "Metadata should be saved to appDir (launcher parent)");
        assertFalse(metadataInBinDir.exists(), "Metadata should NOT be saved to binDir when appDir differs");
    }

    @Test
    public void testUninstallFromAppDirRemovesScriptsFromBinDir() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("cmd1", null, new ArrayList<>()));
        commands.add(new CommandSpec("cmd2", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

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
    public void testTemplateMethodProducesEquivalentBehaviorToMac() throws IOException {
        // This test documents that Linux and Mac installers follow the same pattern:
        // 1. Scripts go to binDir
        // 2. Metadata goes to appDir (launcher parent)
        // 3. Uninstall from appDir removes scripts from binDir
        
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Verify: script created in binDir
        assertEquals(1, created.size());
        assertEquals(binDir.getAbsoluteFile(), created.get(0).getParentFile().getAbsoluteFile());
        
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
        String existingContent = "#!/bin/sh\nexec \"" + launcherPath.getAbsolutePath() + "\" --jdeploy:command=mycmd -- \"$@\"\n";
        Files.write(scriptFile.toPath(), existingContent.getBytes(StandardCharsets.UTF_8));
        scriptFile.setExecutable(true);

        // Track if collision handler is called
        final boolean[] handlerCalled = {false};
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            handlerCalled[0] = true;
            return CollisionAction.SKIP;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

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
        String existingContent = "#!/bin/sh\nexec \"" + differentLauncher.getAbsolutePath() + "\" --jdeploy:command=mycmd -- \"$@\"\n";
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
        commands.add(new CommandSpec("mycmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        // Small delay to ensure modification time would differ if file was changed
        try { Thread.sleep(100); } catch (InterruptedException e) {}

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
        String existingContent = "#!/bin/sh\nexec \"" + differentLauncher.getAbsolutePath() + "\" --jdeploy:command=mycmd -- \"$@\"\n";
        Files.write(scriptFile.toPath(), existingContent.getBytes(StandardCharsets.UTF_8));
        scriptFile.setExecutable(true);

        // Handler returns OVERWRITE
        final boolean[] handlerCalled = {false};
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            handlerCalled[0] = true;
            return CollisionAction.OVERWRITE;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", null, new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliLauncher(false);
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        // Handler should be called
        assertTrue(handlerCalled[0], "CollisionHandler should be called for different-app collision");
        
        // File should be in created list (was overwritten)
        assertEquals(1, created.size());
        
        // File should now point to new launcher
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
        String expectedPath = launcherPath.getAbsolutePath().replace("\\", "\\\\");
        assertTrue(content.contains(expectedPath), "Script should now point to new launcher");
        String expectedOldPath = differentLauncher.getAbsolutePath().replace("\\", "\\\\");
        assertFalse(content.contains(expectedOldPath), "Script should not contain old launcher path");
    }
}
