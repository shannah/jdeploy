package ca.weblite.jdeploy.archive;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static ca.weblite.jdeploy.archive.JDeployArchiveGenerator.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the round-trip: generate an archive, read it back, and validate it.
 */
public class JDeployArchiveRoundTripTest {

    @TempDir
    File tempDir;

    private File packageJsonFile;
    private File universalTarball;

    @BeforeEach
    void setUp() throws IOException {
        // Create a minimal package.json
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("jar", "test-app.jar");
        packageJson.put("jdeploy", jdeploy);

        packageJsonFile = new File(tempDir, "package.json");
        Files.writeString(packageJsonFile.toPath(), packageJson.toString(2));

        // Create a fake universal tarball
        universalTarball = new File(tempDir, "test-app-1.0.0.tgz");
        Files.writeString(universalTarball.toPath(), "fake tarball content");
    }

    @Test
    void testGenerateAndReadArchive() throws IOException {
        // Generate archive manually (without PublishingContext dependency)
        File archiveFile = new File(tempDir, "test-app-1.0.0.jdeploy");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, "test-app");
        attrs.putValue(ATTR_PACKAGE_VERSION, "1.0.0");
        attrs.putValue(ATTR_IS_PRERELEASE, "false");
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_INSTALLERS, "false");
        attrs.putValue(ATTR_CREATED_AT, "2026-01-01T00:00:00Z");

        try (var fos = new java.io.FileOutputStream(archiveFile);
             var jos = new JarOutputStream(fos, manifest)) {

            // Add package.json
            JarEntry entry = new JarEntry("package.json");
            jos.putNextEntry(entry);
            Files.copy(packageJsonFile.toPath(), jos);
            jos.closeEntry();

            // Add universal tarball
            entry = new JarEntry("bundles/" + universalTarball.getName());
            jos.putNextEntry(entry);
            Files.copy(universalTarball.toPath(), jos);
            jos.closeEntry();
        }

        // Read it back
        try (JDeployArchiveReader reader = new JDeployArchiveReader(archiveFile)) {
            assertEquals("test-app", reader.getPackageName());
            assertEquals("1.0.0", reader.getPackageVersion());
            assertFalse(reader.isPrerelease());
            assertFalse(reader.hasPlatformBundles());
            assertFalse(reader.hasPrebuiltBundles());
            assertFalse(reader.hasInstallers());
            assertEquals("2026-01-01T00:00:00Z", reader.getCreatedAt());

            // Check package.json
            JSONObject readPackageJson = reader.getPackageJson();
            assertEquals("test-app", readPackageJson.getString("name"));
            assertEquals("1.0.0", readPackageJson.getString("version"));
            assertTrue(readPackageJson.has("jdeploy"));

            // Check bundles
            List<String> bundleNames = reader.getBundleNames();
            assertEquals(1, bundleNames.size());
            assertEquals("test-app-1.0.0.tgz", bundleNames.get(0));

            // Check universal tarball stream
            try (InputStream is = reader.getUniversalTarball()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                assertEquals("fake tarball content", content);
            }

            // Check all entries
            List<JDeployArchiveReader.EntryInfo> entries = reader.listAllEntries();
            assertEquals(2, entries.size());
        }
    }

    @Test
    void testArchiveWithPlatformBundles() throws IOException {
        File archiveFile = new File(tempDir, "test-app-1.0.0.jdeploy");

        // Create platform tarballs
        File macTarball = new File(tempDir, "test-app-1.0.0-mac-arm64.tgz");
        Files.writeString(macTarball.toPath(), "mac bundle");
        File winTarball = new File(tempDir, "test-app-1.0.0-win-x64.tgz");
        Files.writeString(winTarball.toPath(), "win bundle");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, "test-app");
        attrs.putValue(ATTR_PACKAGE_VERSION, "1.0.0");
        attrs.putValue(ATTR_IS_PRERELEASE, "false");
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, "true");
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_INSTALLERS, "false");
        attrs.putValue(ATTR_PLATFORMS, "mac-arm64,win-x64");
        attrs.putValue(ATTR_CREATED_AT, "2026-01-01T00:00:00Z");

        try (var fos = new java.io.FileOutputStream(archiveFile);
             var jos = new JarOutputStream(fos, manifest)) {
            addEntry(jos, "package.json", packageJsonFile);
            addEntry(jos, "bundles/" + universalTarball.getName(), universalTarball);
            addEntry(jos, "bundles/" + macTarball.getName(), macTarball);
            addEntry(jos, "bundles/" + winTarball.getName(), winTarball);
        }

        try (JDeployArchiveReader reader = new JDeployArchiveReader(archiveFile)) {
            assertTrue(reader.hasPlatformBundles());
            List<String> platforms = reader.getPlatforms();
            assertEquals(2, platforms.size());
            assertTrue(platforms.contains("mac-arm64"));
            assertTrue(platforms.contains("win-x64"));

            List<String> bundleNames = reader.getBundleNames();
            assertEquals(3, bundleNames.size());

            try (InputStream is = reader.getPlatformTarball("mac-arm64")) {
                assertEquals("mac bundle", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void testValidatorOnValidArchive() throws IOException {
        File archiveFile = createValidArchive();

        JDeployArchiveValidator validator = new JDeployArchiveValidator();
        JDeployArchiveValidator.ValidationResult result = validator.validate(archiveFile);

        assertTrue(result.isValid(), "Expected valid archive but got errors: " + result.getErrors());
        assertTrue(result.getErrors().isEmpty());
    }

    @Test
    void testValidatorOnMissingFile() {
        File nonExistent = new File(tempDir, "nonexistent.jdeploy");

        JDeployArchiveValidator validator = new JDeployArchiveValidator();
        JDeployArchiveValidator.ValidationResult result = validator.validate(nonExistent);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("does not exist")));
    }

    @Test
    void testValidatorDetectsNameMismatch() throws IOException {
        // Create archive where manifest name differs from package.json name
        File archiveFile = new File(tempDir, "test-app-1.0.0.jdeploy");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, "wrong-name");
        attrs.putValue(ATTR_PACKAGE_VERSION, "1.0.0");
        attrs.putValue(ATTR_IS_PRERELEASE, "false");
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_INSTALLERS, "false");
        attrs.putValue(ATTR_CREATED_AT, "2026-01-01T00:00:00Z");

        try (var fos = new java.io.FileOutputStream(archiveFile);
             var jos = new JarOutputStream(fos, manifest)) {
            addEntry(jos, "package.json", packageJsonFile);
            // Use the manifest's (wrong) name for the bundle path
            File tarball = new File(tempDir, "wrong-name-1.0.0.tgz");
            Files.writeString(tarball.toPath(), "content");
            addEntry(jos, "bundles/wrong-name-1.0.0.tgz", tarball);
        }

        JDeployArchiveValidator validator = new JDeployArchiveValidator();
        JDeployArchiveValidator.ValidationResult result = validator.validate(archiveFile);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("name mismatch")),
                "Expected name mismatch error, got: " + result.getErrors());
    }

    @Test
    void testValidatorDetectsMissingUniversalTarball() throws IOException {
        File archiveFile = new File(tempDir, "test-app-1.0.0.jdeploy");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, "test-app");
        attrs.putValue(ATTR_PACKAGE_VERSION, "1.0.0");
        attrs.putValue(ATTR_IS_PRERELEASE, "false");
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_INSTALLERS, "false");
        attrs.putValue(ATTR_CREATED_AT, "2026-01-01T00:00:00Z");

        try (var fos = new java.io.FileOutputStream(archiveFile);
             var jos = new JarOutputStream(fos, manifest)) {
            addEntry(jos, "package.json", packageJsonFile);
            // No bundle added
        }

        JDeployArchiveValidator validator = new JDeployArchiveValidator();
        JDeployArchiveValidator.ValidationResult result = validator.validate(archiveFile);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("no bundles")),
                "Expected no bundles error, got: " + result.getErrors());
    }

    @Test
    void testReaderAssetsAndPrebuilt() throws IOException {
        File archiveFile = new File(tempDir, "test-app-1.0.0.jdeploy");

        // Create asset and prebuilt files
        File icon = new File(tempDir, "icon.png");
        Files.writeString(icon.toPath(), "fake icon data");
        File prebuilt = new File(tempDir, "test-app-mac-arm64-1.0.0.tar.gz");
        Files.writeString(prebuilt.toPath(), "fake prebuilt data");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, "test-app");
        attrs.putValue(ATTR_PACKAGE_VERSION, "1.0.0");
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, "true");
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_INSTALLERS, "false");
        attrs.putValue(ATTR_CREATED_AT, "2026-01-01T00:00:00Z");

        try (var fos = new java.io.FileOutputStream(archiveFile);
             var jos = new JarOutputStream(fos, manifest)) {
            addEntry(jos, "package.json", packageJsonFile);
            addEntry(jos, "bundles/" + universalTarball.getName(), universalTarball);
            addEntry(jos, "assets/icon.png", icon);
            addEntry(jos, "prebuilt/" + prebuilt.getName(), prebuilt);
        }

        try (JDeployArchiveReader reader = new JDeployArchiveReader(archiveFile)) {
            // Test asset
            try (InputStream is = reader.getAsset("icon.png")) {
                assertNotNull(is);
                assertEquals("fake icon data", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }

            // Test missing asset returns null
            assertNull(reader.getAsset("nonexistent.png"));

            // Test prebuilt bundles
            assertTrue(reader.hasPrebuiltBundles());
            List<String> prebuiltNames = reader.getPrebuiltBundleNames();
            assertEquals(1, prebuiltNames.size());
            assertEquals("test-app-mac-arm64-1.0.0.tar.gz", prebuiltNames.get(0));

            try (InputStream is = reader.getPrebuiltBundle("test-app-mac-arm64-1.0.0.tar.gz")) {
                assertNotNull(is);
                assertEquals("fake prebuilt data", new String(is.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    @Test
    void testPrereleaseDetection() throws IOException {
        File archiveFile = new File(tempDir, "test-app-1.0.0-beta.1.jdeploy");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, "test-app");
        attrs.putValue(ATTR_PACKAGE_VERSION, "1.0.0-beta.1");
        attrs.putValue(ATTR_IS_PRERELEASE, "true");
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_INSTALLERS, "false");
        attrs.putValue(ATTR_CREATED_AT, "2026-01-01T00:00:00Z");

        try (var fos = new java.io.FileOutputStream(archiveFile);
             var jos = new JarOutputStream(fos, manifest)) {
            // Create matching package.json
            JSONObject pj = new JSONObject();
            pj.put("name", "test-app");
            pj.put("version", "1.0.0-beta.1");
            pj.put("jdeploy", new JSONObject().put("jar", "test.jar"));
            File pjFile = new File(tempDir, "prerelease-package.json");
            Files.writeString(pjFile.toPath(), pj.toString());
            addEntry(jos, "package.json", pjFile);

            File tarball = new File(tempDir, "test-app-1.0.0-beta.1.tgz");
            Files.writeString(tarball.toPath(), "content");
            addEntry(jos, "bundles/test-app-1.0.0-beta.1.tgz", tarball);
        }

        try (JDeployArchiveReader reader = new JDeployArchiveReader(archiveFile)) {
            assertTrue(reader.isPrerelease());
            assertEquals("1.0.0-beta.1", reader.getPackageVersion());
        }
    }

    // --- Helpers ---

    private File createValidArchive() throws IOException {
        File archiveFile = new File(tempDir, "test-app-1.0.0.jdeploy");

        Manifest manifest = new Manifest();
        Attributes attrs = manifest.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.putValue(ATTR_ARCHIVE_VERSION, ARCHIVE_VERSION);
        attrs.putValue(ATTR_PACKAGE_NAME, "test-app");
        attrs.putValue(ATTR_PACKAGE_VERSION, "1.0.0");
        attrs.putValue(ATTR_IS_PRERELEASE, "false");
        attrs.putValue(ATTR_HAS_PLATFORM_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_PREBUILT_BUNDLES, "false");
        attrs.putValue(ATTR_HAS_INSTALLERS, "false");
        attrs.putValue(ATTR_CREATED_AT, "2026-01-01T00:00:00Z");

        try (var fos = new java.io.FileOutputStream(archiveFile);
             var jos = new JarOutputStream(fos, manifest)) {
            addEntry(jos, "package.json", packageJsonFile);
            addEntry(jos, "bundles/" + universalTarball.getName(), universalTarball);
        }

        return archiveFile;
    }

    private void addEntry(JarOutputStream jos, String path, File file) throws IOException {
        JarEntry entry = new JarEntry(path);
        jos.putNextEntry(entry);
        Files.copy(file.toPath(), jos);
        jos.closeEntry();
    }
}
