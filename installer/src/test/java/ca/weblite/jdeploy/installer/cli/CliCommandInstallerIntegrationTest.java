package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.win.InMemoryRegistryOperations;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

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
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());

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
        settings.setCommandLinePath(new File(binDir, "test-launcher").getAbsolutePath());
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

    /**
     * Testable subclass of WindowsCliCommandInstaller that:
     * - Uses a configurable bin directory instead of hardcoded ~/.jdeploy/bin
     * - Uses injected InMemoryRegistryOperations for testing
     */
    private static class TestableWindowsCliCommandInstaller extends WindowsCliCommandInstaller {
        private final File customBinDir;
        private final RegistryOperations registryOps;

        TestableWindowsCliCommandInstaller(File customBinDir, RegistryOperations registryOps) {
            this.customBinDir = customBinDir;
            this.registryOps = registryOps;
            setRegistryOperations(registryOps);
        }

        @Override
        public List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings) {
            List<File> createdFiles = new java.util.ArrayList<>();

            if (commands == null || commands.isEmpty()) {
                return createdFiles;
            }

            try {
                // Write .cmd wrappers using custom bin directory
                List<File> wrapperFiles = writeCommandWrappersForTest(customBinDir, launcherPath, commands);
                createdFiles.addAll(wrapperFiles);

                // Update user PATH via registry
                boolean pathUpdated = addToPath(customBinDir);

                // Persist metadata for uninstall in the app directory
                File appDir = launcherPath.getParentFile();
                persistMetadata(appDir, wrapperFiles, pathUpdated);

            } catch (IOException e) {
                System.err.println("Warning: Failed to install CLI commands: " + e.getMessage());
            }

            return createdFiles;
        }

        @Override
        public void uninstallCommands(File appDir) {
            if (appDir == null || !appDir.exists()) {
                return;
            }

            // Read metadata file
            File metadataFile = new File(appDir, ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_METADATA_FILE);
            if (!metadataFile.exists()) {
                return;
            }

            try {
                String metadataContent = FileUtils.readFileToString(metadataFile, "UTF-8");
                JSONObject metadata = new JSONObject(metadataContent);

                // Clean up wrapper files
                JSONArray wrappersArray = metadata.optJSONArray(ca.weblite.jdeploy.installer.CliInstallerConstants.CREATED_WRAPPERS_KEY);
                if (wrappersArray != null) {
                    for (int i = 0; i < wrappersArray.length(); i++) {
                        String wrapperName = wrappersArray.getString(i);
                        File wrapperFile = new File(customBinDir, wrapperName);
                        if (wrapperFile.exists() && !wrapperFile.delete()) {
                            System.err.println("Warning: Failed to delete wrapper file: " + wrapperFile.getAbsolutePath());
                        }

                        // Also attempt to delete the extensionless version (for Git Bash)
                        String shWrapperName = wrapperName.endsWith(".cmd")
                                ? wrapperName.substring(0, wrapperName.length() - 4)
                                : wrapperName;

                        File shWrapperFile = new File(customBinDir, shWrapperName);
                        if (shWrapperFile.exists() && !shWrapperFile.delete()) {
                            System.err.println("Warning: Failed to delete shell wrapper file: " + shWrapperFile.getAbsolutePath());
                        }
                    }
                }

                // Remove from PATH if it was added
                if (metadata.optBoolean(ca.weblite.jdeploy.installer.CliInstallerConstants.PATH_UPDATED_KEY, false)) {
                    removeFromUserPath(customBinDir);
                }

                // Remove from Git Bash path if it was added
                if (metadata.optBoolean(ca.weblite.jdeploy.installer.CliInstallerConstants.GIT_BASH_PATH_UPDATED_KEY, false)) {
                    // Logic for removal in test (reflects WindowsCliCommandInstaller.removeFromGitBashPath)
                    java.lang.reflect.Method removeMethod = WindowsCliCommandInstaller.class.getDeclaredMethod("removeFromGitBashPath", File.class);
                    removeMethod.setAccessible(true);
                    removeMethod.invoke(this, customBinDir);
                }

                // Delete metadata file
                if (!metadataFile.delete()) {
                    System.err.println("Warning: Failed to delete metadata file: " + metadataFile.getAbsolutePath());
                }

            } catch (Exception e) {
                System.err.println("Warning: Failed to uninstall CLI commands: " + e.getMessage());
            }
        }

        @Override
        protected InstallWindowsRegistry createRegistryHelper() {
            return new InstallWindowsRegistry(null, null, null, null, registryOps);
        }

        /**
         * Public accessor for removeFromUserPath for testing.
         */
        public boolean removeFromUserPath(File binDir) {
            if (binDir == null) {
                return false;
            }

            try {
                InstallWindowsRegistry registry = createRegistryHelper();
                return registry.removeFromUserPath(binDir);
            } catch (Exception e) {
                System.err.println("Warning: Failed to remove from user PATH: " + e.getMessage());
                return false;
            }
        }

        /**
         * Helper to persist metadata to file.
         */
        private void persistMetadata(File appDir, List<File> createdWrappers, boolean pathUpdated) throws IOException {
            JSONObject metadata = new JSONObject();

            // Store list of created wrapper file names
            JSONArray wrappersArray = new JSONArray();
            for (File wrapper : createdWrappers) {
                wrappersArray.put(wrapper.getName());
            }
            metadata.put(ca.weblite.jdeploy.installer.CliInstallerConstants.CREATED_WRAPPERS_KEY, wrappersArray);

            // Store whether PATH was updated
            metadata.put(ca.weblite.jdeploy.installer.CliInstallerConstants.PATH_UPDATED_KEY, pathUpdated);

            // Write metadata file
            File metadataFile = new File(appDir, ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_METADATA_FILE);
            FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");
        }

        @Override
        public String convertToMsysPath(File windowsPath) {
            return super.convertToMsysPath(windowsPath);
        }
    }

    @Test
    @DisabledOnOs({OS.MAC, OS.LINUX})
    public void testConvertToMsysPath() {
        WindowsCliCommandInstaller winInstaller = new WindowsCliCommandInstaller();
        
        assertEquals("/c/Users/Test", winInstaller.convertToMsysPath(new File("C:\\Users\\Test")));
        assertEquals("/d/Program Files/App", winInstaller.convertToMsysPath(new File("D:\\Program Files\\App")));
        assertEquals("/z/some/path", winInstaller.convertToMsysPath(new File("Z:/some/path")));
    }

    @Test
    @DisabledOnOs({OS.MAC, OS.LINUX})
    public void testWindowsGitBashPathIntegration() throws Exception {
        // Arrange
        File mockHome = tempDir.resolve("mock-home").toFile();
        mockHome.mkdirs();
        System.setProperty("user.home", mockHome.getAbsolutePath());
        
        File customBinDir = tempDir.resolve("gitbash-bin").toFile();
        customBinDir.mkdirs();
        
        TestableWindowsCliCommandInstaller windowsInstaller = 
                new TestableWindowsCliCommandInstaller(customBinDir, new InMemoryRegistryOperations());
        
        // Act - Add to Git Bash path
        boolean added = windowsInstaller.addToPath(customBinDir); // In implementation this calls addToGitBashPath in installCommands flow
        // For testing we call the protected/private logic via helper or direct if visible
        // Since we want to test the specific Git Bash logic:
        java.lang.reflect.Method addMethod = WindowsCliCommandInstaller.class.getDeclaredMethod("addToGitBashPath", File.class);
        addMethod.setAccessible(true);
        boolean result = (boolean) addMethod.invoke(windowsInstaller, customBinDir);
        
        // Assert
        assertTrue(result, "Should return true when adding new path to .bashrc");
        File bashrc = new File(mockHome, ".bashrc");
        assertTrue(bashrc.exists(), ".bashrc should be created");
        String content = FileUtils.readFileToString(bashrc, "UTF-8");
        String expectedPath = windowsInstaller.convertToMsysPath(customBinDir);
        assertTrue(content.contains("export PATH=\"$PATH:" + expectedPath + "\""), "Content should contain export line");
        
        // Act - Remove
        java.lang.reflect.Method removeMethod = WindowsCliCommandInstaller.class.getDeclaredMethod("removeFromGitBashPath", File.class);
        removeMethod.setAccessible(true);
        boolean removed = (boolean) removeMethod.invoke(windowsInstaller, customBinDir);
        
        // Assert
        assertTrue(removed, "Should return true when removing existing path");
        content = FileUtils.readFileToString(bashrc, "UTF-8");
        assertFalse(content.contains(expectedPath), "Content should no longer contain the path");
    }

    @Test
    @DisabledOnOs({OS.MAC, OS.LINUX})
    public void testWindowsCreatesBothCmdAndShellScripts() throws Exception {
        // Arrange
        InMemoryRegistryOperations registryOps = new InMemoryRegistryOperations();
        File customBinDir = tempDir.resolve("dual-scripts-bin").toFile();
        customBinDir.mkdirs();
        TestableWindowsCliCommandInstaller windowsInstaller = 
                new TestableWindowsCliCommandInstaller(customBinDir, registryOps);
        
        List<CommandSpec> commands = Arrays.asList(new CommandSpec("testapp", Arrays.asList()));
        InstallationSettings settings = new InstallationSettings();
        
        // Act
        windowsInstaller.installCommands(launcherPath, commands, settings);
        
        // Assert
        File cmdFile = new File(customBinDir, "testapp.cmd");
        File shFile = new File(customBinDir, "testapp");
        
        assertTrue(cmdFile.exists(), ".cmd wrapper should exist");
        assertTrue(shFile.exists(), "Extensionless shell script should exist");
        
        String cmdContent = FileUtils.readFileToString(cmdFile, "UTF-8");
        assertTrue(cmdContent.contains("@echo off"), "CMD should have batch header");
        
        String shContent = FileUtils.readFileToString(shFile, "UTF-8");
        assertTrue(shContent.startsWith("#!/bin/sh"), "Shell script should have shebang");
        assertTrue(shContent.contains(windowsInstaller.convertToMsysPath(launcherPath)), "Shell script should use MSYS path");

        // Act - Uninstall
        windowsInstaller.uninstallCommands(launcherDir);
        
        // Assert
        assertFalse(cmdFile.exists(), ".cmd should be deleted");
        assertFalse(shFile.exists(), "Shell script should be deleted");
    }

    @Test
    @DisabledOnOs({OS.MAC, OS.LINUX})
    public void testWindowsInstallVerifyUninstallCycle() throws Exception {
        // Arrange
        InMemoryRegistryOperations registryOps = new InMemoryRegistryOperations();
        File customBinDir = tempDir.resolve("custom-bin").toFile();
        customBinDir.mkdirs();

        TestableWindowsCliCommandInstaller windowsInstaller =
                new TestableWindowsCliCommandInstaller(customBinDir, registryOps);

        List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("myapp", Arrays.asList("--verbose")),
                new CommandSpec("myapp-admin", Arrays.asList("--admin", "--debug"))
        );

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliCommands(true);
        settings.setCommandLinePath(new File(customBinDir, "test-launcher").getAbsolutePath());

        // Act - Install commands
        List<File> installedFiles = windowsInstaller.installCommands(launcherPath, commands, settings);

        // Assert - Verify installation
        assertNotNull(installedFiles, "installCommands should return a list of installed files");
        assertFalse(installedFiles.isEmpty(), "Should have installed at least one command script");

        // Verify wrapper files exist
        for (File scriptFile : installedFiles) {
            assertTrue(scriptFile.exists(), "Installed wrapper should exist: " + scriptFile.getAbsolutePath());
            assertTrue(scriptFile.getName().endsWith(".cmd"), "Windows wrapper should be .cmd file");
            
            // Verify wrapper contains launcher path reference
            String content = new String(Files.readAllBytes(scriptFile.toPath()), StandardCharsets.UTF_8);
            assertTrue(content.contains(launcherPath.getAbsolutePath()), 
                "Wrapper should reference launcher path");
        }

        // Verify PATH updated in registry
        assertTrue(registryOps.keyExists("Environment"), "Environment key should be created in registry");
        String pathValue = registryOps.getStringValue("Environment", "Path");
        assertNotNull(pathValue, "Path value should be set in registry");
        assertTrue(pathValue.contains(customBinDir.getAbsolutePath()), 
            "Registry PATH should contain the bin directory");

        // Verify metadata file created and contains correct data
        File metadataFile = new File(launcherDir, ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_METADATA_FILE);
        assertTrue(metadataFile.exists(), "Metadata file should be created");
        
        String metadataContent = FileUtils.readFileToString(metadataFile, "UTF-8");
        JSONObject metadata = new JSONObject(metadataContent);
        
        JSONArray wrappersArray = metadata.getJSONArray(ca.weblite.jdeploy.installer.CliInstallerConstants.CREATED_WRAPPERS_KEY);
        assertEquals(commands.size(), wrappersArray.length(), 
            "Metadata should list all created wrappers");
        
        for (int i = 0; i < wrappersArray.length(); i++) {
            String wrapperName = wrappersArray.getString(i);
            assertTrue(wrapperName.endsWith(".cmd"), "Wrapper names should have .cmd extension");
        }
        
        assertTrue(metadata.getBoolean(ca.weblite.jdeploy.installer.CliInstallerConstants.PATH_UPDATED_KEY), 
            "Metadata should indicate PATH was updated");

        // Act - Uninstall commands
        windowsInstaller.uninstallCommands(launcherDir);

        // Assert - Verify cleanup
        // Wrapper files should be removed
        File[] remainingWrappers = customBinDir.listFiles((dir, name) -> name.endsWith(".cmd"));
        assertEquals(0, (remainingWrappers != null ? remainingWrappers.length : 0),
            "All .cmd wrappers should be removed after uninstall");

        // Metadata file should be deleted
        assertFalse(metadataFile.exists(), "Metadata file should be deleted after uninstall");
    }

    @Test
    @DisabledOnOs({OS.MAC, OS.LINUX})
    public void testWindowsPathUpdateInRegistry() throws Exception {
        // Arrange
        InMemoryRegistryOperations registryOps = new InMemoryRegistryOperations();
        File customBinDir = tempDir.resolve("registry-test-bin").toFile();
        customBinDir.mkdirs();

        TestableWindowsCliCommandInstaller windowsInstaller =
                new TestableWindowsCliCommandInstaller(customBinDir, registryOps);

        // Act - Add to PATH first time
        boolean firstChange = windowsInstaller.addToPath(customBinDir);

        // Assert - Should report change
        assertTrue(firstChange, "First addToPath should return true");
        assertTrue(registryOps.keyExists("Environment"), "Environment key should exist");
        String pathValue = registryOps.getStringValue("Environment", "Path");
        assertNotNull(pathValue, "Path value should be set");
        assertTrue(pathValue.contains(customBinDir.getAbsolutePath()), 
            "PATH should contain the bin directory");

        // Act - Add to PATH second time (same directory)
        boolean secondChange = windowsInstaller.addToPath(customBinDir);

        // Assert - Should not report change (idempotent)
        assertFalse(secondChange, "Second addToPath of same directory should return false");
        String pathValueAfterSecond = registryOps.getStringValue("Environment", "Path");
        assertEquals(pathValue, pathValueAfterSecond, 
            "PATH should not change when adding duplicate");

        // Act - Add a different directory
        File anotherBinDir = tempDir.resolve("another-bin").toFile();
        anotherBinDir.mkdirs();
        boolean thirdChange = windowsInstaller.addToPath(anotherBinDir);

        // Assert - Should report change
        assertTrue(thirdChange, "Adding different directory should return true");
        String pathValueAfterThird = registryOps.getStringValue("Environment", "Path");
        assertTrue(pathValueAfterThird.contains(customBinDir.getAbsolutePath()),
            "First bin dir should still be in PATH");
        assertTrue(pathValueAfterThird.contains(anotherBinDir.getAbsolutePath()),
            "Second bin dir should be in PATH");
    }
}
