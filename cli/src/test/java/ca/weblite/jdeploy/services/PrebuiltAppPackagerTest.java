package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.Platform;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PrebuiltAppPackager to ensure correct tarball naming,
 * checksum generation, and packaging functionality.
 */
public class PrebuiltAppPackagerTest {

    private PrebuiltAppPackager packager;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        packager = new PrebuiltAppPackager();
    }

    // ==================== getTarballName Tests ====================

    @Test
    public void testGetTarballName_WindowsX64() {
        String name = packager.getTarballName("myapp", "1.0.0", Platform.WIN_X64);
        assertEquals("myapp-1.0.0-win-x64-bin.tgz", name);
    }

    @Test
    public void testGetTarballName_WindowsArm64() {
        String name = packager.getTarballName("myapp", "1.0.0", Platform.WIN_ARM64);
        assertEquals("myapp-1.0.0-win-arm64-bin.tgz", name);
    }

    @Test
    public void testGetTarballName_MacX64() {
        String name = packager.getTarballName("myapp", "2.1.0", Platform.MAC_X64);
        assertEquals("myapp-2.1.0-mac-x64-bin.tgz", name);
    }

    @Test
    public void testGetTarballName_MacArm64() {
        String name = packager.getTarballName("myapp", "2.1.0", Platform.MAC_ARM64);
        assertEquals("myapp-2.1.0-mac-arm64-bin.tgz", name);
    }

    @Test
    public void testGetTarballName_LinuxX64() {
        String name = packager.getTarballName("myapp", "3.0.0-beta", Platform.LINUX_X64);
        assertEquals("myapp-3.0.0-beta-linux-x64-bin.tgz", name);
    }

    @Test
    public void testGetTarballName_LinuxArm64() {
        String name = packager.getTarballName("myapp", "3.0.0-beta", Platform.LINUX_ARM64);
        assertEquals("myapp-3.0.0-beta-linux-arm64-bin.tgz", name);
    }

    @Test
    public void testGetTarballName_ScopedPackage() {
        // Scoped npm packages like @org/myapp
        String name = packager.getTarballName("@org/myapp", "1.0.0", Platform.WIN_X64);
        assertEquals("@org/myapp-1.0.0-win-x64-bin.tgz", name);
    }

    @Test
    public void testGetTarballName_SnapshotVersion() {
        String name = packager.getTarballName("myapp", "1.0.0-SNAPSHOT", Platform.WIN_X64);
        assertEquals("myapp-1.0.0-SNAPSHOT-win-x64-bin.tgz", name);
    }

    // ==================== Checksum Generation Tests ====================

    @Test
    public void testGenerateChecksum_ConsistentResults() throws IOException {
        // Create a test file with known content
        File testFile = new File(tempDir.toFile(), "test.txt");
        FileUtils.writeStringToFile(testFile, "Hello, World!", StandardCharsets.UTF_8);

        // Generate checksum twice
        String checksum1 = packager.generateChecksum(testFile);
        String checksum2 = packager.generateChecksum(testFile);

        // Should be consistent
        assertEquals(checksum1, checksum2);
        assertNotNull(checksum1);
        assertFalse(checksum1.isEmpty());
    }

    @Test
    public void testGenerateChecksum_DifferentFilesHaveDifferentChecksums() throws IOException {
        // Create two different files
        File file1 = new File(tempDir.toFile(), "file1.txt");
        File file2 = new File(tempDir.toFile(), "file2.txt");
        FileUtils.writeStringToFile(file1, "Content A", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(file2, "Content B", StandardCharsets.UTF_8);

        // Checksums should be different
        String checksum1 = packager.generateChecksum(file1);
        String checksum2 = packager.generateChecksum(file2);

        assertNotEquals(checksum1, checksum2);
    }

    @Test
    public void testGenerateChecksum_Base64Encoded() throws IOException {
        File testFile = new File(tempDir.toFile(), "test.txt");
        FileUtils.writeStringToFile(testFile, "Test content for checksum", StandardCharsets.UTF_8);

        String checksum = packager.generateChecksum(testFile);

        // Base64 checksum should only contain valid Base64 characters
        assertTrue(checksum.matches("[A-Za-z0-9+/=]+"));
    }

    @Test
    public void testGenerateChecksumHex_HexEncoded() throws IOException {
        File testFile = new File(tempDir.toFile(), "test.txt");
        FileUtils.writeStringToFile(testFile, "Test content for checksum", StandardCharsets.UTF_8);

        String checksumHex = packager.generateChecksumHex(testFile);

        // Hex checksum should only contain hex characters
        assertTrue(checksumHex.matches("[0-9a-f]+"));
        // SHA-256 produces 64 hex characters
        assertEquals(64, checksumHex.length());
    }

    @Test
    public void testGenerateChecksum_EmptyFile() throws IOException {
        File emptyFile = new File(tempDir.toFile(), "empty.txt");
        FileUtils.writeStringToFile(emptyFile, "", StandardCharsets.UTF_8);

        // Should not throw, should return valid checksum
        String checksum = packager.generateChecksum(emptyFile);
        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
    }

    @Test
    public void testGenerateChecksum_LargeFile() throws IOException {
        File largeFile = new File(tempDir.toFile(), "large.bin");

        // Create a larger file (1MB)
        byte[] data = new byte[1024 * 1024];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        FileUtils.writeByteArrayToFile(largeFile, data);

        // Should handle large files without issues
        String checksum = packager.generateChecksum(largeFile);
        assertNotNull(checksum);
        assertFalse(checksum.isEmpty());
    }

    // ==================== Checksum Verification Tests ====================

    @Test
    public void testVerifyChecksum_MatchingChecksum() throws IOException {
        File testFile = new File(tempDir.toFile(), "test.txt");
        FileUtils.writeStringToFile(testFile, "Verify me!", StandardCharsets.UTF_8);

        String checksum = packager.generateChecksum(testFile);

        assertTrue(packager.verifyChecksum(testFile, checksum));
    }

    @Test
    public void testVerifyChecksum_MismatchedChecksum() throws IOException {
        File testFile = new File(tempDir.toFile(), "test.txt");
        FileUtils.writeStringToFile(testFile, "Verify me!", StandardCharsets.UTF_8);

        String wrongChecksum = "WRONGCHECKSUM123456789";

        assertFalse(packager.verifyChecksum(testFile, wrongChecksum));
    }

    @Test
    public void testVerifyChecksum_FileModified() throws IOException {
        File testFile = new File(tempDir.toFile(), "test.txt");
        FileUtils.writeStringToFile(testFile, "Original content", StandardCharsets.UTF_8);

        // Generate checksum of original
        String originalChecksum = packager.generateChecksum(testFile);

        // Modify file
        FileUtils.writeStringToFile(testFile, "Modified content", StandardCharsets.UTF_8);

        // Verification should fail
        assertFalse(packager.verifyChecksum(testFile, originalChecksum));
    }

    // ==================== Naming Pattern Tests ====================

    @Test
    public void testTarballNamingPattern_HasBinSuffix() {
        for (Platform platform : Platform.values()) {
            if (platform == Platform.DEFAULT) continue; // Skip default

            String name = packager.getTarballName("app", "1.0.0", platform);
            assertTrue(name.endsWith("-bin.tgz"),
                "Tarball name should end with -bin.tgz: " + name);
        }
    }

    @Test
    public void testTarballNamingPattern_ContainsPlatform() {
        String name = packager.getTarballName("app", "1.0.0", Platform.WIN_X64);
        assertTrue(name.contains("win-x64"), "Tarball name should contain platform identifier");
    }

    @Test
    public void testTarballNamingPattern_ContainsVersion() {
        String name = packager.getTarballName("app", "2.5.3", Platform.WIN_X64);
        assertTrue(name.contains("2.5.3"), "Tarball name should contain version");
    }

    @Test
    public void testTarballNamingPattern_ContainsAppName() {
        String name = packager.getTarballName("my-cool-app", "1.0.0", Platform.WIN_X64);
        assertTrue(name.startsWith("my-cool-app-"), "Tarball name should start with app name");
    }

    // ==================== Edge Cases ====================

    @Test
    public void testGetTarballName_AllPlatforms() {
        // Ensure all platforms produce valid names
        String[] expectedPlatforms = {"win-x64", "win-arm64", "mac-x64", "mac-arm64", "linux-x64", "linux-arm64"};
        Platform[] platforms = {
            Platform.WIN_X64, Platform.WIN_ARM64,
            Platform.MAC_X64, Platform.MAC_ARM64,
            Platform.LINUX_X64, Platform.LINUX_ARM64
        };

        for (int i = 0; i < platforms.length; i++) {
            String name = packager.getTarballName("app", "1.0.0", platforms[i]);
            assertTrue(name.contains(expectedPlatforms[i]),
                "Expected platform " + expectedPlatforms[i] + " in " + name);
        }
    }
}
