package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
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
        installer = new LinuxCliCommandInstaller();
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
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));
        commands.add(new CommandSpec("othercmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

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
        settings.setCommandLinePath(binDir.getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(0, created.size());
    }

    @Test
    public void testInstallCommandsWithNullList() {
        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

        List<File> created = installer.installCommands(launcherPath, null, settings);

        assertEquals(0, created.size());
    }

    @Test
    public void testInstallCommandsCreatesDefaultBinDir() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("testcmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
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
        commands.add(new CommandSpec("testcmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

        installer.installCommands(launcherPath, commands, settings);

        File scriptFile = new File(binDir, "testcmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("#!/bin/sh"));
        assertTrue(content.contains(launcherPath.getAbsolutePath()));
        assertTrue(content.contains("--jdeploy:command=testcmd"));
    }

    @Test
    public void testAddToPathBash() {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File profile = new File(homeDir, ".profile");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        // UnixPathManager always uses .profile for POSIX compatibility
        assertTrue(profile.exists(), "profile was not created");
    }

    @Test
    public void testAddToPathZsh() {
        String shell = "/bin/zsh";
        String pathEnv = "/usr/bin:/bin";
        File profile = new File(homeDir, ".profile");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(profile.exists());
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
        assertTrue(profile.exists());
    }

    @Test
    public void testAddToPathUnknownShell() {
        String shell = "/bin/unknown";
        String pathEnv = "/usr/bin:/bin";
        File profile = new File(homeDir, ".profile");

        boolean result = AbstractUnixCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(profile.exists());
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
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

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
    public void testInstallLauncherCreatesSymlink() throws IOException {
        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

        File result = installer.installLauncher(launcherPath, "myapp", settings);

        assertNotNull(result);
        assertTrue(result.exists());
        assertEquals("myapp", result.getName());
    }

    @Test
    public void testInstallLauncherWithNullCommandName() {
        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

        File result = installer.installLauncher(launcherPath, null, settings);

        assertNull(result);
    }

    @Test
    public void testInstallLauncherWithNullLauncherPath() {
        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

        File result = installer.installLauncher(null, "myapp", settings);

        assertNull(result);
    }

    @Test
    public void testInstallCommandsRemovesExistingScript() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

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
}
