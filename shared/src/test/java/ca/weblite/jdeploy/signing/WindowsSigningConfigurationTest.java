package ca.weblite.jdeploy.signing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for WindowsSigningConfiguration.
 */
class WindowsSigningConfigurationTest {

    @TempDir
    Path tempDir;

    private File testCertFile;

    @BeforeEach
    void setUp() throws IOException {
        // Create a mock certificate file for testing
        testCertFile = tempDir.resolve("test-cert.pfx").toFile();
        Files.write(testCertFile.toPath(), "mock certificate content".getBytes());
    }

    // ==================== Builder Tests ====================

    @Test
    void testBuilderCreatesConfiguration() {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath("/path/to/cert.pfx")
                .certificatePassword("password123")
                .timestampServer("http://timestamp.example.com")
                .publisherName("Test Publisher")
                .algorithm("SHA-512")
                .build();

        assertEquals("/path/to/cert.pfx", config.getCertificatePath());
        assertEquals("password123", config.getCertificatePassword());
        assertEquals("http://timestamp.example.com", config.getTimestampServer());
        assertEquals("Test Publisher", config.getPublisherName());
        assertEquals("SHA-512", config.getAlgorithm());
    }

    @Test
    void testBuilderDefaults() {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath("/path/to/cert.pfx")
                .certificatePassword("password")
                .build();

        assertEquals("http://timestamp.digicert.com", config.getTimestampServer());
        assertEquals("SHA-256", config.getAlgorithm());
        assertNull(config.getPublisherName());
    }

    @Test
    void testEmptyConfiguration() {
        WindowsSigningConfiguration config = new WindowsSigningConfiguration();

        assertNull(config.getCertificatePath());
        assertNull(config.getCertificatePassword());
        assertEquals("http://timestamp.digicert.com", config.getTimestampServer());
        assertEquals("SHA-256", config.getAlgorithm());
    }

    // ==================== Validation Tests ====================

    @Test
    void testValidateSuccess() throws SigningConfigurationException {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath(testCertFile.getAbsolutePath())
                .certificatePassword("password")
                .build();

        // Should not throw
        config.validate();
        assertTrue(config.isValid());
    }

    @Test
    void testValidateFailsWithNullPath() {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePassword("password")
                .build();

        SigningConfigurationException ex = assertThrows(
                SigningConfigurationException.class,
                config::validate
        );
        assertTrue(ex.getMessage().contains("Certificate path is required"));
        assertFalse(config.isValid());
    }

    @Test
    void testValidateFailsWithEmptyPath() {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath("")
                .certificatePassword("password")
                .build();

        SigningConfigurationException ex = assertThrows(
                SigningConfigurationException.class,
                config::validate
        );
        assertTrue(ex.getMessage().contains("Certificate path is required"));
    }

    @Test
    void testValidateFailsWithNonexistentFile() {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath("/nonexistent/path/cert.pfx")
                .certificatePassword("password")
                .build();

        SigningConfigurationException ex = assertThrows(
                SigningConfigurationException.class,
                config::validate
        );
        assertTrue(ex.getMessage().contains("not found"));
    }

    @Test
    void testValidateFailsWithNullPassword() {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath(testCertFile.getAbsolutePath())
                .build();

        SigningConfigurationException ex = assertThrows(
                SigningConfigurationException.class,
                config::validate
        );
        assertTrue(ex.getMessage().contains("password is required"));
    }

    @Test
    void testValidateSucceedsWithEmptyPassword() throws SigningConfigurationException {
        // Empty password (not null) should be allowed for some cert types
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath(testCertFile.getAbsolutePath())
                .certificatePassword("")
                .build();

        // Should not throw
        config.validate();
    }

    // ==================== Setter Tests ====================

    @Test
    void testSetTimestampServerNullUsesDefault() {
        WindowsSigningConfiguration config = new WindowsSigningConfiguration();
        config.setTimestampServer(null);

        assertEquals("http://timestamp.digicert.com", config.getTimestampServer());
    }

    @Test
    void testSetAlgorithmNullUsesDefault() {
        WindowsSigningConfiguration config = new WindowsSigningConfiguration();
        config.setAlgorithm(null);

        assertEquals("SHA-256", config.getAlgorithm());
    }

    // ==================== toString Tests ====================

    @Test
    void testToStringDoesNotIncludePassword() {
        WindowsSigningConfiguration config = WindowsSigningConfiguration.builder()
                .certificatePath("/path/to/cert.pfx")
                .certificatePassword("secretPassword123")
                .publisherName("Test Publisher")
                .build();

        String str = config.toString();
        assertTrue(str.contains("certificatePath"));
        assertTrue(str.contains("publisherName"));
        assertFalse(str.contains("secretPassword123")); // Password should NOT be in toString
        assertFalse(str.contains("certificatePassword")); // Field name shouldn't be there either
    }
}
