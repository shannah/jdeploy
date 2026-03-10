package ca.weblite.jdeploy.installer.prebuilt;

import ca.weblite.jdeploy.installer.npm.NPMPackageVersion;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PrebuiltBundleDownloaderTest {

    @TempDir
    Path tempDir;

    @Test
    void testVerifySha256_correctHash() throws Exception {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();

        File testFile = tempDir.resolve("test.bin").toFile();
        byte[] content = "Hello, world!".getBytes("UTF-8");
        Files.write(testFile.toPath(), content);

        // Compute expected SHA-256
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String expectedHash = bytesToHex(digest.digest(content));

        assertTrue(downloader.verifySha256(testFile, expectedHash));
    }

    @Test
    void testVerifySha256_wrongHash() throws Exception {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();

        File testFile = tempDir.resolve("test.bin").toFile();
        Files.write(testFile.toPath(), "Hello, world!".getBytes("UTF-8"));

        assertFalse(downloader.verifySha256(testFile, "0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void testVerifySha256_caseInsensitive() throws Exception {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();

        File testFile = tempDir.resolve("test.bin").toFile();
        byte[] content = "test".getBytes("UTF-8");
        Files.write(testFile.toPath(), content);

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        String hash = bytesToHex(digest.digest(content));

        // Verify both upper and lower case work
        assertTrue(downloader.verifySha256(testFile, hash.toLowerCase()));
        assertTrue(downloader.verifySha256(testFile, hash.toUpperCase()));
    }

    @Test
    void testExtractBundleJar_singleFile() throws Exception {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();

        // Create a JAR with a single file entry
        File jarFile = tempDir.resolve("bundle.jar").toFile();
        byte[] exeContent = new byte[]{0x4D, 0x5A, 0x00, 0x01}; // mock exe bytes

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            JarEntry entry = new JarEntry("myapp.exe");
            jos.putNextEntry(entry);
            jos.write(exeContent);
            jos.closeEntry();
        }

        File extractDir = tempDir.resolve("extract").toFile();
        extractDir.mkdirs();

        downloader.extractBundleJar(jarFile, extractDir);

        File extractedExe = new File(extractDir, "myapp.exe");
        assertTrue(extractedExe.exists());
        assertArrayEquals(exeContent, Files.readAllBytes(extractedExe.toPath()));
    }

    @Test
    void testExtractBundleJar_directoryStructure() throws Exception {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();

        // Create a JAR mimicking a macOS .app bundle
        File jarFile = tempDir.resolve("bundle.jar").toFile();

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            jos.putNextEntry(new JarEntry("MyApp.app/"));
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("MyApp.app/Contents/"));
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("MyApp.app/Contents/MacOS/"));
            jos.closeEntry();

            JarEntry launcherEntry = new JarEntry("MyApp.app/Contents/MacOS/MyApp");
            jos.putNextEntry(launcherEntry);
            jos.write("#!/bin/bash\necho hello".getBytes("UTF-8"));
            jos.closeEntry();

            JarEntry plistEntry = new JarEntry("MyApp.app/Contents/Info.plist");
            jos.putNextEntry(plistEntry);
            jos.write("<plist></plist>".getBytes("UTF-8"));
            jos.closeEntry();
        }

        File extractDir = tempDir.resolve("extract").toFile();
        extractDir.mkdirs();

        downloader.extractBundleJar(jarFile, extractDir);

        assertTrue(new File(extractDir, "MyApp.app").isDirectory());
        assertTrue(new File(extractDir, "MyApp.app/Contents/MacOS/MyApp").exists());
        assertTrue(new File(extractDir, "MyApp.app/Contents/Info.plist").exists());
    }

    @Test
    void testExtractBundleJar_skipsMetaInf() throws Exception {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();

        File jarFile = tempDir.resolve("bundle.jar").toFile();

        try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jarFile))) {
            jos.putNextEntry(new JarEntry("META-INF/"));
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jos.write("Manifest-Version: 1.0\n".getBytes("UTF-8"));
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("myapp.exe"));
            jos.write(new byte[]{0x4D, 0x5A});
            jos.closeEntry();
        }

        File extractDir = tempDir.resolve("extract").toFile();
        extractDir.mkdirs();

        downloader.extractBundleJar(jarFile, extractDir);

        assertFalse(new File(extractDir, "META-INF").exists());
        assertTrue(new File(extractDir, "myapp.exe").exists());
    }

    @Test
    void testDownloadAndReplace_nullNpmPackageVersion() {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();
        File fakeApp = tempDir.resolve("fake.exe").toFile();

        assertFalse(downloader.downloadAndReplace(null, "win-x64", fakeApp, null));
    }

    @Test
    void testDownloadAndReplace_noArtifact() {
        PrebuiltBundleDownloader downloader = new PrebuiltBundleDownloader();

        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        packageJson.put("jdeploy", new JSONObject());
        NPMPackageVersion version = NPMPackageVersion.fromLocalPackageJson(packageJson);

        File fakeApp = tempDir.resolve("fake.exe").toFile();

        assertFalse(downloader.downloadAndReplace(version, "win-x64", fakeApp, null));
    }

    @Test
    void testGetPrebuiltArtifact_integration() {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");

        JSONObject jdeploy = new JSONObject();
        JSONObject artifacts = new JSONObject();

        JSONObject macArtifact = new JSONObject();
        macArtifact.put("url", "https://example.com/mac-arm64.jar");
        macArtifact.put("sha256", "abc123");
        artifacts.put("mac-arm64", macArtifact);

        JSONObject winArtifact = new JSONObject();
        winArtifact.put("url", "https://example.com/win-x64.jar");
        winArtifact.put("sha256", "def456");
        JSONObject cli = new JSONObject();
        cli.put("url", "https://example.com/win-x64-cli.jar");
        cli.put("sha256", "ghi789");
        winArtifact.put("cli", cli);
        artifacts.put("win-x64", winArtifact);

        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        NPMPackageVersion version = NPMPackageVersion.fromLocalPackageJson(packageJson);

        // Mac artifact
        PrebuiltArtifactInfo macInfo = version.getPrebuiltArtifact("mac-arm64");
        assertNotNull(macInfo);
        assertEquals("https://example.com/mac-arm64.jar", macInfo.getUrl());
        assertEquals("abc123", macInfo.getSha256());
        assertFalse(macInfo.hasCli());

        // Windows artifact with CLI
        PrebuiltArtifactInfo winInfo = version.getPrebuiltArtifact("win-x64");
        assertNotNull(winInfo);
        assertEquals("https://example.com/win-x64.jar", winInfo.getUrl());
        assertTrue(winInfo.hasCli());
        assertEquals("https://example.com/win-x64-cli.jar", winInfo.getCliUrl());

        // Missing platform
        assertNull(version.getPrebuiltArtifact("linux-x64"));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
