package ca.weblite.jdeploy.installer.util;

import ca.weblite.tools.io.MD5;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CliCommandBinDirResolver utility class.
 *
 * Tests cover:
 * - NPM packages (source=null)
 * - GitHub packages (MD5.packageName format)
 * - Null/empty packageName validation
 * - Testable overload with injected dependencies
 */
public class CliCommandBinDirResolverTest {

    @Test
    public void testComputeFullyQualifiedPackageNameForNpmPackage() {
        // For NPM packages, source is null and should return packageName as-is
        String result = CliCommandBinDirResolver.computeFullyQualifiedPackageName("my-app", null);
        assertEquals("my-app", result);
    }

    @Test
    public void testComputeFullyQualifiedPackageNameForNpmPackageWithScope() {
        // NPM scoped packages should also work as-is
        String result = CliCommandBinDirResolver.computeFullyQualifiedPackageName("@myorg/my-app", null);
        assertEquals("@myorg/my-app", result);
    }

    @Test
    public void testComputeFullyQualifiedPackageNameForGithubPackage() {
        // For GitHub packages, should return MD5(source).packageName format
        String source = "https://github.com/example/my-repo";
        String result = CliCommandBinDirResolver.computeFullyQualifiedPackageName("my-app", source);

        // Compute expected MD5
        String expectedMd5 = MD5.getMd5(source);
        String expected = expectedMd5 + ".my-app";

        assertEquals(expected, result);
    }

    @Test
    public void testComputeFullyQualifiedPackageNameGithubCollisionAvoidance() {
        // Two different GitHub sources with same package name should produce different FQNs
        String packageName = "my-app";
        String source1 = "https://github.com/user1/my-repo";
        String source2 = "https://github.com/user2/my-repo";

        String result1 = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source1);
        String result2 = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source2);

        assertNotEquals(result1, result2, "Different GitHub sources should produce different FQNs");
        assertTrue(result1.endsWith(".my-app"), "Result should end with .packageName");
        assertTrue(result2.endsWith(".my-app"), "Result should end with .packageName");
    }

    @Test
    public void testComputeFullyQualifiedPackageNameNullPackageName() {
        // Should throw IllegalArgumentException for null packageName
        assertThrows(IllegalArgumentException.class, () ->
            CliCommandBinDirResolver.computeFullyQualifiedPackageName(null, null)
        );
    }

    @Test
    public void testComputeFullyQualifiedPackageNameEmptyPackageName() {
        // Should throw IllegalArgumentException for empty packageName
        assertThrows(IllegalArgumentException.class, () ->
            CliCommandBinDirResolver.computeFullyQualifiedPackageName("", null)
        );
    }

    @Test
    public void testComputeFullyQualifiedPackageNameWhitespaceOnlyPackageName() {
        // Should throw IllegalArgumentException for whitespace-only packageName
        assertThrows(IllegalArgumentException.class, () ->
            CliCommandBinDirResolver.computeFullyQualifiedPackageName("   ", null)
        );
    }

    @Test
    public void testGetCliCommandBinDirConvenience() {
        // Convenience method should use system user.home and return .jdeploy/bin
        File result = CliCommandBinDirResolver.getCliCommandBinDir("my-app", null);

        assertNotNull(result);
        assertTrue(result.getPath().endsWith(".jdeploy" + File.separator + "bin"),
            "Should end with .jdeploy/bin");
        assertTrue(result.getPath().contains(System.getProperty("user.home")),
            "Should be under user home directory");
    }

    @Test
    public void testGetCliCommandBinDirWithInjectedBase() {
        // Testable overload with injected userHome directory
        File testHome = new File("/test/home");
        File result = CliCommandBinDirResolver.getCliCommandBinDir("my-app", null, testHome);

        assertNotNull(result);
        assertEquals(new File(testHome, ".jdeploy/bin"), result);
    }

    @Test
    public void testGetCliCommandBinDirWithInjectedBaseDifferentPackages() {
        // Multiple packages should have the same bin directory
        File testHome = new File("/test/home");
        File result1 = CliCommandBinDirResolver.getCliCommandBinDir("app1", null, testHome);
        File result2 = CliCommandBinDirResolver.getCliCommandBinDir("app2", null, testHome);

        assertEquals(result1, result2, "Different packages should share the same bin directory");
    }

    @Test
    public void testGetCliCommandBinDirNullPackageNameConvenience() {
        // Convenience method should throw for null packageName
        assertThrows(IllegalArgumentException.class, () ->
            CliCommandBinDirResolver.getCliCommandBinDir(null, null)
        );
    }

    @Test
    public void testGetCliCommandBinDirEmptyPackageNameConvenience() {
        // Convenience method should throw for empty packageName
        assertThrows(IllegalArgumentException.class, () ->
            CliCommandBinDirResolver.getCliCommandBinDir("", null)
        );
    }

    @Test
    public void testGetCliCommandBinDirNullPackageNameTestable() {
        // Testable method should throw for null packageName
        File testHome = new File("/test/home");
        assertThrows(IllegalArgumentException.class, () ->
            CliCommandBinDirResolver.getCliCommandBinDir(null, null, testHome)
        );
    }

    @Test
    public void testGetCliCommandBinDirEmptyPackageNameTestable() {
        // Testable method should throw for empty packageName
        File testHome = new File("/test/home");
        assertThrows(IllegalArgumentException.class, () ->
            CliCommandBinDirResolver.getCliCommandBinDir("", null, testHome)
        );
    }

    @Test
    public void testGetCliCommandBinDirWithGithubSource() {
        // Should work with GitHub source (though source doesn't affect bin dir path)
        File testHome = new File("/test/home");
        String source = "https://github.com/example/repo";
        File result = CliCommandBinDirResolver.getCliCommandBinDir("my-app", source, testHome);

        assertEquals(new File(testHome, ".jdeploy/bin"), result);
    }

    @Test
    public void testConsistencyBetweenNpmAndGithub() {
        // Both NPM and GitHub packages should resolve to the same bin directory
        File testHome = new File("/test/home");
        File npmResult = CliCommandBinDirResolver.getCliCommandBinDir("my-app", null, testHome);
        File githubResult = CliCommandBinDirResolver.getCliCommandBinDir("my-app",
            "https://github.com/example/repo", testHome);

        assertEquals(npmResult, githubResult,
            "NPM and GitHub packages should share the same bin directory");
    }

    @Test
    public void testMd5HashingConsistency() {
        // Same source should always produce same hash
        String source = "https://github.com/example/my-repo";
        String fqn1 = CliCommandBinDirResolver.computeFullyQualifiedPackageName("my-app", source);
        String fqn2 = CliCommandBinDirResolver.computeFullyQualifiedPackageName("my-app", source);

        assertEquals(fqn1, fqn2, "Same source should produce consistent hash");
    }

    @Test
    public void testGithubPackageFormatStructure() {
        // Verify the format is exactly MD5(source).packageName with a dot separator
        String source = "https://github.com/example/repo";
        String packageName = "my-app";
        String result = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);

        String[] parts = result.split("\\.");
        assertTrue(parts.length >= 2, "Should have MD5 hash and package name separated by dot");

        String hash = MD5.getMd5(source);
        assertTrue(result.startsWith(hash), "Should start with MD5 hash");
        assertTrue(result.endsWith(packageName), "Should end with package name");
    }
}
