package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerResult;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsJsonReader;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsJsonWriter;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.models.BundleArtifact;
import ca.weblite.jdeploy.models.BundleManifest;
import ca.weblite.jdeploy.packaging.PackagingContext;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PublishBundleService to verify that:
 * - CLI bundles are only built for Windows platforms
 * - GUI bundles are built for all configured platforms
 * - publishBundles flag controls the feature
 */
class PublishBundleServiceTest {

    @TempDir
    File tempDir;

    private PublishBundleService publishBundleService;
    private BundleCodeService bundleCodeService;
    private File packageJsonFile;
    private File jarFile;

    @BeforeEach
    void setUp() throws IOException {
        DownloadPageSettingsService downloadPageSettingsService = new DownloadPageSettingsService(
                new DownloadPageSettingsJsonReader(),
                new DownloadPageSettingsJsonWriter()
        );
        bundleCodeService = mock(BundleCodeService.class);
        publishBundleService = new PublishBundleService(downloadPageSettingsService, bundleCodeService);

        // Create a fake JAR file so the bundler has something to reference
        jarFile = new File(tempDir, "app.jar");
        jarFile.createNewFile();

        packageJsonFile = new File(tempDir, "package.json");
    }

    private PackagingContext createContext(Map<String, Object> packageJsonMap) {
        return new PackagingContext(
                tempDir,
                packageJsonMap,
                packageJsonFile,
                false,  // alwaysClean
                false,  // doNotStripJavaFXFiles
                null,   // bundlesOverride
                null,   // installersOverride
                null,   // keyProvider
                null,   // packageSigningService
                System.out,
                System.err,
                System.in,
                false,  // exitOnFail
                false   // isBuildRequired
        );
    }

    private Map<String, Object> createPackageJson(boolean publishBundles, boolean withCommands, String... platforms) {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");

        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("publishBundles", publishBundles);
        jdeploy.put("jar", jarFile.getAbsolutePath());
        jdeploy.put("title", "Test App");

        if (withCommands) {
            Map<String, Object> commands = new HashMap<>();
            Map<String, Object> cmd1 = new HashMap<>();
            cmd1.put("description", "A test command");
            commands.put("testcmd", cmd1);
            jdeploy.put("commands", commands);
        }

        Map<String, Object> downloadPage = new HashMap<>();
        downloadPage.put("platforms", Arrays.asList(platforms));
        jdeploy.put("downloadPage", downloadPage);

        packageJson.put("jdeploy", jdeploy);
        return packageJson;
    }

    private void writePackageJson(Map<String, Object> packageJsonMap) throws IOException {
        JSONObject json = new JSONObject(packageJsonMap);
        FileUtils.writeStringToFile(packageJsonFile, json.toString(2), StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("isEnabled returns false when publishBundles is not set")
    void isEnabled_returnsFalse_whenNotSet() throws IOException {
        Map<String, Object> packageJson = createPackageJson(false, false, "default");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);
        assertFalse(publishBundleService.isEnabled(context));
    }

    @Test
    @DisplayName("isEnabled returns true when publishBundles is true")
    void isEnabled_returnsTrue_whenSet() throws IOException {
        Map<String, Object> packageJson = createPackageJson(true, false, "default");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);
        assertTrue(publishBundleService.isEnabled(context));
    }

    @Test
    @DisplayName("buildBundles returns empty manifest when publishBundles is false")
    void buildBundles_returnsEmpty_whenDisabled() throws IOException {
        Map<String, Object> packageJson = createPackageJson(false, false, "default");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        BundleManifest manifest = publishBundleService.buildBundles(context, null);
        assertTrue(manifest.isEmpty());
    }

    @Test
    @DisplayName("buildBundles creates GUI bundles for all platforms and CLI only for Windows")
    void buildBundles_cliBundlesOnlyForWindows() throws IOException {
        Map<String, Object> packageJson = createPackageJson(
                true, true, "mac-arm64", "mac-x64", "windows-x64", "linux-x64"
        );
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        // Track all Bundler.runit() calls
        List<BundlerRunitCall> calls = new ArrayList<>();

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String target = invocation.getArgument(3);
                ca.weblite.jdeploy.appbundler.BundlerSettings settings = invocation.getArgument(0);
                boolean isCli = settings.isCliCommandsEnabled();

                calls.add(new BundlerRunitCall(target, isCli));

                // Create a fake output file so wrapInJar works
                BundlerResult result = new BundlerResult(target);
                String destDir = invocation.getArgument(4);
                File outputFile = new File(destDir, "fake-bundle" + (isCli ? "-cli" : "") + ".exe");
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                result.setOutputFile(outputFile);
                return result;
            });

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            // Should have GUI bundles for all 4 platforms + CLI bundle only for Windows = 5 total
            assertEquals(5, manifest.getArtifacts().size(),
                    "Expected 4 GUI + 1 CLI (Windows only) = 5 artifacts");

            // Verify CLI bundles
            List<BundleArtifact> cliArtifacts = manifest.getArtifacts().stream()
                    .filter(BundleArtifact::isCli)
                    .collect(Collectors.toList());
            assertEquals(1, cliArtifacts.size(), "Should have exactly 1 CLI artifact");
            assertEquals("win", cliArtifacts.get(0).getPlatform(),
                    "CLI artifact should be for Windows only");
            assertEquals("x64", cliArtifacts.get(0).getArch());

            // Verify GUI bundles exist for all platforms
            List<BundleArtifact> guiArtifacts = manifest.getArtifacts().stream()
                    .filter(a -> !a.isCli())
                    .collect(Collectors.toList());
            assertEquals(4, guiArtifacts.size(), "Should have 4 GUI artifacts");
            Set<String> guiPlatformKeys = guiArtifacts.stream()
                    .map(BundleArtifact::getPlatformKey)
                    .collect(Collectors.toSet());
            assertTrue(guiPlatformKeys.contains("mac-arm64"));
            assertTrue(guiPlatformKeys.contains("mac-x64"));
            assertTrue(guiPlatformKeys.contains("win-x64"));
            assertTrue(guiPlatformKeys.contains("linux-x64"));

            // Verify Bundler.runit() was called with cliMode=true only for win-x64
            List<BundlerRunitCall> cliCalls = calls.stream()
                    .filter(c -> c.isCli)
                    .collect(Collectors.toList());
            assertEquals(1, cliCalls.size(), "Bundler.runit should be called with CLI mode only once");
            assertEquals("win-x64", cliCalls.get(0).target,
                    "CLI build should target win-x64");
        }
    }

    @Test
    @DisplayName("buildBundles creates no CLI bundles when there are no commands")
    void buildBundles_noCliBundles_whenNoCommands() throws IOException {
        Map<String, Object> packageJson = createPackageJson(
                true, false, "mac-arm64", "windows-x64", "linux-x64"
        );
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String target = invocation.getArgument(3);
                BundlerResult result = new BundlerResult(target);
                String destDir = invocation.getArgument(4);
                File outputFile = new File(destDir, "fake-bundle.exe");
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                result.setOutputFile(outputFile);
                return result;
            });

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            // 3 GUI bundles, 0 CLI bundles
            assertEquals(3, manifest.getArtifacts().size());
            assertTrue(manifest.getArtifacts().stream().noneMatch(BundleArtifact::isCli),
                    "No CLI artifacts should be created when no commands are defined");
        }
    }

    @Test
    @DisplayName("buildBundles creates no CLI bundles for Mac even with commands")
    void buildBundles_noCliBundles_forMac_evenWithCommands() throws IOException {
        // Only Mac platforms - should get 0 CLI bundles even with commands
        Map<String, Object> packageJson = createPackageJson(
                true, true, "mac-arm64", "mac-x64"
        );
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String target = invocation.getArgument(3);
                BundlerResult result = new BundlerResult(target);
                String destDir = invocation.getArgument(4);
                File outputDir = new File(destDir, "TestApp.app");
                outputDir.mkdirs();
                new File(outputDir, "Contents/MacOS/launcher").getParentFile().mkdirs();
                new File(outputDir, "Contents/MacOS/launcher").createNewFile();
                result.setOutputFile(outputDir);
                return result;
            });

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(2, manifest.getArtifacts().size(), "Should have 2 GUI artifacts for Mac");
            assertTrue(manifest.getArtifacts().stream().noneMatch(BundleArtifact::isCli),
                    "No CLI artifacts should be created for Mac platforms");
        }
    }

    @Test
    @DisplayName("buildBundles creates no CLI bundles for Linux even with commands")
    void buildBundles_noCliBundles_forLinux_evenWithCommands() throws IOException {
        Map<String, Object> packageJson = createPackageJson(
                true, true, "linux-x64"
        );
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String target = invocation.getArgument(3);
                BundlerResult result = new BundlerResult(target);
                String destDir = invocation.getArgument(4);
                File outputFile = new File(destDir, "test-app");
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                result.setOutputFile(outputFile);
                return result;
            });

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(1, manifest.getArtifacts().size());
            assertFalse(manifest.getArtifacts().get(0).isCli(),
                    "Linux artifact should not be CLI");
        }
    }

    @Test
    @DisplayName("Artifact filenames follow expected convention")
    void buildBundles_artifactFilenames_followConvention() throws IOException {
        Map<String, Object> packageJson = createPackageJson(
                true, true, "windows-x64"
        );
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String target = invocation.getArgument(3);
                BundlerResult result = new BundlerResult(target);
                String destDir = invocation.getArgument(4);
                File outputFile = new File(destDir, "test-app.exe");
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                result.setOutputFile(outputFile);
                return result;
            });

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(2, manifest.getArtifacts().size(), "Should have GUI + CLI for Windows");

            BundleArtifact gui = manifest.getArtifacts().stream()
                    .filter(a -> !a.isCli()).findFirst().orElseThrow();
            BundleArtifact cli = manifest.getArtifacts().stream()
                    .filter(BundleArtifact::isCli).findFirst().orElseThrow();

            assertTrue(gui.getFilename().contains("-win-x64-1.0.0.jar"),
                    "GUI filename should contain platform-arch-version: " + gui.getFilename());
            assertTrue(cli.getFilename().contains("-win-x64-1.0.0-cli.jar"),
                    "CLI filename should contain platform-arch-version-cli: " + cli.getFilename());

            // Both should have SHA-256 hashes
            assertNotNull(gui.getSha256());
            assertEquals(64, gui.getSha256().length(), "SHA-256 should be 64 hex chars");
            assertNotNull(cli.getSha256());
            assertEquals(64, cli.getSha256().length(), "SHA-256 should be 64 hex chars");
        }
    }

    @Test
    @DisplayName("BundleManifest.toPackageJsonBundles only produces cli sub-object for Windows")
    void bundleManifest_toPackageJsonBundles_cliOnlyForWindows() {
        List<BundleArtifact> artifacts = new ArrayList<>();
        artifacts.add(new BundleArtifact(new File("mac.jar"), "mac", "arm64", "1.0.0", false, "aaa", "app-mac-arm64-1.0.0.jar"));
        artifacts.add(new BundleArtifact(new File("win.jar"), "win", "x64", "1.0.0", false, "bbb", "app-win-x64-1.0.0.jar"));
        artifacts.add(new BundleArtifact(new File("win-cli.jar"), "win", "x64", "1.0.0", true, "ccc", "app-win-x64-1.0.0-cli.jar"));
        artifacts.add(new BundleArtifact(new File("linux.jar"), "linux", "x64", "1.0.0", false, "ddd", "app-linux-x64-1.0.0.jar"));

        // Set URLs
        artifacts.get(0).setUrl("https://example.com/mac.jar");
        artifacts.get(1).setUrl("https://example.com/win.jar");
        artifacts.get(2).setUrl("https://example.com/win-cli.jar");
        artifacts.get(3).setUrl("https://example.com/linux.jar");

        BundleManifest manifest = new BundleManifest(artifacts);
        JSONObject json = manifest.toPackageJsonBundles();

        // Mac should have url + sha256, no cli
        JSONObject macEntry = json.getJSONObject("mac-arm64");
        assertTrue(macEntry.has("url"));
        assertTrue(macEntry.has("sha256"));
        assertFalse(macEntry.has("cli"), "Mac entry should NOT have cli sub-object");

        // Windows should have url + sha256 + cli sub-object
        JSONObject winEntry = json.getJSONObject("win-x64");
        assertTrue(winEntry.has("url"));
        assertTrue(winEntry.has("sha256"));
        assertTrue(winEntry.has("cli"), "Windows entry should have cli sub-object");
        JSONObject winCli = winEntry.getJSONObject("cli");
        assertEquals("https://example.com/win-cli.jar", winCli.getString("url"));
        assertEquals("ccc", winCli.getString("sha256"));

        // Linux should have url + sha256, no cli
        JSONObject linuxEntry = json.getJSONObject("linux-x64");
        assertTrue(linuxEntry.has("url"));
        assertTrue(linuxEntry.has("sha256"));
        assertFalse(linuxEntry.has("cli"), "Linux entry should NOT have cli sub-object");
    }

    /**
     * Helper class to record Bundler.runit() calls for assertion.
     */
    private static class BundlerRunitCall {
        final String target;
        final boolean isCli;

        BundlerRunitCall(String target, boolean isCli) {
            this.target = target;
            this.isCli = isCli;
        }
    }
}
