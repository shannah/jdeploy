package ca.weblite.jdeploy.installer.prebuilt;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PrebuiltAppDownloader.
 */
class PrebuiltAppDownloaderTest {

    private PrebuiltAppDownloader downloader;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        downloader = new PrebuiltAppDownloader();
    }

    @Nested
    @DisplayName("checksum verification tests")
    class ChecksumVerificationTests {

        @Test
        @DisplayName("Should verify correct Base64 checksum")
        void shouldVerifyCorrectBase64Checksum() throws Exception {
            // Create a test file
            File testFile = new File(tempDir, "test.txt");
            String content = "Hello, World!";
            Files.write(testFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

            // Calculate expected checksum
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            String expectedChecksum = Base64.getEncoder().encodeToString(hash);

            // Verify
            assertTrue(downloader.verifyChecksum(testFile, expectedChecksum));
        }

        @Test
        @DisplayName("Should reject incorrect checksum")
        void shouldRejectIncorrectChecksum() throws Exception {
            File testFile = new File(tempDir, "test.txt");
            Files.write(testFile.toPath(), "Hello".getBytes(StandardCharsets.UTF_8));

            assertFalse(downloader.verifyChecksum(testFile, "invalid-checksum"));
        }

        @Test
        @DisplayName("Should return true for null checksum (skip verification)")
        void shouldReturnTrueForNullChecksum() throws Exception {
            File testFile = new File(tempDir, "test.txt");
            Files.write(testFile.toPath(), "Test".getBytes(StandardCharsets.UTF_8));

            assertTrue(downloader.verifyChecksum(testFile, null));
        }

        @Test
        @DisplayName("Should return true for empty checksum (skip verification)")
        void shouldReturnTrueForEmptyChecksum() throws Exception {
            File testFile = new File(tempDir, "test.txt");
            Files.write(testFile.toPath(), "Test".getBytes(StandardCharsets.UTF_8));

            assertTrue(downloader.verifyChecksum(testFile, ""));
        }
    }

    @Nested
    @DisplayName("hex checksum verification tests")
    class HexChecksumVerificationTests {

        @Test
        @DisplayName("Should verify correct hex checksum")
        void shouldVerifyCorrectHexChecksum() throws Exception {
            File testFile = new File(tempDir, "test.txt");
            String content = "Hello, World!";
            Files.write(testFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

            // Calculate expected hex checksum
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexBuilder = new StringBuilder();
            for (byte b : hash) {
                hexBuilder.append(String.format("%02x", b));
            }
            String expectedHex = hexBuilder.toString();

            // Verify
            assertTrue(downloader.verifyChecksumHex(testFile, expectedHex));
        }

        @Test
        @DisplayName("Should verify hex checksum case-insensitively")
        void shouldVerifyHexChecksumCaseInsensitively() throws Exception {
            File testFile = new File(tempDir, "test.txt");
            String content = "Test";
            Files.write(testFile.toPath(), content.getBytes(StandardCharsets.UTF_8));

            String checksum = downloader.calculateChecksumHex(testFile);

            // Verify both cases
            assertTrue(downloader.verifyChecksumHex(testFile, checksum.toLowerCase()));
            assertTrue(downloader.verifyChecksumHex(testFile, checksum.toUpperCase()));
        }
    }

    @Nested
    @DisplayName("checksum calculation tests")
    class ChecksumCalculationTests {

        @Test
        @DisplayName("Should calculate same checksum for same content")
        void shouldCalculateSameChecksumForSameContent() throws Exception {
            File file1 = new File(tempDir, "file1.txt");
            File file2 = new File(tempDir, "file2.txt");
            String content = "Identical content";
            Files.write(file1.toPath(), content.getBytes(StandardCharsets.UTF_8));
            Files.write(file2.toPath(), content.getBytes(StandardCharsets.UTF_8));

            String checksum1 = downloader.calculateChecksum(file1);
            String checksum2 = downloader.calculateChecksum(file2);

            assertEquals(checksum1, checksum2);
        }

        @Test
        @DisplayName("Should calculate different checksum for different content")
        void shouldCalculateDifferentChecksumForDifferentContent() throws Exception {
            File file1 = new File(tempDir, "file1.txt");
            File file2 = new File(tempDir, "file2.txt");
            Files.write(file1.toPath(), "Content A".getBytes(StandardCharsets.UTF_8));
            Files.write(file2.toPath(), "Content B".getBytes(StandardCharsets.UTF_8));

            String checksum1 = downloader.calculateChecksum(file1);
            String checksum2 = downloader.calculateChecksum(file2);

            assertNotEquals(checksum1, checksum2);
        }

        @Test
        @DisplayName("Should calculate non-empty checksum")
        void shouldCalculateNonEmptyChecksum() throws Exception {
            File testFile = new File(tempDir, "test.txt");
            Files.write(testFile.toPath(), "Test".getBytes(StandardCharsets.UTF_8));

            String base64Checksum = downloader.calculateChecksum(testFile);
            String hexChecksum = downloader.calculateChecksumHex(testFile);

            assertNotNull(base64Checksum);
            assertFalse(base64Checksum.isEmpty());
            assertNotNull(hexChecksum);
            assertFalse(hexChecksum.isEmpty());
            // SHA-256 produces 64 hex characters
            assertEquals(64, hexChecksum.length());
        }
    }

    @Nested
    @DisplayName("progress listener tests")
    class ProgressListenerTests {

        @Test
        @DisplayName("Should accept progress listener")
        void shouldAcceptProgressListener() {
            // Should not throw
            downloader.setProgressListener((bytesRead, totalBytes) -> {
                // Progress callback
            });
        }

        @Test
        @DisplayName("Should accept null progress listener")
        void shouldAcceptNullProgressListener() {
            // Should not throw
            downloader.setProgressListener(null);
        }
    }

    @Nested
    @DisplayName("extract tests")
    class ExtractTests {

        @Test
        @DisplayName("Should throw when tarball does not exist")
        void shouldThrowWhenTarballNotExists() {
            File nonExistentFile = new File(tempDir, "nonexistent.tgz");
            File destDir = new File(tempDir, "dest");

            assertThrows(IOException.class, () -> {
                downloader.extract(nonExistentFile, destDir);
            });
        }

        @Test
        @DisplayName("Should create destination directory if not exists")
        void shouldCreateDestinationDirectory() throws Exception {
            // Create a minimal test - we can't easily create a real tgz in a unit test
            // but we can verify the destination directory creation logic
            File destDir = new File(tempDir, "dest/nested/path");
            assertFalse(destDir.exists());

            // The extract will fail on the tarball, but dest should be created
            File fakeTarball = new File(tempDir, "fake.tgz");
            Files.write(fakeTarball.toPath(), "not a real tarball".getBytes());

            try {
                downloader.extract(fakeTarball, destDir);
            } catch (IOException e) {
                // Expected - fake tarball isn't valid
            }

            assertTrue(destDir.exists(), "Destination directory should be created");
        }
    }

    @Nested
    @DisplayName("URL accessibility tests")
    class UrlAccessibilityTests {

        @Test
        @DisplayName("Should return false for invalid URL")
        void shouldReturnFalseForInvalidUrl() {
            assertFalse(downloader.isUrlAccessible("https://invalid.url.that.does.not.exist.com/file.tgz"));
        }

        @Test
        @DisplayName("Should return false for malformed URL")
        void shouldReturnFalseForMalformedUrl() {
            assertFalse(downloader.isUrlAccessible("not-a-valid-url"));
        }
    }
}
