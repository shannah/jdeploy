package ca.weblite.jdeploy.installer.util;

import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

/**
 * Unit tests for PackagePathResolver
 */
public class PackagePathResolverTest {

    private static final String TEST_PACKAGE = "test-package";
    private static final String TEST_VERSION = "1.0.0";
    private static final String TEST_SOURCE = "https://github.com/test/repo";

    @Test
    public void testResolvePackagePathNpm() {
        // Test NPM package path resolution
        File path = PackagePathResolver.resolvePackagePath(TEST_PACKAGE, null, null);

        assertNotNull("Path should not be null", path);
        assertTrue("Path should contain package name", path.getAbsolutePath().contains(TEST_PACKAGE));
        assertTrue("Path should contain .jdeploy", path.getAbsolutePath().contains(".jdeploy"));
        assertTrue("Path should contain packages", path.getAbsolutePath().contains("packages"));
    }

    @Test
    public void testResolvePackagePathNpmWithVersion() {
        // Test NPM package path resolution with version
        File path = PackagePathResolver.resolvePackagePath(TEST_PACKAGE, TEST_VERSION, null);

        assertNotNull("Path should not be null", path);
        assertTrue("Path should contain package name", path.getAbsolutePath().contains(TEST_PACKAGE));
        assertTrue("Path should contain version", path.getAbsolutePath().contains(TEST_VERSION));
    }

    @Test
    public void testResolvePackagePathGitHub() {
        // Test GitHub package path resolution
        File path = PackagePathResolver.resolvePackagePath(TEST_PACKAGE, null, TEST_SOURCE);

        assertNotNull("Path should not be null", path);
        assertTrue("Path should contain .jdeploy", path.getAbsolutePath().contains(".jdeploy"));
        assertTrue("Path should contain gh-packages", path.getAbsolutePath().contains("gh-packages"));
    }

    @Test
    public void testGetInstallPackagePathNpm() {
        // Test that install path uses architecture-specific directory
        File path = PackagePathResolver.getInstallPackagePath(TEST_PACKAGE, null, null);

        assertNotNull("Path should not be null", path);
        assertTrue("Path should contain .jdeploy", path.getAbsolutePath().contains(".jdeploy"));
        assertTrue("Path should contain packages", path.getAbsolutePath().contains("packages"));

        // Should contain architecture suffix
        String arch = ArchitectureUtil.getArchitecture();
        assertTrue("Path should contain architecture suffix",
                path.getAbsolutePath().contains("packages-" + arch));
    }

    @Test
    public void testGetInstallPackagePathGitHub() {
        // Test that install path uses architecture-specific directory for GitHub packages
        File path = PackagePathResolver.getInstallPackagePath(TEST_PACKAGE, null, TEST_SOURCE);

        assertNotNull("Path should not be null", path);
        assertTrue("Path should contain .jdeploy", path.getAbsolutePath().contains(".jdeploy"));
        assertTrue("Path should contain gh-packages", path.getAbsolutePath().contains("gh-packages"));

        // Should contain architecture suffix
        String arch = ArchitectureUtil.getArchitecture();
        assertTrue("Path should contain architecture suffix",
                path.getAbsolutePath().contains("gh-packages-" + arch));
    }

    @Test
    public void testGetAllPossiblePackagePaths() {
        // Test that we get both architecture-specific and legacy paths
        File[] paths = PackagePathResolver.getAllPossiblePackagePaths(TEST_PACKAGE, null, null);

        assertNotNull("Paths array should not be null", paths);
        assertEquals("Should return exactly 2 paths", 2, paths.length);

        // First path should be architecture-specific
        assertTrue("First path should be architecture-specific",
                paths[0].getAbsolutePath().contains("packages-" + ArchitectureUtil.getArchitecture()));

        // Second path should be legacy (no architecture suffix)
        assertFalse("Second path should be legacy (no architecture suffix after packages)",
                paths[1].getAbsolutePath().matches(".*packages-[^/\\\\]*[\\\\/].*"));
    }

    @Test
    public void testGetAllPossiblePackagePathsGitHub() {
        // Test that we get both architecture-specific and legacy paths for GitHub packages
        File[] paths = PackagePathResolver.getAllPossiblePackagePaths(TEST_PACKAGE, null, TEST_SOURCE);

        assertNotNull("Paths array should not be null", paths);
        assertEquals("Should return exactly 2 paths", 2, paths.length);

        // First path should be architecture-specific
        assertTrue("First path should be architecture-specific",
                paths[0].getAbsolutePath().contains("gh-packages-" + ArchitectureUtil.getArchitecture()));

        // Second path should be legacy
        assertTrue("Second path should contain gh-packages",
                paths[1].getAbsolutePath().contains("gh-packages"));
        assertFalse("Second path should not have architecture suffix in gh-packages",
                paths[1].getAbsolutePath().matches(".*gh-packages-[^/\\\\]*[\\\\/].*"));
    }

    @Test
    public void testPathsAreDifferent() {
        // Verify that architecture-specific and legacy paths are different
        File[] paths = PackagePathResolver.getAllPossiblePackagePaths(TEST_PACKAGE, null, null);

        assertNotEquals("Architecture-specific and legacy paths should be different",
                paths[0].getAbsolutePath(), paths[1].getAbsolutePath());
    }
}
