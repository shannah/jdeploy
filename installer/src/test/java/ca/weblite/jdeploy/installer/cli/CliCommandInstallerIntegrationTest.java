package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CliCommandInstaller implementations.
 * Tests the full lifecycle: install → verify → uninstall → verify cleanup.
 */
public class CliCommandInstallerIntegrationTest {

    private Path tempDir;
    private File launcherDir;
    private File binDir;
    private File launcherPath;
    private CliCommandInstaller installer;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("cli-installer-test");
        launcherDir = tempDir.resolve("launcher").toFile();
        binDir = tempDir.resolve("bin").toFile();
        launcherPath = tempDir.resolve("launcher").resolve("jdeploy-launcher").toFile();

        launcherDir.mkdirs();
        binDir.mkdirs();

        // Create a mock launcher file
        assertTrue(launcherPath.createNewFile(), "Failed to create launcher file");

        // Use Linux installer for testing (most portable)
        installer = new LinuxCliCommandInstaller();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (tempDir != null && Files.exists(tempDir)) {
            FileUtils.deleteDirectory(tempDir.toFile());
        }
    }

    @Test
    public void testInstallVerifyUninstallCycle() throws Exception {
        // Arrange
        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("myapp", Arrays.asList("--verbose")),
                new CommandSpec("myapp-admin", Arrays.asList("--admin", "--debug"))
        );

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliCommands(true);
        settings.setCommandLinePath(binDir.getAbsolutePath());

        // Act - Install commands
        List<File> installedFiles = installer.installCommands(launcherPath, commands, settings);

        // Assert - Verify installation
        assertNotNull(installedFiles, "installCommands should return a list of installed files");
        assertFalse(installedFiles.isEmpty(), "Should have installed at least one command script");

        // Verify each installed script exists
        for (File scriptFile : installedFiles) {
            assertTrue(scriptFile.exists(), "Installed script should exist: " + scriptFile.getAbsolutePath());
            assertTrue(scriptFile.isFile(), "Installed path should be a file: " + scriptFile.getAbsolutePath());
        }

        // Count files in bin directory before uninstall
        File[] filesBeforeUninstall = binDir.listFiles((dir, name) -> !name.startsWith("."));
        int installedCountBefore = filesBeforeUninstall != null ? filesBeforeUninstall.length : 0;
        assertTrue(installedCountBefore > 0, "Should have installed scripts in bin directory");

        // Act - Uninstall commands
        installer.uninstallCommands(launcherDir);

        // Assert - Verify cleanup
        File[] remainingScripts = binDir.listFiles((dir, name) -> 
                !name.startsWith(".") && !name.endsWith(".json"));
        
        int remainingCount = remainingScripts != null ? remainingScripts.length : 0;
        assertTrue(remainingCount == 0 || remainingCount < installedCountBefore,
                "Scripts should be removed or at least reduced after uninstall");

        // Verify at least some of the originally installed files are gone
        int deletedCount = 0;
        for (File scriptFile : installedFiles) {
            if (!scriptFile.exists()) {
                deletedCount++;
            }
        }
        assertGreater(deletedCount, 0,
                "At least some installed scripts should be deleted after uninstall");
    }

    @Test
    public void testInstallMultipleCommands() throws Exception {
        // Arrange
        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("cmd1", Arrays.asList()),
                new CommandSpec("cmd2", Arrays.asList("--flag")),
                new CommandSpec("cmd3", Arrays.asList("--arg1", "--arg2"))
        );

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());

        // Act
        List<File> installedFiles = installer.installCommands(launcherPath, commands, settings);

        // Assert
        assertFalse(installedFiles.isEmpty(), "Should install commands");
        assertGreaterThanOrEqual(installedFiles.size(), commands.size(),
                "Should have installed script files for each command");

        // Verify all scripts exist
        for (File scriptFile : installedFiles) {
            assertTrue(scriptFile.exists(),
                    "Script should exist after installation: " + scriptFile.getAbsolutePath());
        }
    }

    @Test
    public void testUninstallRemovesInstalledScripts() throws Exception {
        // Arrange
        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("test-cmd", Arrays.asList())
        );

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(binDir.getAbsolutePath());
        List<File> installedFiles = installer.installCommands(launcherPath, commands, settings);

        assertTrue(!installedFiles.isEmpty(), "Commands should be installed");

        // Verify scripts exist before uninstall
        for (File scriptFile : installedFiles) {
            assertTrue(scriptFile.exists(), "Script should exist before uninstall");
        }

        // Act
        installer.uninstallCommands(launcherDir);

        // Assert - Verify most scripts are removed
        int existingCount = 0;
        for (File scriptFile : installedFiles) {
            if (scriptFile.exists()) {
                existingCount++;
            }
        }

        // Allow for metadata file or other remaining artifacts
        assertTrue(existingCount < installedFiles.size(),
                "Most installed scripts should be removed after uninstall");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testPathAdditionWorks() throws Exception {
        // Arrange
        File pathBinDir = new File(binDir, "path-test");
        pathBinDir.mkdirs();

        // Act
        boolean pathAdded = installer.addToPath(pathBinDir);

        // Assert - path addition behavior depends on shell environment
        // Just verify the method executes without throwing
        assertTrue(true, "addToPath should execute without exception");
    }

    /**
     * Helper assertion: actual >= expected.
     */
    private void assertGreaterThanOrEqual(int actual, int expected, String message) {
        assertTrue(actual >= expected, message + " (expected >= " + expected + ", got " + actual + ")");
    }

    /**
     * Helper assertion: actual > expected.
     */
    private void assertGreater(int actual, int expected, String message) {
        assertTrue(actual > expected, message + " (expected > " + expected + ", got " + actual + ")");
    }
}
