package ca.weblite.jdeploy.installer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.*;

/**
 * Unit tests for bundle caching functionality in DefaultInstallationContext.
 * Tests verify cache behavior, HTTP mocking, and error handling.
 */
public class BundleCacheTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File testHome;
    private File mockAppBundle;
    private boolean registryQueryCalled;
    private boolean bundleDownloadCalled;

    @Before
    public void setUp() throws IOException {
        testHome = tempFolder.newFolder("jdeploy-home");
        mockAppBundle = tempFolder.newFile("test-app.app");
        registryQueryCalled = false;
        bundleDownloadCalled = false;
    }

    @After
    public void tearDown() {
        // Cleanup is handled by TemporaryFolder
    }

    /**
     * Test that cache is populated on first install and used on second install.
     */
    @Test
    public void testCachePopulatedOnFirstInstall() throws IOException {
        String bundleCode = "TEST01";

        // Mock registry lookup
        // projectSource = GitHub URL, packageName = simple repo name (no slashes)
        RegistryLookup mockRegistry = (code) -> {
            registryQueryCalled = true;
            assertEquals(bundleCode, code);
            return new BundleInfo("https://github.com/user/test-repo", "test-repo", System.currentTimeMillis());
        };

        // Mock bundle downloader
        BundleDownloader mockDownloader = (code, version, appBundle) -> {
            bundleDownloadCalled = true;
            return createMockZipBundle();
        };

        // First install - should query registry and download bundle
        File result = DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode, "1.0.0", mockAppBundle, testHome, mockRegistry, mockDownloader
        );

        // Verify registry was queried and bundle was downloaded
        assertTrue("Registry should be queried on first install", registryQueryCalled);
        assertTrue("Bundle should be downloaded on first install", bundleDownloadCalled);
        assertNotNull("Result should not be null", result);
        assertTrue("Result should exist", result.exists());
        assertTrue("Result should be .jdeploy-files directory", result.getName().equals(".jdeploy-files"));

        // Verify cache file was created
        File cacheDir = new File(testHome, "registry");
        assertTrue("Cache directory should exist", cacheDir.exists());
        File[] cacheFiles = cacheDir.listFiles((dir, name) -> name.endsWith(".properties"));
        assertNotNull("Cache files should exist", cacheFiles);
        assertEquals("Should have exactly one cache file", 1, cacheFiles.length);

        // Read cache and verify content
        BundleRegistryCache cache = new BundleRegistryCache(DefaultInstallationContext.JDEPLOY_REGISTRY, testHome);
        BundleInfo cachedInfo = cache.lookup(bundleCode);
        assertNotNull("Cached info should exist", cachedInfo);
        assertEquals("https://github.com/user/test-repo", cachedInfo.getProjectSource());
        assertEquals("test-repo", cachedInfo.getPackageName());
        assertFalse("Package name should not contain slash", cachedInfo.getPackageName().contains("/"));
    }

    /**
     * Test that cache is used on second install (registry not queried again).
     */
    @Test
    public void testCacheHitSkipsRegistryLookup() throws IOException {
        String bundleCode = "TEST02";

        // First install - populate cache
        RegistryLookup mockRegistry = (code) -> {
            registryQueryCalled = true;
            return new BundleInfo("https://github.com/user/cached-repo", "cached-repo", System.currentTimeMillis());
        };

        BundleDownloader mockDownloader = (code, version, appBundle) -> {
            bundleDownloadCalled = true;
            return createMockZipBundle();
        };

        DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode, "1.0.0", mockAppBundle, testHome, mockRegistry, mockDownloader
        );

        assertTrue("Registry should be queried on first install", registryQueryCalled);

        // Reset flags
        registryQueryCalled = false;
        bundleDownloadCalled = false;

        // Second install - should use cache, NOT query registry
        RegistryLookup failingRegistry = (code) -> {
            registryQueryCalled = true;
            fail("Registry should NOT be queried when cache hit occurs");
            return null;
        };

        File result = DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode, "1.0.0", mockAppBundle, testHome, failingRegistry, mockDownloader
        );

        // Verify cache was used
        assertFalse("Registry should NOT be queried on cache hit", registryQueryCalled);
        assertTrue("Bundle should still be downloaded", bundleDownloadCalled);
        assertNotNull("Result should not be null", result);
    }

    /**
     * Test handling of registry 404 (bundle not found).
     */
    @Test
    public void testRegistry404Handling() throws IOException {
        String bundleCode = "NOTFOUND";

        // Mock registry returning null (simulates 404)
        RegistryLookup mock404Registry = (code) -> {
            registryQueryCalled = true;
            return null; // Simulates 404 response
        };

        BundleDownloader mockDownloader = (code, version, appBundle) -> {
            bundleDownloadCalled = true;
            return createMockZipBundle();
        };

        File result = DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode, "1.0.0", mockAppBundle, testHome, mock404Registry, mockDownloader
        );

        // Verify registry was queried
        assertTrue("Registry should be queried", registryQueryCalled);

        // Verify bundle download still proceeds (even without registry metadata)
        assertTrue("Bundle download should proceed despite 404", bundleDownloadCalled);
        assertNotNull("Result should not be null", result);

        // Verify no cache entry was created (since registry returned null)
        BundleRegistryCache cache = new BundleRegistryCache(DefaultInstallationContext.JDEPLOY_REGISTRY, testHome);
        BundleInfo cachedInfo = cache.lookup(bundleCode);
        assertNull("No cache entry should exist for 404 response", cachedInfo);
    }

    /**
     * Test handling of registry network error.
     * The registry lookup catches exceptions and returns null, so we simulate that.
     */
    @Test
    public void testRegistryNetworkErrorHandling() throws IOException {
        String bundleCode = "NETERROR";

        // Mock registry returning null (simulating caught exception)
        // In production, queryRegistryForBundleInfo catches exceptions and returns null
        RegistryLookup failingRegistry = (code) -> {
            registryQueryCalled = true;
            // Simulate network error being caught and returning null
            return null;
        };

        BundleDownloader mockDownloader = (code, version, appBundle) -> {
            bundleDownloadCalled = true;
            return createMockZipBundle();
        };

        File result = DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode, "1.0.0", mockAppBundle, testHome, failingRegistry, mockDownloader
        );

        // Verify registry was queried
        assertTrue("Registry lookup should be queried", registryQueryCalled);

        // Verify bundle download still proceeds
        assertTrue("Bundle download should proceed despite registry error", bundleDownloadCalled);
        assertNotNull("Result should not be null", result);
    }

    /**
     * Test handling of bundle download failure.
     */
    @Test
    public void testBundleDownloadFailure() throws IOException {
        String bundleCode = "DLNERROR";

        // Mock successful registry lookup
        RegistryLookup mockRegistry = (code) -> {
            registryQueryCalled = true;
            return new BundleInfo("https://github.com/user/test-app", "test-app", System.currentTimeMillis());
        };

        // Mock bundle download failure
        BundleDownloader failingDownloader = (code, version, appBundle) -> {
            bundleDownloadCalled = true;
            throw new IOException("Bundle download failed");
        };

        try {
            DefaultInstallationContext.downloadJDeployBundleForCode(
                bundleCode, "1.0.0", mockAppBundle, testHome, mockRegistry, failingDownloader
            );
            fail("Should throw IOException when bundle download fails");
        } catch (IOException e) {
            // The error message should mention registry URLs and should have the original cause
            assertTrue("Error message should mention failed registries",
                e.getMessage().contains("Failed to download bundle from all registries"));
            assertTrue("Error should have cause set",
                e.getCause() != null);
            assertEquals("Cause should be the original download exception",
                "Bundle download failed",
                e.getCause().getMessage());
        }

        // Verify registry was queried and cached even though download failed
        assertTrue("Registry should be queried", registryQueryCalled);
        assertTrue("Bundle download should be attempted", bundleDownloadCalled);

        // Verify cache entry exists (registry data was saved before download failure)
        BundleRegistryCache cache = new BundleRegistryCache(DefaultInstallationContext.JDEPLOY_REGISTRY, testHome);
        BundleInfo cachedInfo = cache.lookup(bundleCode);
        assertNotNull("Cache should contain registry data despite download failure", cachedInfo);
        assertEquals("https://github.com/user/test-app", cachedInfo.getProjectSource());
        assertEquals("test-app", cachedInfo.getPackageName());
    }

    /**
     * Test that different bundle codes get separate cache entries.
     */
    @Test
    public void testMultipleBundleCodesInCache() throws IOException {
        String bundleCode1 = "MULTI01";
        String bundleCode2 = "MULTI02";

        RegistryLookup mockRegistry = (code) -> {
            if (code.equals(bundleCode1)) {
                return new BundleInfo("https://github.com/user/app1", "app1", System.currentTimeMillis());
            } else if (code.equals(bundleCode2)) {
                return new BundleInfo("https://github.com/user/app2", "app2", System.currentTimeMillis());
            }
            return null;
        };

        BundleDownloader mockDownloader = (code, version, appBundle) -> createMockZipBundle();

        // Install first bundle
        DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode1, "1.0.0", mockAppBundle, testHome, mockRegistry, mockDownloader
        );

        // Install second bundle
        DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode2, "1.0.0", mockAppBundle, testHome, mockRegistry, mockDownloader
        );

        // Verify both are cached
        BundleRegistryCache cache = new BundleRegistryCache(DefaultInstallationContext.JDEPLOY_REGISTRY, testHome);

        BundleInfo info1 = cache.lookup(bundleCode1);
        assertNotNull("First bundle should be cached", info1);
        assertEquals("app1", info1.getPackageName());
        assertFalse("Package name should not contain slash", info1.getPackageName().contains("/"));

        BundleInfo info2 = cache.lookup(bundleCode2);
        assertNotNull("Second bundle should be cached", info2);
        assertEquals("app2", info2.getPackageName());
        assertFalse("Package name should not contain slash", info2.getPackageName().contains("/"));
    }

    /**
     * Test NPM package (non-GitHub source) handling.
     */
    @Test
    public void testNpmPackageHandling() throws IOException {
        String bundleCode = "NPM001";

        // Mock registry lookup for NPM package
        // projectSource = NPM package name, packageName = simple name
        RegistryLookup mockRegistry = (code) -> {
            registryQueryCalled = true;
            return new BundleInfo("my-npm-package", "my-npm-package", System.currentTimeMillis());
        };

        BundleDownloader mockDownloader = (code, version, appBundle) -> createMockZipBundle();

        File result = DefaultInstallationContext.downloadJDeployBundleForCode(
            bundleCode, "1.0.0", mockAppBundle, testHome, mockRegistry, mockDownloader
        );

        assertNotNull("Result should not be null", result);

        // Verify cache
        BundleRegistryCache cache = new BundleRegistryCache(DefaultInstallationContext.JDEPLOY_REGISTRY, testHome);
        BundleInfo cachedInfo = cache.lookup(bundleCode);
        assertNotNull("Cached info should exist", cachedInfo);
        assertEquals("my-npm-package", cachedInfo.getProjectSource());
        assertEquals("my-npm-package", cachedInfo.getPackageName());
        assertFalse("Package name should not contain slash", cachedInfo.getPackageName().contains("/"));
    }

    /**
     * Helper method to create a mock ZIP bundle containing .jdeploy-files directory.
     */
    private InputStream createMockZipBundle() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            // Create .jdeploy-files directory entry
            ZipEntry dirEntry = new ZipEntry(".jdeploy-files/");
            zos.putNextEntry(dirEntry);
            zos.closeEntry();

            // Create a dummy file inside .jdeploy-files
            ZipEntry fileEntry = new ZipEntry(".jdeploy-files/app.xml");
            zos.putNextEntry(fileEntry);
            zos.write("<?xml version=\"1.0\"?><app></app>".getBytes());
            zos.closeEntry();
        }

        return new ByteArrayInputStream(baos.toByteArray());
    }
}