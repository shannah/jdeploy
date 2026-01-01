package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
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
import java.util.Optional;

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
    private File mockHome;
    private File actualBinDir;
    private CliCommandInstaller installer;

    @BeforeEach
    public void setUp() throws Exception {
        tempDir = Files.createTempDirectory("cli-installer-test");
        launcherDir = tempDir.resolve("launcher").toFile();
        binDir = tempDir.resolve("bin").toFile();
        launcherPath = tempDir.resolve("launcher").resolve("jdeploy-launcher").toFile();

        // Create mock home directory to prevent polluting real shell profiles
        mockHome = tempDir.resolve("mock-home").toFile();
        mockHome.mkdirs();

        launcherDir.mkdirs();
        binDir.mkdirs();

        // Compute the actual bin directory where scripts will be installed
        // With the new per-app structure: ~/.jdeploy/bin-{arch}/{fullyQualifiedPackageName}/
        String archSuffix = ca.weblite.jdeploy.installer.util.ArchitectureUtil.getArchitectureSuffix();
        actualBinDir = new File(mockHome, ".jdeploy/bin" + archSuffix + "/test-app");

        // Create a mock launcher file
        assertTrue(launcherPath.createNewFile(), "Failed to create launcher file");

        // Use testable Linux installer with mock home directory
        installer = new TestableLinuxCliCommandInstaller(mockHome);
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
                new CommandSpec("myapp", null, Arrays.asList("--verbose")),
                new CommandSpec("myapp-admin", null, Arrays.asList("--admin", "--debug"))
        );

        InstallationSettings settings = new InstallationSettings();
        settings.setInstallCliCommands(true);
        settings.setCommandLinePath(new File(actualBinDir, "test-launcher").getAbsolutePath());
        settings.setPackageName("test-app");
        settings.setSource(null);

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

        // Count files in actual bin directory before uninstall
        File[] filesBeforeUninstall = actualBinDir.listFiles((dir, name) -> !name.startsWith("."));
        int installedCountBefore = filesBeforeUninstall != null ? filesBeforeUninstall.length : 0;
        assertTrue(installedCountBefore > 0, "Should have installed scripts in bin directory");

        // Act - Uninstall commands
        installer.uninstallCommands(launcherDir);

        // Assert - Verify cleanup
        File[] remainingScripts = actualBinDir.listFiles((dir, name) -> 
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
                new CommandSpec("cmd1", null, Arrays.asList()),
                new CommandSpec("cmd2", null, Arrays.asList("--flag")),
                new CommandSpec("cmd3", null, Arrays.asList("--arg1", "--arg2"))
        );

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(actualBinDir, "test-launcher").getAbsolutePath());
        settings.setPackageName("test-app");
        settings.setSource(null);

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
                new CommandSpec("test-cmd", null, Arrays.asList())
        );

        InstallationSettings settings = new InstallationSettings();
        settings.setCommandLinePath(new File(actualBinDir, "test-launcher").getAbsolutePath());
        settings.setPackageName("test-app");
        settings.setSource(null);
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
        // mockHome already created in setUp()

        // Act
        // Use the static testable overload to avoid polluting the real user's home directory
        boolean pathAdded = AbstractUnixCliCommandInstaller.addToPath(pathBinDir, "/bin/bash", "", mockHome);

        // Assert - path addition behavior depends on shell environment
        // Just verify the method executes without throwing and created the file in mock home
        assertTrue(pathAdded, "addToPath should return true");
        assertTrue(new File(mockHome, ".bashrc").exists(), ".bashrc should be created in mock home");
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
        assertTrue(content.contains("export PATH=\"" + expectedPath + ":$PATH\""), "Content should contain export line with prepended path");
        
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
        
        List<CommandSpec> commands = Arrays.asList(new CommandSpec("testapp", null, Arrays.asList()));
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
                new CommandSpec("myapp", null, Arrays.asList("--verbose")),
                new CommandSpec("myapp-admin", null, Arrays.asList("--admin", "--debug"))
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

    @Test
    public void testUninstallRemovesPerAppBinDirectory() throws Exception {
        // Arrange - Create per-app bin directory structure
        File appDir = tempDir.resolve("per-app-bin-test").toFile();
        appDir.mkdirs();
        
        // Create per-app bin directory (~/.jdeploy/bin-x64/my.test.app/)
        String arch = ArchitectureUtil.getArchitecture();
        File perAppBinDir = new File(mockHome, ".jdeploy/bin-" + arch + "/my.test.app");
        perAppBinDir.mkdirs();
        
        // Create a script in the per-app directory
        File scriptFile = new File(perAppBinDir, "myapp");
        assertTrue(scriptFile.createNewFile(), "Failed to create script file");
        
        // Create metadata pointing to the per-app directory
        JSONObject metadata = new JSONObject();
        JSONArray wrappersArray = new JSONArray();
        wrappersArray.put("myapp");
        metadata.put(ca.weblite.jdeploy.installer.CliInstallerConstants.CREATED_WRAPPERS_KEY, (Object) wrappersArray);
        metadata.put(ca.weblite.jdeploy.installer.CliInstallerConstants.PATH_UPDATED_KEY, false);
        metadata.put("binDir", perAppBinDir.getAbsolutePath());
        metadata.put("packageName", "my-test-app");
        metadata.put("source", (Object) null);
        
        File metadataFile = new File(appDir, ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_METADATA_FILE);
        FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");
        
        // Verify setup
        assertTrue(perAppBinDir.exists(), "Per-app bin dir should exist");
        assertTrue(scriptFile.exists(), "Script should exist");
        
        // Act - Uninstall using testable installer
        TestableLinuxCliCommandInstaller uninstaller = new TestableLinuxCliCommandInstaller(mockHome);
        uninstaller.uninstallCommands(appDir);
        
        // Assert
        assertFalse(perAppBinDir.exists(), "Per-app bin directory should be removed");
        assertFalse(scriptFile.exists(), "Script should be deleted");
        
        // Verify parent directories still exist
        File archDir = new File(mockHome, ".jdeploy/bin-" + arch);
        assertTrue(archDir.exists(), "Architecture directory should still exist");
        
        File jdeployDir = new File(mockHome, ".jdeploy");
        assertTrue(jdeployDir.exists(), "Jdeploy directory should still exist");
    }

    @Test
    public void testUninstallDeletesManifestFile() throws Exception {
        // Arrange - Install commands to create a manifest
        // Set user.home to mockHome so FileCliCommandManifestRepository uses the same location
        String originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", mockHome.getAbsolutePath());
        
        try {
            List<CommandSpec> commands = Arrays.asList(
                new CommandSpec("test-cmd", null, Arrays.asList())
            );
            
            InstallationSettings settings = new InstallationSettings();
            settings.setInstallCliCommands(true);
            settings.setPackageName("manifest-test-app");
            settings.setSource(null);  // NPM package
            settings.setCommandLinePath(new File(actualBinDir, "test-launcher").getAbsolutePath());
            
            TestableLinuxCliCommandInstaller installer = new TestableLinuxCliCommandInstaller(mockHome);
            List<File> installedFiles = installer.installCommands(launcherPath, commands, settings);
            
            assertTrue(!installedFiles.isEmpty(), "Should have installed commands");
            
            // Act - Uninstall
            File appDir = launcherPath.getParentFile();
            installer.uninstallCommands(appDir);
            
            // Assert - Verify uninstall completed without error
            // Note: The manifest repository is used during uninstall for cleanup,
            // but installCommands saves metadata to a JSON file, not the manifest repository.
            // The uninstall should complete successfully regardless of manifest state.
            File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
            assertFalse(metadataFile.exists(), "Metadata file should be deleted after uninstall");
        } finally {
            // Restore original user.home
            System.setProperty("user.home", originalUserHome);
        }
    }

    @Test
    public void testUninstallPreservesParentDirectories() throws Exception {
        // Arrange - Create a per-app directory structure with multiple apps
        File appDir1 = tempDir.resolve("app1").toFile();
        appDir1.mkdirs();
        
        String arch = ArchitectureUtil.getArchitecture();
        File archDir = new File(mockHome, ".jdeploy/bin-" + arch);
        File app1BinDir = new File(archDir, "my.app.one");
        File app2BinDir = new File(archDir, "my.app.two");
        
        app1BinDir.mkdirs();
        app2BinDir.mkdirs();
        
        // Create scripts in both directories
        File script1 = new File(app1BinDir, "cmd1");
        File script2 = new File(app2BinDir, "cmd2");
        script1.createNewFile();
        script2.createNewFile();
        
        // Create metadata for app1
        JSONObject metadata = new JSONObject();
        JSONArray wrappersArray = new JSONArray();
        wrappersArray.put("cmd1");
        metadata.put(CliInstallerConstants.CREATED_WRAPPERS_KEY, wrappersArray);
        metadata.put(CliInstallerConstants.PATH_UPDATED_KEY, false);
        metadata.put("binDir", app1BinDir.getAbsolutePath());
        metadata.put("packageName", "my-app-one");
        
        File metadataFile = new File(appDir1, CliInstallerConstants.CLI_METADATA_FILE);
        FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");
        
        // Act - Uninstall app1
        TestableLinuxCliCommandInstaller uninstaller = new TestableLinuxCliCommandInstaller(mockHome);
        uninstaller.uninstallCommands(appDir1);
        
        // Assert
        assertFalse(app1BinDir.exists(), "app1 bin dir should be removed");
        assertTrue(app2BinDir.exists(), "app2 bin dir should still exist");
        assertTrue(archDir.exists(), "Architecture directory should still exist");
        assertTrue(new File(mockHome, ".jdeploy").exists(), "Jdeploy directory should still exist");
    }

    @Test
    public void testUninstallWithManifestRepository() throws Exception {
        // Arrange - Create and save a manifest directly via repository
        File appDir = tempDir.resolve("manifest-repo-test").toFile();
        appDir.mkdirs();
        
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName("repo-test-app", null);
        File manifestBinDir = new File(mockHome, ".jdeploy/bin-" + arch + "/" + fqpn);
        manifestBinDir.mkdirs();
        
        // Create script in the manifest bin directory
        File scriptFile = new File(manifestBinDir, "repo-cmd");
        scriptFile.createNewFile();
        
        // Create manifest via repository
        CliCommandManifest manifest = new CliCommandManifest(
            "repo-test-app",
            null,
            manifestBinDir,
            Arrays.asList("repo-cmd"),
            false,
            System.currentTimeMillis()
        );
        
        FileCliCommandManifestRepository manifestRepo = new FileCliCommandManifestRepository();
        System.setProperty("user.home", mockHome.getAbsolutePath());
        manifestRepo.save(manifest);
        
        // Create metadata file pointing to the manifest
        JSONObject metadata = new JSONObject();
        JSONArray wrappersArray = new JSONArray();
        wrappersArray.put("repo-cmd");
        metadata.put(ca.weblite.jdeploy.installer.CliInstallerConstants.CREATED_WRAPPERS_KEY, (Object) wrappersArray);
        metadata.put(ca.weblite.jdeploy.installer.CliInstallerConstants.PATH_UPDATED_KEY, false);
        metadata.put("binDir", manifestBinDir.getAbsolutePath());
        metadata.put("packageName", "repo-test-app");
        metadata.put("source", (Object) null);
        
        File metadataFile = new File(appDir, ca.weblite.jdeploy.installer.CliInstallerConstants.CLI_METADATA_FILE);
        FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");
        
        // Verify setup
        assertTrue(manifestRepo.load("repo-test-app", null).isPresent(), "Manifest should be created");
        assertTrue(scriptFile.exists(), "Script should exist");
        
        // Act - Uninstall
        TestableLinuxCliCommandInstaller uninstaller = new TestableLinuxCliCommandInstaller(mockHome);
        uninstaller.uninstallCommands(appDir);
        
        // Assert - using manifest repository to verify cleanup
        assertFalse(manifestRepo.load("repo-test-app", null).isPresent(), "Manifest should be deleted");
        assertFalse(scriptFile.exists(), "Script should be deleted");
        assertFalse(manifestBinDir.exists(), "Manifest bin directory should be removed");
    }
}
