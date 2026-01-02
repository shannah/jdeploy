package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.uninstall.UninstallManifestRepository;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.util.PackagePathResolver;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InstallationDetectionService.
 */
public class InstallationDetectionServiceTest {

    private UninstallManifestRepository mockRepository;
    private InstallationDetectionService service;
    private File jdeployHome;

    @BeforeEach
    public void setUp() throws Exception {
        // Create mock repository
        mockRepository = Mockito.mock(UninstallManifestRepository.class);
        service = new InstallationDetectionService(mockRepository);

        // Store the original jdeploy home
        jdeployHome = PackagePathResolver.getJDeployHome();
    }

    @AfterEach
    public void tearDown() throws Exception {
        // Clean up any test directories that were created
        if (jdeployHome != null && jdeployHome.exists()) {
            // Clean up test packages
            File packagesDir = new File(jdeployHome, "packages");
            if (packagesDir.exists()) {
                FileUtils.deleteDirectory(packagesDir);
            }
            File ghPackagesDir = new File(jdeployHome, "gh-packages");
            if (ghPackagesDir.exists()) {
                FileUtils.deleteDirectory(ghPackagesDir);
            }
        }
    }

    @Test
    public void testIsInstalled_PackageDirectoryExists_ReturnsTrue() throws Exception {
        // Setup: Create a package directory
        String packageName = "test-package";
        File packagePath = PackagePathResolver.resolvePackagePath(packageName, null, null);
        packagePath.mkdirs();

        // Mock: No uninstall manifest
        when(mockRepository.load(eq(packageName), eq(null))).thenReturn(Optional.empty());

        try {
            // Test
            boolean result = service.isInstalled(packageName, null);

            // Verify
            assertTrue(result, "Should return true when package directory exists");
        } finally {
            // Cleanup
            if (packagePath.exists()) {
                FileUtils.deleteDirectory(packagePath);
            }
        }
    }

    @Test
    public void testIsInstalled_UninstallManifestExists_ReturnsTrue() throws Exception {
        // Setup: No package directory, but manifest exists
        String packageName = "test-package";
        String source = "https://github.com/test/repo";

        // Create a minimal real UninstallManifest instance (cannot mock final class)
        UninstallManifest manifest = UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name(packageName)
                .source(source)
                .version("1.0.0")
                .fullyQualifiedName(packageName)
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0")
                .build())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .build();

        // Mock: Uninstall manifest exists
        when(mockRepository.load(eq(packageName), eq(source))).thenReturn(Optional.of(manifest));

        // Test
        boolean result = service.isInstalled(packageName, source);

        // Verify
        assertTrue(result, "Should return true when uninstall manifest exists");
    }

    @Test
    public void testIsInstalled_BothExist_ReturnsTrue() throws Exception {
        // Setup: Both package directory and manifest exist
        String packageName = "test-package";
        String source = "https://github.com/test/repo";
        File packagePath = PackagePathResolver.resolvePackagePath(packageName, null, source);
        packagePath.mkdirs();

        // Create a minimal real UninstallManifest instance (cannot mock final class)
        UninstallManifest manifest = UninstallManifest.builder()
            .version("1.0")
            .packageInfo(UninstallManifest.PackageInfo.builder()
                .name(packageName)
                .source(source)
                .version("1.0.0")
                .fullyQualifiedName(packageName)
                .architecture("x64")
                .installedAt(Instant.now())
                .installerVersion("1.0")
                .build())
            .files(Collections.emptyList())
            .directories(Collections.emptyList())
            .build();

        when(mockRepository.load(eq(packageName), eq(source))).thenReturn(Optional.of(manifest));

        try {
            // Test
            boolean result = service.isInstalled(packageName, source);

            // Verify
            assertTrue(result, "Should return true when both package directory and manifest exist");
        } finally {
            // Cleanup
            if (packagePath.exists()) {
                FileUtils.deleteDirectory(packagePath);
            }
        }
    }

    @Test
    public void testIsInstalled_NeitherExists_ReturnsFalse() throws Exception {
        // Setup: Neither package directory nor manifest exists
        String packageName = "nonexistent-package";
        String source = null;

        // Mock: No uninstall manifest
        when(mockRepository.load(eq(packageName), eq(source))).thenReturn(Optional.empty());

        // Test
        boolean result = service.isInstalled(packageName, source);

        // Verify
        assertFalse(result, "Should return false when neither package directory nor manifest exists");
    }

    @Test
    public void testIsInstalled_EmptySourceNormalizedToNull() throws Exception {
        // Setup: Empty source string should be treated as null
        String packageName = "test-package";
        String emptySource = "";

        // Mock: Repository should receive null, not empty string
        when(mockRepository.load(eq(packageName), eq(null))).thenReturn(Optional.empty());

        // Test
        boolean result = service.isInstalled(packageName, emptySource);

        // Verify
        assertFalse(result, "Should handle empty source as null");
    }

    @Test
    public void testIsInstalled_WithVersion_PackageDirectoryExists() throws Exception {
        // Setup: Create a versioned package directory
        String packageName = "test-package";
        String version = "1.0.0";
        File packagePath = PackagePathResolver.resolvePackagePath(packageName, version, null);
        packagePath.mkdirs();

        // Mock: No uninstall manifest
        when(mockRepository.load(eq(packageName), eq(null))).thenReturn(Optional.empty());

        try {
            // Test
            boolean result = service.isInstalled(packageName, version, null);

            // Verify
            assertTrue(result, "Should return true when versioned package directory exists");
        } finally {
            // Cleanup
            if (packagePath.exists()) {
                FileUtils.deleteDirectory(packagePath.getParentFile());
            }
        }
    }

    @Test
    public void testIsInstalled_NullPackageName_ThrowsException() {
        // Test
        assertThrows(IllegalArgumentException.class, () -> {
            service.isInstalled(null, null);
        }, "Should throw IllegalArgumentException for null package name");
    }

    @Test
    public void testIsInstalled_EmptyPackageName_ThrowsException() {
        // Test
        assertThrows(IllegalArgumentException.class, () -> {
            service.isInstalled("   ", null);
        }, "Should throw IllegalArgumentException for empty package name");
    }

    @Test
    public void testIsInstalled_ManifestLoadThrowsException_ReturnsFalse() throws Exception {
        // Setup: Manifest load throws exception
        String packageName = "test-package";
        when(mockRepository.load(anyString(), anyString())).thenThrow(new RuntimeException("Test exception"));

        // Test
        boolean result = service.isInstalled(packageName, null);

        // Verify - should handle exception gracefully and return false
        assertFalse(result, "Should return false when manifest load throws exception");
    }

    @Test
    public void testIsInstalled_GitHubPackage_PackageDirectoryExists() throws Exception {
        // Setup: Create a GitHub package directory
        String packageName = "test-package";
        String source = "https://github.com/test/repo";
        File packagePath = PackagePathResolver.resolvePackagePath(packageName, null, source);
        packagePath.mkdirs();

        // Mock: No uninstall manifest
        when(mockRepository.load(eq(packageName), eq(source))).thenReturn(Optional.empty());

        try {
            // Test
            boolean result = service.isInstalled(packageName, source);

            // Verify
            assertTrue(result, "Should return true when GitHub package directory exists");
        } finally {
            // Cleanup
            if (packagePath.exists()) {
                FileUtils.deleteDirectory(packagePath);
            }
        }
    }

    @Test
    public void testIsInstalled_TwoParameterVersion_CallsThreeParameterVersion() throws Exception {
        // Setup: Test that the two-parameter version calls the three-parameter version with null version
        String packageName = "test-package";
        String source = "https://github.com/test/repo";

        // Mock: No package or manifest
        when(mockRepository.load(eq(packageName), eq(source))).thenReturn(Optional.empty());

        // Test: Call two-parameter version
        boolean result = service.isInstalled(packageName, source);

        // Verify: Should behave the same as calling with null version
        assertFalse(result, "Two-parameter version should work correctly");
    }
}
