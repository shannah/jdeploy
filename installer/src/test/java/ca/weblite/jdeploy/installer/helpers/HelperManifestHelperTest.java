package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.tools.platform.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HelperManifestHelper.
 */
public class HelperManifestHelperTest {

    private UninstallManifestBuilder builder;

    @BeforeEach
    public void setUp() {
        builder = new UninstallManifestBuilder();
        // Set up required package info for the builder
        builder.withPackageInfo("test-app", "npm", "1.0.0", "x64");
    }

    // ========== Validation tests ==========

    @Test
    public void testAddHelperToManifest_NullBuilder() {
        File helperExe = new File("/path/to/helper.exe");
        File contextDir = new File("/path/to/.jdeploy-files");
        HelperInstallationResult result = HelperInstallationResult.success(helperExe, contextDir);

        assertThrows(IllegalArgumentException.class, () -> {
            HelperManifestHelper.addHelperToManifest(null, result);
        });
    }

    @Test
    public void testAddHelperToManifest_NullResult() {
        assertThrows(IllegalArgumentException.class, () -> {
            HelperManifestHelper.addHelperToManifest(builder, null);
        });
    }

    @Test
    public void testAddHelperToManifest_FailedResult() {
        HelperInstallationResult failedResult = HelperInstallationResult.failure("Installation failed");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            HelperManifestHelper.addHelperToManifest(builder, failedResult);
        });

        assertTrue(exception.getMessage().contains("failed"),
            "Exception should mention failed installation");
    }

    // ========== macOS tests ==========

    @Test
    public void testAddHelperToManifest_MacOS_AddsAppBundle() {
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac
        }

        File helperApp = new File("/Users/test/Applications/MyApp Helper/MyApp Helper.app");
        File contextDir = new File("/Users/test/Applications/MyApp Helper/.jdeploy-files");
        HelperInstallationResult result = HelperInstallationResult.success(helperApp, contextDir);

        HelperManifestHelper.addHelperToManifest(builder, result);

        UninstallManifest manifest = builder.build();
        List<UninstallManifest.InstalledDirectory> directories = manifest.getDirectories();

        // Should have 3 directories: .app bundle, context dir, and parent dir
        assertEquals(3, directories.size(), "Should have 3 directory entries on macOS");

        // Verify .app bundle is added with ALWAYS strategy
        boolean foundAppBundle = directories.stream()
            .anyMatch(d -> d.getPath().endsWith(".app") &&
                          d.getCleanup() == UninstallManifest.CleanupStrategy.ALWAYS);
        assertTrue(foundAppBundle, "Should add .app bundle with ALWAYS strategy");

        // Verify context directory is added with ALWAYS strategy
        boolean foundContextDir = directories.stream()
            .anyMatch(d -> d.getPath().endsWith(".jdeploy-files") &&
                          d.getCleanup() == UninstallManifest.CleanupStrategy.ALWAYS);
        assertTrue(foundContextDir, "Should add context directory with ALWAYS strategy");

        // Verify parent directory is added with IF_EMPTY strategy
        boolean foundParentDir = directories.stream()
            .anyMatch(d -> d.getPath().endsWith("MyApp Helper") &&
                          !d.getPath().endsWith(".app") &&
                          d.getCleanup() == UninstallManifest.CleanupStrategy.IF_EMPTY);
        assertTrue(foundParentDir, "Should add parent directory with IF_EMPTY strategy");
    }

    @Test
    public void testAddHelperToManifest_MacOS_NoFiles() {
        if (!Platform.getSystemPlatform().isMac()) {
            return; // Skip on non-Mac
        }

        File helperApp = new File("/Users/test/Applications/MyApp Helper/MyApp Helper.app");
        File contextDir = new File("/Users/test/Applications/MyApp Helper/.jdeploy-files");
        HelperInstallationResult result = HelperInstallationResult.success(helperApp, contextDir);

        HelperManifestHelper.addHelperToManifest(builder, result);

        UninstallManifest manifest = builder.build();

        // On macOS, the .app bundle is treated as a directory, not a file
        assertTrue(manifest.getFiles().isEmpty(),
            "macOS should not add any file entries (bundle is a directory)");
    }

    // ========== Windows/Linux tests ==========

    @Test
    public void testAddHelperToManifest_WindowsLinux_AddsExecutableAsFile() {
        if (Platform.getSystemPlatform().isMac()) {
            return; // Skip on Mac
        }

        File helperExe = new File("/path/to/app/helpers/myapp-helper.exe");
        File contextDir = new File("/path/to/app/helpers/.jdeploy-files");
        HelperInstallationResult result = HelperInstallationResult.success(helperExe, contextDir);

        HelperManifestHelper.addHelperToManifest(builder, result);

        UninstallManifest manifest = builder.build();
        List<UninstallManifest.InstalledFile> files = manifest.getFiles();

        // Should have 1 file: the helper executable
        assertEquals(1, files.size(), "Should have 1 file entry on Windows/Linux");

        UninstallManifest.InstalledFile helperFile = files.get(0);
        assertEquals(UninstallManifest.FileType.BINARY, helperFile.getType(),
            "Helper should be marked as BINARY");
        assertTrue(helperFile.getPath().contains("helper"),
            "File path should contain 'helper'");
    }

    @Test
    public void testAddHelperToManifest_WindowsLinux_AddsDirectories() {
        if (Platform.getSystemPlatform().isMac()) {
            return; // Skip on Mac
        }

        File helperExe = new File("/path/to/app/helpers/myapp-helper.exe");
        File contextDir = new File("/path/to/app/helpers/.jdeploy-files");
        HelperInstallationResult result = HelperInstallationResult.success(helperExe, contextDir);

        HelperManifestHelper.addHelperToManifest(builder, result);

        UninstallManifest manifest = builder.build();
        List<UninstallManifest.InstalledDirectory> directories = manifest.getDirectories();

        // Should have 2 directories: context dir and parent helpers dir
        assertEquals(2, directories.size(), "Should have 2 directory entries on Windows/Linux");

        // Verify context directory is added with ALWAYS strategy
        boolean foundContextDir = directories.stream()
            .anyMatch(d -> d.getPath().endsWith(".jdeploy-files") &&
                          d.getCleanup() == UninstallManifest.CleanupStrategy.ALWAYS);
        assertTrue(foundContextDir, "Should add context directory with ALWAYS strategy");

        // Verify helpers directory is added with IF_EMPTY strategy
        boolean foundHelpersDir = directories.stream()
            .anyMatch(d -> d.getPath().endsWith("helpers") &&
                          d.getCleanup() == UninstallManifest.CleanupStrategy.IF_EMPTY);
        assertTrue(foundHelpersDir, "Should add helpers directory with IF_EMPTY strategy");
    }

    // ========== Description tests ==========

    @Test
    public void testAddHelperToManifest_IncludesDescriptions() {
        File helperExe;
        File contextDir;

        if (Platform.getSystemPlatform().isMac()) {
            helperExe = new File("/Users/test/Applications/MyApp Helper/MyApp Helper.app");
            contextDir = new File("/Users/test/Applications/MyApp Helper/.jdeploy-files");
        } else {
            helperExe = new File("/path/to/app/helpers/myapp-helper.exe");
            contextDir = new File("/path/to/app/helpers/.jdeploy-files");
        }

        HelperInstallationResult result = HelperInstallationResult.success(helperExe, contextDir);
        HelperManifestHelper.addHelperToManifest(builder, result);

        UninstallManifest manifest = builder.build();

        // Verify all entries have descriptions
        for (UninstallManifest.InstalledDirectory dir : manifest.getDirectories()) {
            assertNotNull(dir.getDescription(),
                "Directory entry should have a description: " + dir.getPath());
            assertFalse(dir.getDescription().isEmpty(),
                "Directory description should not be empty: " + dir.getPath());
        }

        for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
            assertNotNull(file.getDescription(),
                "File entry should have a description: " + file.getPath());
            assertFalse(file.getDescription().isEmpty(),
                "File description should not be empty: " + file.getPath());
        }
    }

    // ========== Path tests ==========

    @Test
    public void testAddHelperToManifest_UsesAbsolutePaths() {
        File helperExe;
        File contextDir;

        if (Platform.getSystemPlatform().isMac()) {
            helperExe = new File("/Users/test/Applications/MyApp Helper/MyApp Helper.app");
            contextDir = new File("/Users/test/Applications/MyApp Helper/.jdeploy-files");
        } else {
            helperExe = new File("/path/to/app/helpers/myapp-helper.exe");
            contextDir = new File("/path/to/app/helpers/.jdeploy-files");
        }

        HelperInstallationResult result = HelperInstallationResult.success(helperExe, contextDir);
        HelperManifestHelper.addHelperToManifest(builder, result);

        UninstallManifest manifest = builder.build();

        // All paths should be absolute
        for (UninstallManifest.InstalledDirectory dir : manifest.getDirectories()) {
            assertTrue(new File(dir.getPath()).isAbsolute(),
                "Directory path should be absolute: " + dir.getPath());
        }

        for (UninstallManifest.InstalledFile file : manifest.getFiles()) {
            assertTrue(new File(file.getPath()).isAbsolute(),
                "File path should be absolute: " + file.getPath());
        }
    }

    // ========== Multiple calls test ==========

    @Test
    public void testAddHelperToManifest_CanBeCalledMultipleTimes() {
        // This tests that multiple helpers could be added (e.g., for different apps)
        File helperExe1;
        File contextDir1;
        File helperExe2;
        File contextDir2;

        if (Platform.getSystemPlatform().isMac()) {
            helperExe1 = new File("/Users/test/Applications/App1 Helper/App1 Helper.app");
            contextDir1 = new File("/Users/test/Applications/App1 Helper/.jdeploy-files");
            helperExe2 = new File("/Users/test/Applications/App2 Helper/App2 Helper.app");
            contextDir2 = new File("/Users/test/Applications/App2 Helper/.jdeploy-files");
        } else {
            helperExe1 = new File("/path/to/app1/helpers/app1-helper.exe");
            contextDir1 = new File("/path/to/app1/helpers/.jdeploy-files");
            helperExe2 = new File("/path/to/app2/helpers/app2-helper.exe");
            contextDir2 = new File("/path/to/app2/helpers/.jdeploy-files");
        }

        HelperInstallationResult result1 = HelperInstallationResult.success(helperExe1, contextDir1);
        HelperInstallationResult result2 = HelperInstallationResult.success(helperExe2, contextDir2);

        // Should not throw when called multiple times
        assertDoesNotThrow(() -> {
            HelperManifestHelper.addHelperToManifest(builder, result1);
            HelperManifestHelper.addHelperToManifest(builder, result2);
        });

        UninstallManifest manifest = builder.build();

        // Should have entries for both helpers
        if (Platform.getSystemPlatform().isMac()) {
            // macOS: 3 dirs per helper = 6 total
            assertEquals(6, manifest.getDirectories().size(),
                "Should have directory entries for both helpers");
        } else {
            // Windows/Linux: 1 file + 2 dirs per helper
            assertEquals(2, manifest.getFiles().size(),
                "Should have file entries for both helpers");
            assertEquals(4, manifest.getDirectories().size(),
                "Should have directory entries for both helpers");
        }
    }
}
