package ca.weblite.jdeploy.jvmdownloader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ZuluJVMDownloaderTest {

    private JVMKit kit;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        kit = new JVMKit();
    }

    @EnabledIfEnvironmentVariable(named = "JDEPLOY_TEST_JVM_DOWNLOADS", matches = "true")
    @ParameterizedTest
    @CsvSource({
            // "linux, arm, 32, true", JavaFX not supported on arm 32
            //"windows, arm, 64, true, 21", Windows ARM not supported by deprecated builds API
            //"windows, arm, 64, false, 21", Windows ARM not supported by deprecated builds API
            "windows, x86, 32, true, 11",
            "windows, x86, 32, false, 11",
            "windows, x86, 64, true, 11",
            "windows, x86, 64, false, 11",
            "linux, arm, 32, false, 11",
            "linux, arm, 64, true, 11",
            "linux, arm, 64, false, 11",
            "macos, x86, 64, true, 11",
            "macos, x86, 64, false, 11",
            "macos, arm, 64, true, 11",
            "macos, arm, 64, false, 11",
            "linux, x86, 32, true, 11",
            "linux, x86, 32, false, 11",
            "linux, x86, 64, true, 11",
            "linux, x86, 64, false, 11"

    })
    public void testDownloadJVM(String platform, String arch, String bitness, boolean javafx, String version) throws IOException {

        JVMFinder finder = kit.createFinder(false);

        ZuluJVMDownloader downloader = new ZuluJVMDownloader(kit);
        String basePath = tempDir.toString();
        String bundleType = "jre";
        IOException expectedException = null;
        try {
            finder.findJVM(tempDir.toString(), version, bundleType, javafx, platform, arch, bitness);
        } catch (IOException e) {
            expectedException = e;
        }
        assertInstanceOf(IOException.class, expectedException);
        downloader.downloadJVM(basePath, version, bundleType, javafx, platform, arch, bitness);

        String javafxStr = javafx ? "fx" : "default";
        String versionStr = bundleType + version + (javafx ? "fx" : "");
        String ext = platform.equals("linux") && (javafx || "arm".equals(arch)) ? "tar.gz" : "zip";
        String fileName = versionStr + "." + ext;
        Path savePath = tempDir.resolve(Paths.get("jre", "zulu", platform, arch + bitness, version, bundleType, javafxStr, fileName));

        assertTrue(Files.exists(savePath), "Downloaded file should exist: " + savePath);

        File foundJvm = finder.findJVM(tempDir.toString(), version, bundleType, javafx, platform, arch, bitness);
        assertTrue(foundJvm.exists(), "Found JVM should exist: " + foundJvm);
    }

    @EnabledIfEnvironmentVariable(named = "JDEPLOY_TEST_JVM_DOWNLOADS", matches = "true")
    @ParameterizedTest
    @CsvSource({
            // "linux, arm, 32, true", JavaFX not supported on arm 32
            //"windows, arm, 64, true, 21", Windows ARM not supported by deprecated builds API
            //"windows, arm, 64, false, 21", Windows ARM not supported by deprecated builds API
            "windows, x86, 32, true, 11",
            "windows, x86, 32, false, 11",
            "windows, x86, 64, true, 11",
            "windows, x86, 64, false, 11",
            "linux, arm, 32, false, 11",
            "linux, arm, 64, true, 11",
            "linux, arm, 64, false, 11",
            "macos, x86, 64, true, 11",
            "macos, x86, 64, false, 11",
            "macos, arm, 64, true, 11",
            "macos, arm, 64, false, 11",
            "linux, x86, 32, true, 11",
            "linux, x86, 32, false, 11",
            "linux, x86, 64, true, 11",
            "linux, x86, 64, false, 11"

    })
    public void testJVMFinder(String platform, String arch, String bitness, boolean javafx, String version) throws IOException {

        JVMFinder finder = kit.createFinder(true);

        String basePath = tempDir.toString();
        String bundleType = "jre";

        finder.findJVM(basePath, version, bundleType, javafx, platform, arch, bitness);

        String javafxStr = javafx ? "fx" : "default";
        String versionStr = bundleType + version + (javafx ? "fx" : "");
        String ext = platform.equals("linux") && (javafx || "arm".equals(arch)) ? "tar.gz" : "zip";
        String fileName = versionStr + "." + ext;
        Path savePath = tempDir.resolve(Paths.get("jre", "zulu", platform, arch + bitness, version, bundleType, javafxStr, fileName));

        assertTrue(Files.exists(savePath), "Downloaded file should exist: " + savePath);

        File foundJvm = finder.findJVM(tempDir.toString(), version, bundleType, javafx, platform, arch, bitness);
        assertTrue(foundJvm.exists(), "Found JVM should exist: " + foundJvm);
    }
}
