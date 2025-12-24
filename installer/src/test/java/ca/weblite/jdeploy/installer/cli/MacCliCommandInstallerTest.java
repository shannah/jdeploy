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
import java.util.ArrayList;
import java.util.List;

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
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        List<File> created = installer.installCommands(launcherPath, commands, settings);

        assertEquals(2, created.size());
        assertTrue(new File(binDir, "mycmd").exists());
        assertTrue(new File(binDir, "othercmd").exists());
    }

    @Test
    public void testInstallCommandsWithCliLauncher() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
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
    public void testInstallCommandsWithBothOptions() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
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
        settings.setInstallCliCommands(true);

        List<File> created = installer.installCommands(null, commands, settings);

        assertEquals(0, created.size());
    }

    @Test
    public void testCommandScriptEscapeDoubleQuotes() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
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
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File scriptFile = new File(binDir, "mycmd");
        String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);

        assertTrue(content.contains("--flag"));
        assertTrue(content.contains("value"));
    }

    @Test
    public void testAddToPathBash() {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");

        boolean result = MacCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(bashrc.exists());
    }

    @Test
    public void testAddToPathZsh() {
        String shell = "/bin/zsh";
        String pathEnv = "/usr/bin:/bin";
        File zshrc = new File(homeDir, ".zshrc");

        boolean result = MacCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(zshrc.exists());
    }

    @Test
    public void testAddToPathAlreadyInPath() {
        String shell = "/bin/bash";
        String pathEnv = "/usr/bin:" + binDir.getAbsolutePath() + ":/bin";
        File bashrc = new File(homeDir, ".bashrc");

        boolean result = MacCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

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

        boolean result = MacCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
    }

    @Test
    public void testAddToPathFish() {
        String shell = "/usr/bin/fish";
        String pathEnv = "/usr/bin:/bin";

        boolean result = MacCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertFalse(result);
    }

    @Test
    public void testAddToPathUnknownShell() {
        String shell = "/bin/unknown";
        String pathEnv = "/usr/bin:/bin";
        File profile = new File(homeDir, ".profile");

        boolean result = MacCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
        assertTrue(profile.exists());
    }

    @Test
    public void testAddToPathNullShell() {
        String shell = null;
        String pathEnv = "/usr/bin:/bin";
        File bashrc = new File(homeDir, ".bashrc");

        boolean result = MacCliCommandInstaller.addToPath(binDir, shell, pathEnv, homeDir);

        assertTrue(result);
    }

    @Test
    public void testUninstallCommandsRemovesScripts() throws IOException {
        // First install some commands
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("cmd1", new ArrayList<>()));
        commands.add(new CommandSpec("cmd2", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

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
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File metadataFile = new File(binDir, CliInstallerConstants.CLI_METADATA_FILE);
        assertTrue(metadataFile.exists());

        String content = new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8);
        JSONObject metadata = new JSONObject(content);

        assertTrue(metadata.has(CliInstallerConstants.CREATED_WRAPPERS_KEY));
        JSONArray wrappers = metadata.getJSONArray(CliInstallerConstants.CREATED_WRAPPERS_KEY);
        assertEquals(1, wrappers.length());
        assertEquals("mycmd", wrappers.getString(0));
    }

    @Test
    public void testDeriveCommandNameFromAppInfo() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();

        InstallationSettings settings = new InstallationSettings();
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
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File metadataFile = new File(binDir, CliInstallerConstants.CLI_METADATA_FILE);
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
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(false);

        installer.installCommands(launcherPath, commands, settings);

        File metadataFile = new File(binDir, CliInstallerConstants.CLI_METADATA_FILE);
        String content = new String(Files.readAllBytes(metadataFile.toPath()), StandardCharsets.UTF_8);
        JSONObject metadata = new JSONObject(content);

        assertTrue(metadata.has(CliInstallerConstants.PATH_UPDATED_KEY));
    }

    @Test
    public void testInstallCommandsUpdateSettings() throws IOException {
        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliCommands(true);
        settings.setInstallCliLauncher(true);
        AppInfo appInfo = new AppInfo();
        appInfo.setTitle("Test App");
        settings.setAppInfo(appInfo);

        installer.installCommands(launcherPath, commands, settings);

        assertTrue(settings.isCommandLineSymlinkCreated() || settings.isAddedToPath());
    }
}
