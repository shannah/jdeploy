package ca.weblite.jdeploy.installer.preferences;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PreferencesService.
 */
class PreferencesServiceTest {

    private PreferencesService service;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        service = new PreferencesService(new PrintStream(outContent), new PrintStream(errContent));

        // Override user.home to use temp directory
        System.setProperty("user.home", tempDir.getAbsolutePath());
    }

    @AfterEach
    void tearDown() {
        // Restore user.home - not critical for tests as @TempDir handles cleanup
    }

    @Nested
    @DisplayName("FQN calculation tests")
    class FqnCalculationTests {

        @Test
        @DisplayName("Should return package name when source is null")
        void shouldReturnPackageNameWhenSourceNull() {
            String fqn = service.calculateFqn("my-app", null);
            assertEquals("my-app", fqn);
        }

        @Test
        @DisplayName("Should return package name when source is empty")
        void shouldReturnPackageNameWhenSourceEmpty() {
            String fqn = service.calculateFqn("my-app", "");
            assertEquals("my-app", fqn);
        }

        @Test
        @DisplayName("Should prefix with MD5 hash when source provided")
        void shouldPrefixWithMd5HashWhenSourceProvided() {
            String fqn = service.calculateFqn("my-app", "https://github.com/user/repo");
            assertNotEquals("my-app", fqn);
            assertTrue(fqn.endsWith(".my-app"));
            // MD5 hash is 32 chars
            assertEquals(32 + 1 + "my-app".length(), fqn.length());
        }

        @Test
        @DisplayName("Should produce same FQN for same source")
        void shouldProduceSameFqnForSameSource() {
            String fqn1 = service.calculateFqn("my-app", "https://github.com/user/repo");
            String fqn2 = service.calculateFqn("my-app", "https://github.com/user/repo");
            assertEquals(fqn1, fqn2);
        }

        @Test
        @DisplayName("Should produce different FQN for different sources")
        void shouldProduceDifferentFqnForDifferentSources() {
            String fqn1 = service.calculateFqn("my-app", "https://github.com/user/repo1");
            String fqn2 = service.calculateFqn("my-app", "https://github.com/user/repo2");
            assertNotEquals(fqn1, fqn2);
        }
    }

    @Nested
    @DisplayName("Preferences path tests")
    class PreferencesPathTests {

        @Test
        @DisplayName("Should return correct preferences directory path")
        void shouldReturnCorrectPreferencesDirPath() {
            File prefsDir = service.getPreferencesDir("my-app");
            String expected = tempDir.getAbsolutePath() + File.separator +
                    ".jdeploy" + File.separator + "preferences" + File.separator + "my-app";
            assertEquals(expected, prefsDir.getAbsolutePath());
        }

        @Test
        @DisplayName("Should return correct preferences file path")
        void shouldReturnCorrectPreferencesFilePath() {
            File prefsFile = service.getPreferencesFile("my-app");
            assertTrue(prefsFile.getAbsolutePath().endsWith("preferences.xml"));
            assertTrue(prefsFile.getAbsolutePath().contains("my-app"));
        }
    }

    @Nested
    @DisplayName("Write and read preferences tests")
    class WriteReadPreferencesTests {

        @Test
        @DisplayName("Should write and read preferences successfully")
        void shouldWriteAndReadPreferences() throws IOException {
            AppPreferences prefs = AppPreferences.builder()
                    .version("1.2.3")
                    .prereleaseChannel("prerelease")
                    .autoUpdate("patch")
                    .jvmArgs("-Xmx512m")
                    .prebuiltInstallation(true)
                    .build();

            service.writePreferences("test-app", prefs);
            assertTrue(service.preferencesExist("test-app"));

            AppPreferences readPrefs = service.readPreferences("test-app");
            assertNotNull(readPrefs);
            assertEquals("1.2.3", readPrefs.getVersion());
            assertEquals("prerelease", readPrefs.getPrereleaseChannel());
            assertEquals("patch", readPrefs.getAutoUpdate());
            assertEquals("-Xmx512m", readPrefs.getJvmArgs());
            assertTrue(readPrefs.isPrebuiltInstallation());
        }

        @Test
        @DisplayName("Should return null when preferences don't exist")
        void shouldReturnNullWhenPreferencesNotExist() {
            AppPreferences prefs = service.readPreferences("nonexistent-app");
            assertNull(prefs);
        }

        @Test
        @DisplayName("Should create directories when writing preferences")
        void shouldCreateDirectoriesWhenWriting() throws IOException {
            AppPreferences prefs = AppPreferences.builder()
                    .version("1.0.0")
                    .build();

            assertFalse(service.preferencesExist("new-app"));
            service.writePreferences("new-app", prefs);
            assertTrue(service.preferencesExist("new-app"));
        }

        @Test
        @DisplayName("Should handle optional fields gracefully")
        void shouldHandleOptionalFieldsGracefully() throws IOException {
            AppPreferences prefs = AppPreferences.builder()
                    .version("1.0.0")
                    // Not setting jvmArgs
                    .build();

            service.writePreferences("test-app", prefs);

            AppPreferences readPrefs = service.readPreferences("test-app");
            assertNotNull(readPrefs);
            assertNull(readPrefs.getJvmArgs());
        }
    }

    @Nested
    @DisplayName("Delete preferences tests")
    class DeletePreferencesTests {

        @Test
        @DisplayName("Should delete existing preferences")
        void shouldDeleteExistingPreferences() throws IOException {
            AppPreferences prefs = AppPreferences.builder()
                    .version("1.0.0")
                    .build();
            service.writePreferences("delete-test", prefs);
            assertTrue(service.preferencesExist("delete-test"));

            boolean deleted = service.deletePreferences("delete-test");
            assertTrue(deleted);
            assertFalse(service.preferencesExist("delete-test"));
        }

        @Test
        @DisplayName("Should return true when deleting non-existent preferences")
        void shouldReturnTrueWhenDeletingNonExistent() {
            boolean deleted = service.deletePreferences("nonexistent");
            assertTrue(deleted);
        }
    }

    @Nested
    @DisplayName("Create initial preferences tests")
    class CreateInitialPreferencesTests {

        @Test
        @DisplayName("Should create initial preferences with all settings")
        void shouldCreateInitialPreferencesWithAllSettings() throws IOException {
            service.createInitialPreferences(
                    "init-test",
                    "2.0.0",
                    true,  // prerelease
                    "all", // autoUpdate
                    true   // prebuilt
            );

            AppPreferences prefs = service.readPreferences("init-test");
            assertNotNull(prefs);
            assertEquals("2.0.0", prefs.getVersion());
            assertEquals("prerelease", prefs.getPrereleaseChannel());
            assertEquals("all", prefs.getAutoUpdate());
            assertTrue(prefs.isPrebuiltInstallation());
        }

        @Test
        @DisplayName("Should use default autoUpdate when null")
        void shouldUseDefaultAutoUpdateWhenNull() throws IOException {
            service.createInitialPreferences(
                    "default-test",
                    "1.0.0",
                    false,
                    null,  // null autoUpdate
                    false
            );

            AppPreferences prefs = service.readPreferences("default-test");
            assertNotNull(prefs);
            assertEquals("minor", prefs.getAutoUpdate());
        }
    }

    @Nested
    @DisplayName("Corrupted preferences handling tests")
    class CorruptedPreferencesTests {

        @Test
        @DisplayName("Should handle corrupted XML gracefully")
        void shouldHandleCorruptedXmlGracefully() throws IOException {
            // Write invalid XML
            File prefsDir = service.getPreferencesDir("corrupted-test");
            prefsDir.mkdirs();
            File prefsFile = service.getPreferencesFile("corrupted-test");
            Files.write(prefsFile.toPath(), "not valid xml <<>>".getBytes());

            // Should return null and not throw
            AppPreferences prefs = service.readPreferences("corrupted-test");
            assertNull(prefs);

            // Check that error was logged
            String errOutput = errContent.toString();
            assertTrue(errOutput.contains("Warning") || errOutput.contains("corrupted"));
        }
    }
}
