package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.appbundler.Bundler;
import ca.weblite.jdeploy.appbundler.BundlerResult;
import ca.weblite.jdeploy.models.BundleArtifact;
import ca.weblite.jdeploy.models.BundleManifest;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishing.BundleChecksumWriter;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for PublishBundleService to verify that:
 * - Platforms to build are driven by jdeploy.artifacts entries with enabled: true
 * - CLI bundles are only built for Windows platforms
 * - GUI bundles are built for all enabled artifact platforms
 * - BundleChecksumWriter merges url/sha256 into existing artifact entries
 */
class PublishBundleServiceTest {

    @TempDir
    File tempDir;

    private PublishBundleService publishBundleService;
    private File packageJsonFile;
    private File jarFile;

    @BeforeEach
    void setUp() throws IOException {
        publishBundleService = new PublishBundleService();

        // Create a fake JAR file so the bundler has something to reference
        jarFile = new File(tempDir, "app.jar");
        jarFile.createNewFile();

        packageJsonFile = new File(tempDir, "package.json");
    }

    private PackagingContext createContext(Map<String, Object> packageJsonMap) {
        // Use null streams to avoid corrupting Surefire's forked JVM communication
        PrintStream nullOut = new PrintStream(new OutputStream() {
            public void write(int b) {}
        });
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
                nullOut,
                nullOut,
                new ByteArrayInputStream(new byte[0]),
                false,  // exitOnFail
                false   // isBuildRequired
        );
    }

    /**
     * Creates a package.json map with jdeploy.artifacts entries.
     *
     * @param withCommands whether to include CLI commands
     * @param platformKeys platform keys to enable (e.g. "mac-arm64", "win-x64")
     */
    private Map<String, Object> createPackageJson(boolean withCommands, String... platformKeys) {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");

        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", jarFile.getAbsolutePath());
        jdeploy.put("title", "Test App");

        if (withCommands) {
            Map<String, Object> commands = new HashMap<>();
            Map<String, Object> cmd1 = new HashMap<>();
            cmd1.put("description", "A test command");
            commands.put("testcmd", cmd1);
            jdeploy.put("commands", commands);
        }

        // Build artifacts map with enabled: true for each platform
        Map<String, Object> artifacts = new LinkedHashMap<>();
        for (String key : platformKeys) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("enabled", true);
            artifacts.put(key, entry);
        }
        jdeploy.put("artifacts", artifacts);

        packageJson.put("jdeploy", jdeploy);
        return packageJson;
    }

    private void writePackageJson(Map<String, Object> packageJsonMap) throws IOException {
        JSONObject json = new JSONObject(packageJsonMap);
        FileUtils.writeStringToFile(packageJsonFile, json.toString(2), StandardCharsets.UTF_8);
    }

    // -- Bundler.runit() mock helper --

    private void mockBundlerRunit(MockedStatic<Bundler> bundlerMock, List<BundlerRunitCall> calls) {
        bundlerMock.when(() -> Bundler.runit(
                any(), any(), anyString(), anyString(), anyString(), anyString()
        )).thenAnswer(invocation -> {
            String target = invocation.getArgument(3);
            ca.weblite.jdeploy.appbundler.BundlerSettings settings = invocation.getArgument(0);
            boolean isCli = settings.isCliCommandsEnabled();

            if (calls != null) {
                calls.add(new BundlerRunitCall(target, isCli));
            }

            BundlerResult result = new BundlerResult(target);
            String destDir = invocation.getArgument(4);
            File outputFile = new File(destDir, "fake-bundle" + (isCli ? "-cli" : "") + ".exe");
            outputFile.getParentFile().mkdirs();
            outputFile.createNewFile();
            result.setOutputFile(outputFile);
            return result;
        });
    }

    // -- isEnabled tests --

    @Test
    @DisplayName("isEnabled returns false when no artifacts section exists")
    void isEnabled_returnsFalse_whenNoArtifacts() {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", jarFile.getAbsolutePath());
        packageJson.put("jdeploy", jdeploy);

        PackagingContext context = createContext(packageJson);
        assertFalse(publishBundleService.isEnabled(context));
    }

    @Test
    @DisplayName("isEnabled returns false when artifacts exist but none are enabled")
    void isEnabled_returnsFalse_whenNoneEnabled() {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", jarFile.getAbsolutePath());
        Map<String, Object> artifacts = new HashMap<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("enabled", false);
        artifacts.put("mac-arm64", entry);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        PackagingContext context = createContext(packageJson);
        assertFalse(publishBundleService.isEnabled(context));
    }

    @Test
    @DisplayName("isEnabled returns true when at least one artifact is enabled")
    void isEnabled_returnsTrue_whenEnabled() {
        Map<String, Object> packageJson = createPackageJson(false, "mac-arm64");
        PackagingContext context = createContext(packageJson);
        assertTrue(publishBundleService.isEnabled(context));
    }

    @Test
    @DisplayName("isEnabled handles String 'true' from JSONParser (default boolean parsing)")
    void isEnabled_returnsTrue_whenEnabledIsStringTrue() {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", jarFile.getAbsolutePath());
        Map<String, Object> artifacts = new HashMap<>();
        Map<String, Object> entry = new HashMap<>();
        // JSONParser parses JSON true as String "true" by default
        entry.put("enabled", "true");
        artifacts.put("mac-arm64", entry);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        PackagingContext context = createContext(packageJson);
        assertTrue(publishBundleService.isEnabled(context),
                "isEnabled should handle String 'true' from JSONParser");
    }

    @Test
    @DisplayName("isEnabled returns false when enabled is String 'false'")
    void isEnabled_returnsFalse_whenEnabledIsStringFalse() {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", jarFile.getAbsolutePath());
        Map<String, Object> artifacts = new HashMap<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("enabled", "false");
        artifacts.put("mac-arm64", entry);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        PackagingContext context = createContext(packageJson);
        assertFalse(publishBundleService.isEnabled(context));
    }

    // -- buildBundles tests --

    @Test
    @DisplayName("buildBundles returns empty manifest when no artifacts are enabled")
    void buildBundles_returnsEmpty_whenNoArtifacts() throws IOException {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", jarFile.getAbsolutePath());
        packageJson.put("jdeploy", jdeploy);
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        BundleManifest manifest = publishBundleService.buildBundles(context, null);
        assertTrue(manifest.isEmpty());
    }

    @Test
    @DisplayName("buildBundles creates GUI bundles for all enabled platforms and CLI only for Windows")
    void buildBundles_cliBundlesOnlyForWindows() throws IOException {
        Map<String, Object> packageJson = createPackageJson(
                true, "mac-arm64", "mac-x64", "win-x64", "linux-x64"
        );
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        List<BundlerRunitCall> calls = new ArrayList<>();

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, calls);

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            // 4 GUI + 1 CLI (Windows only) = 5
            assertEquals(5, manifest.getArtifacts().size(),
                    "Expected 4 GUI + 1 CLI (Windows only) = 5 artifacts");

            // Verify CLI bundles
            List<BundleArtifact> cliArtifacts = manifest.getArtifacts().stream()
                    .filter(BundleArtifact::isCli)
                    .collect(Collectors.toList());
            assertEquals(1, cliArtifacts.size(), "Should have exactly 1 CLI artifact");
            assertEquals("win", cliArtifacts.get(0).getPlatform());
            assertEquals("x64", cliArtifacts.get(0).getArch());

            // Verify GUI bundles for all platforms
            Set<String> guiPlatformKeys = manifest.getArtifacts().stream()
                    .filter(a -> !a.isCli())
                    .map(BundleArtifact::getPlatformKey)
                    .collect(Collectors.toSet());
            assertEquals(new HashSet<>(Arrays.asList("mac-arm64", "mac-x64", "win-x64", "linux-x64")),
                    guiPlatformKeys);

            // Verify Bundler.runit() CLI call was only for win-x64
            List<BundlerRunitCall> cliCalls = calls.stream()
                    .filter(c -> c.isCli)
                    .collect(Collectors.toList());
            assertEquals(1, cliCalls.size());
            assertEquals("win-x64", cliCalls.get(0).target);
        }
    }

    @Test
    @DisplayName("buildBundles only builds for enabled platforms, not all")
    void buildBundles_onlyBuildsEnabledPlatforms() throws IOException {
        // Only enable win-x64 and linux-x64, not mac
        Map<String, Object> packageJson = createPackageJson(false, "win-x64", "linux-x64");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, null);

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(2, manifest.getArtifacts().size());
            Set<String> keys = manifest.getArtifacts().stream()
                    .map(BundleArtifact::getPlatformKey)
                    .collect(Collectors.toSet());
            assertEquals(new HashSet<>(Arrays.asList("win-x64", "linux-x64")), keys);
        }
    }

    @Test
    @DisplayName("buildBundles creates no CLI bundles when there are no commands")
    void buildBundles_noCliBundles_whenNoCommands() throws IOException {
        Map<String, Object> packageJson = createPackageJson(false, "mac-arm64", "win-x64", "linux-x64");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, null);

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(3, manifest.getArtifacts().size());
            assertTrue(manifest.getArtifacts().stream().noneMatch(BundleArtifact::isCli),
                    "No CLI artifacts should be created when no commands are defined");
        }
    }

    @Test
    @DisplayName("buildBundles creates no CLI bundles for Mac even with commands")
    void buildBundles_noCliBundles_forMac_evenWithCommands() throws IOException {
        Map<String, Object> packageJson = createPackageJson(true, "mac-arm64", "mac-x64");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, null);

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(2, manifest.getArtifacts().size());
            assertTrue(manifest.getArtifacts().stream().noneMatch(BundleArtifact::isCli),
                    "No CLI artifacts should be created for Mac platforms");
        }
    }

    @Test
    @DisplayName("buildBundles creates no CLI bundles for Linux even with commands")
    void buildBundles_noCliBundles_forLinux_evenWithCommands() throws IOException {
        Map<String, Object> packageJson = createPackageJson(true, "linux-x64");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, null);

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(1, manifest.getArtifacts().size());
            assertFalse(manifest.getArtifacts().get(0).isCli());
        }
    }

    @Test
    @DisplayName("Artifact filenames follow expected convention")
    void buildBundles_artifactFilenames_followConvention() throws IOException {
        Map<String, Object> packageJson = createPackageJson(true, "win-x64");
        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, null);

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(2, manifest.getArtifacts().size(), "Should have GUI + CLI for Windows");

            BundleArtifact gui = manifest.getArtifacts().stream()
                    .filter(a -> !a.isCli()).findFirst().orElseThrow(() -> new RuntimeException("GUI artifact not found"));
            BundleArtifact cli = manifest.getArtifacts().stream()
                    .filter(BundleArtifact::isCli).findFirst().orElseThrow(() -> new RuntimeException("CLI artifact not found"));

            assertTrue(gui.getFilename().contains("-win-x64-1.0.0.jar"),
                    "GUI filename should contain platform-arch-version: " + gui.getFilename());
            assertTrue(cli.getFilename().contains("-win-x64-1.0.0-cli.jar"),
                    "CLI filename should contain platform-arch-version-cli: " + cli.getFilename());

            assertNotNull(gui.getSha256());
            assertEquals(64, gui.getSha256().length(), "SHA-256 should be 64 hex chars");
            assertNotNull(cli.getSha256());
            assertEquals(64, cli.getSha256().length(), "SHA-256 should be 64 hex chars");
        }
    }

    @Test
    @DisplayName("buildBundles works with String 'true' enabled values (as parsed by JSONParser)")
    void buildBundles_worksWithStringEnabled() throws IOException {
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", jarFile.getAbsolutePath());
        jdeploy.put("title", "Test App");
        Map<String, Object> artifacts = new LinkedHashMap<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("enabled", "true");  // String, not Boolean
        artifacts.put("linux-x64", entry);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        writePackageJson(packageJson);
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, null);

            BundleManifest manifest = publishBundleService.buildBundles(context, null);

            assertEquals(1, manifest.getArtifacts().size(),
                    "Should build bundles even when enabled is String 'true'");
            assertEquals("linux-x64", manifest.getArtifacts().get(0).getPlatformKey());
        }
    }

    // -- BundleManifest JSON output tests --

    @Test
    @DisplayName("BundleManifest.toPackageJsonBundles only produces cli sub-object for Windows")
    void bundleManifest_toPackageJsonBundles_cliOnlyForWindows() {
        List<BundleArtifact> artifacts = new ArrayList<>();
        artifacts.add(new BundleArtifact(new File("mac.jar"), "mac", "arm64", "1.0.0", false, "aaa", "app-mac-arm64-1.0.0.jar"));
        artifacts.add(new BundleArtifact(new File("win.jar"), "win", "x64", "1.0.0", false, "bbb", "app-win-x64-1.0.0.jar"));
        artifacts.add(new BundleArtifact(new File("win-cli.jar"), "win", "x64", "1.0.0", true, "ccc", "app-win-x64-1.0.0-cli.jar"));
        artifacts.add(new BundleArtifact(new File("linux.jar"), "linux", "x64", "1.0.0", false, "ddd", "app-linux-x64-1.0.0.jar"));

        artifacts.get(0).setUrl("https://example.com/mac.jar");
        artifacts.get(1).setUrl("https://example.com/win.jar");
        artifacts.get(2).setUrl("https://example.com/win-cli.jar");
        artifacts.get(3).setUrl("https://example.com/linux.jar");

        BundleManifest manifest = new BundleManifest(artifacts);
        JSONObject json = manifest.toPackageJsonBundles();

        // Mac: url + sha256, no cli
        JSONObject macEntry = json.getJSONObject("mac-arm64");
        assertTrue(macEntry.has("url"));
        assertTrue(macEntry.has("sha256"));
        assertFalse(macEntry.has("cli"), "Mac entry should NOT have cli sub-object");

        // Windows: url + sha256 + cli
        JSONObject winEntry = json.getJSONObject("win-x64");
        assertTrue(winEntry.has("url"));
        assertTrue(winEntry.has("sha256"));
        assertTrue(winEntry.has("cli"), "Windows entry should have cli sub-object");
        assertEquals("https://example.com/win-cli.jar", winEntry.getJSONObject("cli").getString("url"));

        // Linux: url + sha256, no cli
        JSONObject linuxEntry = json.getJSONObject("linux-x64");
        assertTrue(linuxEntry.has("url"));
        assertTrue(linuxEntry.has("sha256"));
        assertFalse(linuxEntry.has("cli"), "Linux entry should NOT have cli sub-object");
    }

    // -- BundleChecksumWriter merge tests --

    @Test
    @DisplayName("BundleChecksumWriter preserves enabled field when merging url/sha256")
    void bundleChecksumWriter_preservesEnabledField() throws IOException {
        // Create package.json with seeded artifacts (enabled: true)
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        JSONObject jdeploy = new JSONObject();
        JSONObject artifacts = new JSONObject();
        JSONObject macEntry = new JSONObject();
        macEntry.put("enabled", true);
        artifacts.put("mac-arm64", macEntry);
        JSONObject winEntry = new JSONObject();
        winEntry.put("enabled", true);
        artifacts.put("win-x64", winEntry);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        File publishPackageJson = new File(tempDir, "publish-package.json");
        FileUtils.writeStringToFile(publishPackageJson, packageJson.toString(2), StandardCharsets.UTF_8);

        // Create manifest with built artifacts
        List<BundleArtifact> builtArtifacts = new ArrayList<>();
        BundleArtifact mac = new BundleArtifact(new File("m.jar"), "mac", "arm64", "1.0.0", false, "abc123", "app-mac-arm64.jar");
        mac.setUrl("https://example.com/mac.jar");
        builtArtifacts.add(mac);
        BundleArtifact win = new BundleArtifact(new File("w.jar"), "win", "x64", "1.0.0", false, "def456", "app-win-x64.jar");
        win.setUrl("https://example.com/win.jar");
        builtArtifacts.add(win);
        BundleManifest manifest = new BundleManifest(builtArtifacts);

        // Write using BundleChecksumWriter
        BundleChecksumWriter writer = new BundleChecksumWriter();
        writer.writeBundles(publishPackageJson, manifest);

        // Read back and verify
        String result = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject resultJson = new JSONObject(result);
        JSONObject resultArtifacts = resultJson.getJSONObject("jdeploy").getJSONObject("artifacts");

        // Mac entry should have enabled + url + sha256
        JSONObject resultMac = resultArtifacts.getJSONObject("mac-arm64");
        assertTrue(resultMac.getBoolean("enabled"), "enabled field should be preserved");
        assertEquals("https://example.com/mac.jar", resultMac.getString("url"));
        assertEquals("abc123", resultMac.getString("sha256"));

        // Win entry should have enabled + url + sha256
        JSONObject resultWin = resultArtifacts.getJSONObject("win-x64");
        assertTrue(resultWin.getBoolean("enabled"), "enabled field should be preserved");
        assertEquals("https://example.com/win.jar", resultWin.getString("url"));
        assertEquals("def456", resultWin.getString("sha256"));
    }

    // -- App URL resolution tests (icon.png co-location) --

    @Test
    @DisplayName("buildBundles uses jdeploy-bundle jar when it exists (for icon resolution)")
    void buildBundles_usesJdeployBundleJar_whenAvailable() throws IOException {
        // Simulate a project where jar is in target/ subdirectory
        File targetDir = new File(tempDir, "target");
        targetDir.mkdirs();
        File targetJar = new File(targetDir, "myapp.jar");
        targetJar.createNewFile();

        // Create jdeploy-bundle with the jar AND icon.png (as PackageService.bundleIcon does)
        File jdeployBundleDir = new File(tempDir, "jdeploy-bundle");
        jdeployBundleDir.mkdirs();
        File bundledJar = new File(jdeployBundleDir, "myapp.jar");
        bundledJar.createNewFile();
        File bundledIcon = new File(jdeployBundleDir, "icon.png");
        bundledIcon.createNewFile();

        // Create package.json with jar pointing to target/myapp.jar
        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", "target/myapp.jar");
        jdeploy.put("title", "Test App");
        Map<String, Object> artifacts = new LinkedHashMap<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("enabled", true);
        artifacts.put("linux-x64", entry);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        File pjFile = new File(tempDir, "package.json");
        FileUtils.writeStringToFile(pjFile, new JSONObject(packageJson).toString(2), "UTF-8");
        PackagingContext context = createContext(packageJson);

        // Capture the AppInfo URL passed to Bundler.runit
        List<String> capturedUrls = new ArrayList<>();

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            bundlerMock.when(() -> Bundler.runit(
                    any(), any(), anyString(), anyString(), anyString(), anyString()
            )).thenAnswer(invocation -> {
                String url = invocation.getArgument(2);
                capturedUrls.add(url);

                BundlerResult result = new BundlerResult(invocation.getArgument(3));
                String destDir = invocation.getArgument(4);
                File outputFile = new File(destDir, "fake-bundle.bin");
                outputFile.getParentFile().mkdirs();
                outputFile.createNewFile();
                result.setOutputFile(outputFile);
                return result;
            });

            publishBundleService.buildBundles(context, null);

            // Verify the app URL points to the jdeploy-bundle jar, not target/myapp.jar
            assertFalse(capturedUrls.isEmpty(), "Bundler.runit should have been called");
            String appUrl = capturedUrls.get(0);
            assertTrue(appUrl.contains("jdeploy-bundle"),
                    "App URL should point to jdeploy-bundle dir for icon resolution, was: " + appUrl);
            assertFalse(appUrl.contains("/target/"),
                    "App URL should NOT point to target/ dir, was: " + appUrl);
        }
    }

    @Test
    @DisplayName("buildBundles copies icon.png next to jar when jdeploy-bundle doesn't exist")
    void buildBundles_copiesIcon_whenNoJdeployBundle() throws IOException {
        // Simulate a project where jar is in target/ subdirectory
        File targetDir = new File(tempDir, "target");
        targetDir.mkdirs();
        File targetJar = new File(targetDir, "myapp.jar");
        targetJar.createNewFile();

        // Put icon.png in project root only (NOT next to the jar)
        File projectIcon = new File(tempDir, "icon.png");
        FileUtils.writeStringToFile(projectIcon, "fake-icon-data", "UTF-8");

        // Do NOT create jdeploy-bundle directory

        Map<String, Object> packageJson = new HashMap<>();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");
        Map<String, Object> jdeploy = new HashMap<>();
        jdeploy.put("jar", "target/myapp.jar");
        jdeploy.put("title", "Test App");
        Map<String, Object> artifacts = new LinkedHashMap<>();
        Map<String, Object> entry = new HashMap<>();
        entry.put("enabled", true);
        artifacts.put("linux-x64", entry);
        jdeploy.put("artifacts", artifacts);
        packageJson.put("jdeploy", jdeploy);

        File pjFile = new File(tempDir, "package.json");
        FileUtils.writeStringToFile(pjFile, new JSONObject(packageJson).toString(2), "UTF-8");
        PackagingContext context = createContext(packageJson);

        try (MockedStatic<Bundler> bundlerMock = mockStatic(Bundler.class)) {
            mockBundlerRunit(bundlerMock, null);

            publishBundleService.buildBundles(context, null);

            // Verify icon.png was copied next to the jar
            File copiedIcon = new File(targetDir, "icon.png");
            assertTrue(copiedIcon.exists(),
                    "icon.png should be copied to target/ dir when jdeploy-bundle doesn't exist");
        }
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
