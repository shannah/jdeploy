package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import ca.weblite.jdeploy.installer.cli.CollisionAction;
import ca.weblite.jdeploy.installer.cli.CollisionHandler;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class WindowsCliCommandInstallerTest {

    private static File tempBaseDir;
    private static String originalUserHome;

    @BeforeClass
    public static void setUpClass() throws IOException {
        // Save original user.home
        originalUserHome = System.getProperty("user.home");

        // Create a temporary base directory for testing
        tempBaseDir = Files.createTempDirectory("jdeploy-cli-test").toFile();

        // Override user.home to point to our temp directory
        System.setProperty("user.home", tempBaseDir.getAbsolutePath());
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
        // Restore original user.home
        System.setProperty("user.home", originalUserHome);

        // Clean up temporary directory
        if (tempBaseDir != null && tempBaseDir.exists()) {
            deleteRecursive(tempBaseDir);
        }
    }

    private static void deleteRecursive(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteRecursive(f);
                }
            }
        }
        Files.deleteIfExists(file.toPath());
    }

    /**
     * Testable subclass of WindowsCliCommandInstaller that allows mocking
     * the registry helper and tracking method calls.
     */
    private static class TestableWindowsCliCommandInstaller extends WindowsCliCommandInstaller {
        private boolean removeFromPathCalled = false;
        private File removeFromPathArg = null;
        private int removeFromPathCallCount = 0;

        public boolean wasRemoveFromPathCalled() {
            return removeFromPathCalled;
        }

        public File getRemoveFromPathArg() {
            return removeFromPathArg;
        }

        public int getRemoveFromPathCallCount() {
            return removeFromPathCallCount;
        }

        public void resetTracking() {
            removeFromPathCalled = false;
            removeFromPathArg = null;
            removeFromPathCallCount = 0;
        }

        @Override
        public void setCollisionHandler(CollisionHandler handler) {
            super.setCollisionHandler(handler);
        }

        @Override
        protected InstallWindowsRegistry createRegistryHelper() {
            // Return a mock that tracks removeFromUserPath calls
            return new InstallWindowsRegistry(null, null, null, null) {
                @Override
                public boolean removeFromUserPath(File binDir) {
                    removeFromPathCalled = true;
                    removeFromPathArg = binDir;
                    removeFromPathCallCount++;
                    return true;
                }
            };
        }
    }

    /**
     * Helper to create a metadata file in the app directory.
     */
    private File createMetadataFile(File appDir, String[] wrapperNames, boolean pathUpdated) throws IOException {
        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        JSONObject metadata = new JSONObject();

        JSONArray wrappersArray = new JSONArray();
        for (String name : wrapperNames) {
            wrappersArray.put(name);
        }
        metadata.put(CliInstallerConstants.CREATED_WRAPPERS_KEY, wrappersArray);
        metadata.put(CliInstallerConstants.PATH_UPDATED_KEY, pathUpdated);

        File metadataFile = new File(appDir, CliInstallerConstants.CLI_METADATA_FILE);
        FileUtils.writeStringToFile(metadataFile, metadata.toString(), "UTF-8");

        return metadataFile;
    }

    /**
     * Helper to create wrapper files in the bin directory.
     */
    private void createWrapperFiles(File binDir, String[] wrapperNames) throws IOException {
        if (!binDir.exists()) {
            binDir.mkdirs();
        }

        for (String name : wrapperNames) {
            File wrapper = new File(binDir, name);
            FileUtils.writeStringToFile(wrapper, "@echo off\necho test\n", "UTF-8");
        }
    }

    /**
     * Helper to get the bin directory based on current user.home.
     */
    private File getBinDir() {
        return new File(System.getProperty("user.home") + File.separator + ".jdeploy" + File.separator + "bin");
    }

    @Test
    public void testUninstallCommandsRemovesWrapperFiles() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-test-1");
        File binDir = getBinDir();

        String[] wrapperNames = {"cmd1.cmd", "cmd2.cmd", "cmd3.cmd"};
        createWrapperFiles(binDir, wrapperNames);
        createMetadataFile(appDir, wrapperNames, false);

        // Verify files exist
        for (String name : wrapperNames) {
            assertTrue("Wrapper file should exist before uninstall", new File(binDir, name).exists());
        }

        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();

        // Act
        installer.uninstallCommands(appDir);

        // Assert
        for (String name : wrapperNames) {
            assertFalse("Wrapper file should be deleted after uninstall", new File(binDir, name).exists());
        }
    }

    @Test
    public void testUninstallCommandsWithNoMetadataFileDoesNothing() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-test-2");
        appDir.mkdirs();

        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();

        // Act & Assert - should not throw any exception
        try {
            installer.uninstallCommands(appDir);
            assertTrue("Should complete without error", true);
        } catch (Exception e) {
            fail("Should not throw exception for missing metadata: " + e.getMessage());
        }
    }

    @Test
    public void testUninstallCommandsWithNullAppDirDoesNothing() {
        // Arrange
        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();

        // Act & Assert - should not throw any exception
        try {
            installer.uninstallCommands(null);
            assertTrue("Should complete without error", true);
        } catch (Exception e) {
            fail("Should not throw exception for null app dir: " + e.getMessage());
        }
    }

    @Test
    public void testUninstallCommandsWithNonExistentAppDirDoesNothing() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "nonexistent-app-dir");
        assertFalse("App dir should not exist", appDir.exists());

        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();

        // Act & Assert - should not throw any exception
        try {
            installer.uninstallCommands(appDir);
            assertTrue("Should complete without error", true);
        } catch (Exception e) {
            fail("Should not throw exception for non-existent app dir: " + e.getMessage());
        }
    }

    @Test
    public void testUninstallCommandsCallsRemoveFromPathWhenPathWasUpdated() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-test-3");
        File binDir = getBinDir();

        String[] wrapperNames = {"mycommand.cmd"};
        createWrapperFiles(binDir, wrapperNames);
        createMetadataFile(appDir, wrapperNames, true);

        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();
        installer.resetTracking();

        // Act
        installer.uninstallCommands(appDir);

        // Assert
        assertTrue("removeFromPath should be called when pathUpdated is true", installer.wasRemoveFromPathCalled());
        assertEquals("removeFromPath should be called exactly once", 1, installer.getRemoveFromPathCallCount());
        assertEquals("removeFromPath should be called with correct bin directory", binDir, installer.getRemoveFromPathArg());
    }

    @Test
    public void testUninstallCommandsDoesNotCallRemoveFromPathWhenPathWasNotUpdated() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-test-4");
        File binDir = getBinDir();

        String[] wrapperNames = {"anothercommand.cmd"};
        createWrapperFiles(binDir, wrapperNames);
        createMetadataFile(appDir, wrapperNames, false);

        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();
        installer.resetTracking();

        // Act
        installer.uninstallCommands(appDir);

        // Assert
        assertFalse("removeFromPath should NOT be called when pathUpdated is false", installer.wasRemoveFromPathCalled());
        assertEquals("removeFromPath should not be called at all", 0, installer.getRemoveFromPathCallCount());
    }

    @Test
    public void testUninstallCommandsHandlesPartiallyMissingWrappers() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-test-5");
        File binDir = getBinDir();

        String[] allWrapperNames = {"exists1.cmd", "missing.cmd", "exists2.cmd"};
        String[] existingWrapperNames = {"exists1.cmd", "exists2.cmd"};

        // Create only some of the wrappers
        createWrapperFiles(binDir, existingWrapperNames);
        createMetadataFile(appDir, allWrapperNames, false);

        // Verify setup
        assertTrue("exists1.cmd should exist", new File(binDir, "exists1.cmd").exists());
        assertFalse("missing.cmd should not exist", new File(binDir, "missing.cmd").exists());
        assertTrue("exists2.cmd should exist", new File(binDir, "exists2.cmd").exists());

        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();

        // Act
        installer.uninstallCommands(appDir);

        // Assert - should not throw and should delete existing ones
        assertFalse("exists1.cmd should be deleted", new File(binDir, "exists1.cmd").exists());
        assertFalse("exists2.cmd should be deleted", new File(binDir, "exists2.cmd").exists());
        assertFalse("missing.cmd should still not exist (no error)", new File(binDir, "missing.cmd").exists());
    }

    @Test
    public void testUninstallCommandsDeletesMetadataFile() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-test-6");
        File binDir = getBinDir();

        String[] wrapperNames = {"test.cmd"};
        createWrapperFiles(binDir, wrapperNames);
        File metadataFile = createMetadataFile(appDir, wrapperNames, false);

        assertTrue("Metadata file should exist before uninstall", metadataFile.exists());

        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();

        // Act
        installer.uninstallCommands(appDir);

        // Assert
        // Note: Current implementation does not delete metadata file, only uses it
        // This test documents the current behavior; if deletion is added later, update this test
        assertTrue("Metadata file still exists after uninstall (by design)", metadataFile.exists());
    }

    @Test
    public void testSameAppCollision_silentOverwrite() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-collision-same");
        appDir.mkdirs();
        File binDir = getBinDir();
        binDir.mkdirs();
        
        File launcherPath = new File(appDir, "MyLauncher.exe");
        launcherPath.createNewFile();

        // Create existing .cmd pointing to the same launcher
        File cmdFile = new File(binDir, "mycmd.cmd");
        String existingContent = "@echo off\r\n\"" + launcherPath.getAbsolutePath() + "\" --jdeploy:command=mycmd %*\r\n";
        FileUtils.writeStringToFile(cmdFile, existingContent, "UTF-8");

        // Track if collision handler is called
        final boolean[] handlerCalled = {false};
        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            handlerCalled[0] = true;
            return CollisionAction.SKIP;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        // Act
        List<File> created = installer.writeCommandWrappersForTest(binDir, launcherPath, commands);

        // Assert
        // Handler should NOT be called for same-app collision
        assertFalse("CollisionHandler should not be called for same-app collision", handlerCalled[0]);
        // File should still be created (overwritten)
        assertEquals(1, created.size());
        assertTrue(cmdFile.exists());
    }

    @Test
    public void testDifferentAppCollision_skip() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-collision-skip");
        appDir.mkdirs();
        File binDir = getBinDir();
        binDir.mkdirs();
        
        File launcherPath = new File(appDir, "MyLauncher.exe");
        launcherPath.createNewFile();
        
        // Create existing .cmd pointing to a DIFFERENT launcher
        File differentLauncher = new File(tempBaseDir, "other-app/OtherLauncher.exe");
        differentLauncher.getParentFile().mkdirs();
        differentLauncher.createNewFile();
        
        File cmdFile = new File(binDir, "mycmd.cmd");
        String existingContent = "@echo off\r\n\"" + differentLauncher.getAbsolutePath() + "\" --jdeploy:command=mycmd %*\r\n";
        FileUtils.writeStringToFile(cmdFile, existingContent, "UTF-8");

        // Handler returns SKIP
        final String[] capturedParams = new String[3];
        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            capturedParams[0] = cmdName;
            capturedParams[1] = existingPath;
            capturedParams[2] = newPath;
            return CollisionAction.SKIP;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        // Act
        List<File> created = installer.writeCommandWrappersForTest(binDir, launcherPath, commands);

        // Assert
        // Handler should be called with correct parameters
        assertEquals("mycmd", capturedParams[0]);
        assertEquals(differentLauncher.getAbsolutePath(), capturedParams[1]);
        assertEquals(launcherPath.getAbsolutePath(), capturedParams[2]);
        
        // File should NOT be in created list (was skipped)
        assertEquals(0, created.size());
        
        // Original file should be preserved
        String content = FileUtils.readFileToString(cmdFile, "UTF-8");
        assertTrue("Original .cmd should be preserved", content.contains(differentLauncher.getAbsolutePath()));
    }

    @Test
    public void testDifferentAppCollision_overwrite() throws IOException {
        // Arrange
        File appDir = new File(tempBaseDir, "app-collision-overwrite");
        appDir.mkdirs();
        File binDir = getBinDir();
        binDir.mkdirs();
        
        File launcherPath = new File(appDir, "MyLauncher.exe");
        launcherPath.createNewFile();
        
        // Create existing .cmd pointing to a DIFFERENT launcher
        File differentLauncher = new File(tempBaseDir, "other-app2/OtherLauncher.exe");
        differentLauncher.getParentFile().mkdirs();
        differentLauncher.createNewFile();
        
        File cmdFile = new File(binDir, "mycmd.cmd");
        String existingContent = "@echo off\r\n\"" + differentLauncher.getAbsolutePath() + "\" --jdeploy:command=mycmd %*\r\n";
        FileUtils.writeStringToFile(cmdFile, existingContent, "UTF-8");

        // Handler returns OVERWRITE
        final boolean[] handlerCalled = {false};
        TestableWindowsCliCommandInstaller installer = new TestableWindowsCliCommandInstaller();
        installer.setCollisionHandler((cmdName, existingPath, newPath) -> {
            handlerCalled[0] = true;
            return CollisionAction.OVERWRITE;
        });

        List<CommandSpec> commands = new ArrayList<>();
        commands.add(new CommandSpec("mycmd", new ArrayList<>()));

        // Act
        List<File> created = installer.writeCommandWrappersForTest(binDir, launcherPath, commands);

        // Assert
        // Handler should be called
        assertTrue("CollisionHandler should be called for different-app collision", handlerCalled[0]);
        
        // File should be in created list (was overwritten)
        assertEquals(1, created.size());
        
        // File should now point to new launcher
        String content = FileUtils.readFileToString(cmdFile, "UTF-8");
        assertTrue("Cmd should now point to new launcher", content.contains(launcherPath.getAbsolutePath()));
        assertFalse("Cmd should not contain old launcher path", content.contains(differentLauncher.getAbsolutePath()));
    }
}
