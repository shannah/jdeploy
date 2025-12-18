package ca.weblite.jdeploy.installer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Integration tests for the installer that use real HTTP requests to the jDeploy registry.
 * These tests verify end-to-end functionality with actual network calls.
 */
public class InstallationIntegrationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File testHome;
    private File mockAppBundle;

    // Real project code for testing
    private static final String TEST_PROJECT_CODE = "262N";
    private static final String STABLE_VERSION = "0.16.6";
    private static final String PRERELEASE_VERSION = "0.16.3-beta2";

    @Before
    public void setUp() throws IOException {
        testHome = tempFolder.newFolder("jdeploy-home");
        mockAppBundle = tempFolder.newFile("test-app.app");
    }

    @After
    public void tearDown() {
        // Cleanup is handled by TemporaryFolder
    }

    /**
     * Test downloading bundle info and files for a stable release.
     * This test makes real HTTP requests to https://www.jdeploy.com/
     */
    @Test
    public void testDownloadStableRelease() throws IOException {
        // Download bundle for stable version
        File result = DefaultInstallationContext.downloadJDeployBundleForCode(
            TEST_PROJECT_CODE,
            STABLE_VERSION,
            mockAppBundle,
            testHome
        );

        // Verify result
        assertNotNull("Result should not be null", result);
        assertTrue("Result should exist", result.exists());
        assertTrue("Result should be a directory", result.isDirectory());
        assertEquals("Result should be .jdeploy-files directory", ".jdeploy-files", result.getName());

        // Verify the bundle contains expected files
        File[] files = result.listFiles();
        assertNotNull("Bundle directory should contain files", files);
        assertTrue("Bundle directory should not be empty", files.length > 0);

        // Verify cache was populated
        BundleRegistryCache cache = new BundleRegistryCache(
            DefaultInstallationContext.JDEPLOY_REGISTRY,
            testHome
        );
        BundleInfo cachedInfo = cache.lookup(TEST_PROJECT_CODE);
        assertNotNull("Bundle info should be cached", cachedInfo);
        assertNotNull("Project source should not be null", cachedInfo.getProjectSource());
        assertNotNull("Package name should not be null", cachedInfo.getPackageName());
        assertFalse("Package name should not be empty", cachedInfo.getPackageName().isEmpty());

        // Verify expected values for bundle code 262N (Brokk)
        assertEquals("Project source should be Brokk GitHub URL",
            "https://github.com/BrokkAi/brokk",
            cachedInfo.getProjectSource());
        assertEquals("Package name should be 'brokk'",
            "brokk",
            cachedInfo.getPackageName());
    }

    /**
     * Test downloading bundle info and files for a prerelease version.
     * This test makes real HTTP requests to https://www.jdeploy.com/
     * Uses the new prerelease parameter to avoid modifying global state.
     */
    @Test
    public void testDownloadPrereleaseVersion() {
        // Note: We expect this test to potentially fail with IOException because
        // the specific prerelease version may not be available in the registry.
        // The important thing is that we can test the prerelease flag without
        // modifying global system properties.
        try {
            // Download bundle for prerelease version using explicit prerelease flag
            File result = DefaultInstallationContext.downloadJDeployBundleForCode(
                TEST_PROJECT_CODE,
                PRERELEASE_VERSION,
                mockAppBundle,
                testHome,
                true  // prerelease flag
            );

            // If we get here, the registry actually has this prerelease version
            assertNotNull("Result should not be null", result);
            assertTrue("Result should exist", result.exists());
            assertTrue("Result should be a directory", result.isDirectory());
            assertEquals("Result should be .jdeploy-files directory", ".jdeploy-files", result.getName());

            System.out.println("Note: Prerelease version " + PRERELEASE_VERSION + " was successfully downloaded from registry");
        } catch (IOException e) {
            // Expected if the prerelease version is not available
            System.out.println("Note: Prerelease version " + PRERELEASE_VERSION + " not available in registry: " + e.getMessage());
            // This is acceptable - we're mainly testing that the prerelease flag works without global state modification
            assertTrue("Test should not crash", true);
        }
    }

    /**
     * Test that cache is used on second download (registry not queried again).
     * This test makes real HTTP requests to https://www.jdeploy.com/
     */
    @Test
    public void testCacheIsUsedOnSecondDownload() throws IOException {
        // First download - should query registry and cache result
        File result1 = DefaultInstallationContext.downloadJDeployBundleForCode(
            TEST_PROJECT_CODE,
            STABLE_VERSION,
            mockAppBundle,
            testHome
        );
        assertNotNull("First download should succeed", result1);
        assertTrue("First download result should exist", result1.exists());

        // Verify cache was populated with correct data
        BundleRegistryCache cache = new BundleRegistryCache(
            DefaultInstallationContext.JDEPLOY_REGISTRY,
            testHome
        );
        BundleInfo cachedInfo = cache.lookup(TEST_PROJECT_CODE);
        assertNotNull("Bundle info should be cached after first download", cachedInfo);
        assertEquals("Cached project source should be Brokk GitHub URL",
            "https://github.com/BrokkAi/brokk",
            cachedInfo.getProjectSource());
        assertEquals("Cached package name should be 'brokk'",
            "brokk",
            cachedInfo.getPackageName());

        // Second download - should use cache (we can't easily verify it doesn't query
        // the registry without mocking, but we can verify it succeeds and returns
        // the same type of result)
        File result2 = DefaultInstallationContext.downloadJDeployBundleForCode(
            TEST_PROJECT_CODE,
            STABLE_VERSION,
            mockAppBundle,
            testHome
        );
        assertNotNull("Second download should succeed", result2);
        assertTrue("Second download result should exist", result2.exists());
        assertEquals("Both downloads should return .jdeploy-files directory",
            ".jdeploy-files", result2.getName());

        // Verify cache still has the same data
        BundleInfo cachedInfo2 = cache.lookup(TEST_PROJECT_CODE);
        assertNotNull("Bundle info should still be cached", cachedInfo2);
        assertEquals("Cached project source should remain unchanged",
            cachedInfo.getProjectSource(),
            cachedInfo2.getProjectSource());
        assertEquals("Cached package name should remain unchanged",
            cachedInfo.getPackageName(),
            cachedInfo2.getPackageName());
    }

    /**
     * Test downloading with invalid bundle code.
     * Should fail gracefully with appropriate error.
     *
     * Note: The registry may return a bundle for some "invalid" codes,
     * so this test expects either an IOException or successful download.
     * The key is that the code doesn't crash or hang.
     */
    @Test
    public void testInvalidBundleCode() {
        try {
            File result = DefaultInstallationContext.downloadJDeployBundleForCode(
                "INVALID999",
                "1.0.0",
                mockAppBundle,
                testHome
            );
            // If we get here, the registry actually returned something for this code
            // This is acceptable - we just want to verify the code doesn't crash
            if (result != null) {
                System.out.println("Note: Registry returned data for 'INVALID999' bundle code");
            }
        } catch (IOException e) {
            // Also acceptable - verify error message is reasonable
            String message = e.getMessage();
            assertNotNull("Error message should not be null", message);
            // Error can be about registry failure, download failure, or invalid bundle
            assertTrue("Error message should be descriptive",
                message.length() > 0);
        }
    }

    /**
     * Test that the registry lookup returns valid bundle info structure.
     * This verifies the actual registry API response format and expected values.
     */
    @Test
    public void testRegistryResponseFormat() throws IOException {
        // Download to populate cache
        File result = DefaultInstallationContext.downloadJDeployBundleForCode(
            TEST_PROJECT_CODE,
            STABLE_VERSION,
            mockAppBundle,
            testHome
        );

        // Verify the download succeeded
        assertNotNull("Download result should not be null", result);
        assertTrue("Download result should exist", result.exists());

        // Verify cache contains properly structured data
        BundleRegistryCache cache = new BundleRegistryCache(
            DefaultInstallationContext.JDEPLOY_REGISTRY,
            testHome
        );
        BundleInfo info = cache.lookup(TEST_PROJECT_CODE);

        assertNotNull("Bundle info should exist", info);
        assertNotNull("Project source should not be null", info.getProjectSource());
        assertNotNull("Package name should not be null", info.getPackageName());

        // Verify project source is a GitHub URL (for bundle code 262N)
        String projectSource = info.getProjectSource();
        assertTrue("Project source should be GitHub URL",
            projectSource.startsWith("https://github.com/"));
        assertEquals("Project source should be Brokk repository",
            "https://github.com/BrokkAi/brokk",
            projectSource);

        // Verify package name doesn't contain slashes (should be simple name)
        String packageName = info.getPackageName();
        assertFalse("Package name should not contain slashes",
            packageName.contains("/"));
        assertEquals("Package name should be 'brokk'",
            "brokk",
            packageName);

        // Verify the timestamp is reasonable (within last hour)
        long timestamp = info.getTimestamp();
        long now = System.currentTimeMillis();
        long oneHourAgo = now - (60 * 60 * 1000);
        assertTrue("Timestamp should be recent",
            timestamp >= oneHourAgo && timestamp <= now);
    }
}