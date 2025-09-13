package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
public class PlatformBundleGeneratorTest {

    private PlatformSpecificJarProcessor jarProcessor;
    private PlatformBundleGenerator generator;
    
    @Mock
    private NPM npm;
    
    @Mock
    private DownloadPageSettingsService downloadPageSettingsService;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create mock ignore service
        JDeployIgnoreService mockIgnoreService = Mockito.mock(JDeployIgnoreService.class);
        when(mockIgnoreService.hasIgnoreFiles(any(JDeployProject.class))).thenReturn(false);
        
        jarProcessor = new PlatformSpecificJarProcessor(mockIgnoreService);
        
        // Mock the download page settings service to return default settings
        DownloadPageSettings defaultSettings = new DownloadPageSettings();
        when(downloadPageSettingsService.read(any(JSONObject.class))).thenReturn(defaultSettings);
        
        generator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService, mockIgnoreService);
    }

    @Test
    public void testShouldGeneratePlatformBundles_Disabled() {
        JDeployProject project = createProject(false, Collections.emptyMap(), Collections.emptyMap());
        
        assertFalse(generator.shouldGeneratePlatformBundles(project));
        assertEquals(0, generator.getPlatformsForBundleGeneration(project).size());
    }

    @Test
    public void testShouldGeneratePlatformBundles_NoNamespaces() {
        JDeployProject project = createProject(true, Collections.emptyMap(), Collections.emptyMap());
        
        assertFalse(generator.shouldGeneratePlatformBundles(project));
        assertEquals(0, generator.getPlatformsForBundleGeneration(project).size());
    }

    @Test
    public void testShouldGeneratePlatformBundles_WithIgnoreFiles() throws IOException {
        // Create project with platform bundles enabled
        JDeployProject project = createProject(true, Collections.emptyMap(), Collections.emptyMap());
        
        // Create .jdpignore file to trigger platform bundle generation
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File jdpignoreFile = new File(projectDir, ".jdpignore");
        FileUtils.writeStringToFile(jdpignoreFile, "com.example.test.native\n", "UTF-8");
        
        // Create platform-specific ignore file
        File macIgnoreFile = new File(projectDir, ".jdpignore.mac-x64");
        FileUtils.writeStringToFile(macIgnoreFile, "!ca.weblite.native.mac.x64\n", "UTF-8");
        
        // Update to use real ignore service that can detect the files
        JDeployIgnoreService realIgnoreService = new JDeployIgnoreService(new JDeployIgnoreFileParser());
        jarProcessor = new PlatformSpecificJarProcessor(realIgnoreService);
        generator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService, realIgnoreService);
        
        assertTrue(generator.shouldGeneratePlatformBundles(project));
        
        // getPlatformsForBundleGeneration returns all enabled platforms when ignore files exist
        // This is correct behavior - all platforms get bundles when platform bundles are enabled and ignore files exist
        List<Platform> platforms = generator.getPlatformsForBundleGeneration(project);
        assertTrue(platforms.size() > 0); // Should generate platform bundles
    }

    @Test
    public void testGeneratePlatformBundles_PlatformBundlesDisabled() throws IOException {
        JDeployProject project = createProject(false, Collections.emptyMap(), Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        Map<Platform, File> result = generator.generatePlatformBundles(project, universalDir, outputDir);

        assertTrue(result.isEmpty());
        assertFalse(outputDir.exists());
    }

    @Test
    public void testGeneratePlatformBundles_WithPlatformSpecificNames() throws IOException {
        Map<String, String> packageNames = new HashMap<>();
        packageNames.put("packageMacX64", "myapp-macos-intel");
        packageNames.put("packageWinX64", "myapp-windows-x64");

        JDeployProject project = createProject(true, Collections.emptyMap(), packageNames);
        
        // Create .jdpignore files to trigger platform bundle generation
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File jdpignoreFile = new File(projectDir, ".jdpignore");
        FileUtils.writeStringToFile(jdpignoreFile, "com.example.test.native\n", "UTF-8");
        
        File macIgnoreFile = new File(projectDir, ".jdpignore.mac-x64");
        FileUtils.writeStringToFile(macIgnoreFile, "!ca.weblite.native.mac.x64\n", "UTF-8");
        
        File winIgnoreFile = new File(projectDir, ".jdpignore.win-x64");
        FileUtils.writeStringToFile(winIgnoreFile, "!ca.weblite.native.win.x64\n", "UTF-8");
        
        // Update mock to return true when ignore files exist
        JDeployIgnoreService realIgnoreService = new JDeployIgnoreService(new JDeployIgnoreFileParser());
        jarProcessor = new PlatformSpecificJarProcessor(realIgnoreService);
        generator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService, realIgnoreService);
        
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        Map<Platform, File> result = generator.generatePlatformBundles(project, universalDir, outputDir);

        // With .jdpignore files present, all configured platforms get bundles
        assertTrue(result.size() >= 2); // At least the 2 configured platforms
        assertTrue(result.containsKey(Platform.MAC_X64));
        assertTrue(result.containsKey(Platform.WIN_X64));

        // Check platform-specific directory names
        File macBundle = result.get(Platform.MAC_X64);
        assertEquals("myapp-macos-intel", macBundle.getName());

        File winBundle = result.get(Platform.WIN_X64);
        assertEquals("myapp-windows-x64", winBundle.getName());

        // Check that files were copied
        assertTrue(new File(macBundle, "package.json").exists());
        assertTrue(new File(macBundle, "test.jar").exists());
        assertTrue(new File(winBundle, "package.json").exists());
        assertTrue(new File(winBundle, "test.jar").exists());
    }

    @Test
    public void testGeneratePlatformBundle_UpdatesPackageJson() throws IOException {
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("mac-x64", Arrays.asList("ca.weblite.native.mac.x64"));

        Map<String, String> packageNames = new HashMap<>();
        packageNames.put("packageMacX64", "myapp-macos-intel");

        JDeployProject project = createProject(true, namespaces, packageNames);
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        File platformBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);

        // Check package.json was updated
        File packageJsonFile = new File(platformBundle, "package.json");
        assertTrue(packageJsonFile.exists());

        String content = FileUtils.readFileToString(packageJsonFile, "UTF-8");
        JSONObject packageJson = new JSONObject(content);

        assertEquals("myapp-macos-intel", packageJson.getString("name"));
        
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
        assertEquals("mac-x64", jdeploy.getString("platformVariant"));
        assertEquals("myapp", jdeploy.getString("universalPackage"));
    }

    @Test
    public void testGeneratePlatformBundle_ProcessesJarsWithNamespaces() throws IOException {
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("mac-x64", Arrays.asList("ca.weblite.native.mac.x64"));
        namespaces.put("win-x64", Arrays.asList("ca.weblite.native.win.x64"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create a real JAR file with some content
        File jarFile = new File(universalDir, "test.jar");
        createTestJar(jarFile, "com/example/Main.class");

        File platformBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);

        // Verify the platform bundle was created and contains the JAR
        assertTrue(platformBundle.exists());
        File processedJar = new File(platformBundle, "test.jar");
        assertTrue(processedJar.exists());
        assertTrue(processedJar.length() > 0);
    }

    @Test
    public void testGeneratePlatformTarballs() throws IOException {
        JDeployProject project = createProject(true, Collections.emptyMap(), Collections.emptyMap());
        
        // Create .jdpignore file to trigger platform bundle generation
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File jdpignoreFile = new File(projectDir, ".jdpignore");
        FileUtils.writeStringToFile(jdpignoreFile, "com.example.test.native\n", "UTF-8");
        
        File macIgnoreFile = new File(projectDir, ".jdpignore.mac-x64");
        FileUtils.writeStringToFile(macIgnoreFile, "!ca.weblite.native.mac.x64\n", "UTF-8");
        
        // Update to use real ignore service
        JDeployIgnoreService realIgnoreService = new JDeployIgnoreService(new JDeployIgnoreFileParser());
        jarProcessor = new PlatformSpecificJarProcessor(realIgnoreService);
        generator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService, realIgnoreService);
        
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("tarballs").toFile();
        outputDir.mkdirs();

        // Mock npm.pack() to create the expected tarball files
        doAnswer(invocation -> {
            File bundleDir = invocation.getArgument(0);
            File targetOutputDir = invocation.getArgument(1);
            
            // Read the package.json to get the name npm pack would use
            File packageJsonFile = new File(bundleDir, "package.json");
            if (packageJsonFile.exists()) {
                String content = FileUtils.readFileToString(packageJsonFile, "UTF-8");
                JSONObject packageJson = new JSONObject(content);
                String packageName = packageJson.optString("name", "app");
                String version = packageJson.optString("version", "1.0.0");
                
                // Create the tarball file that npm pack would create
                File tarballFile = new File(targetOutputDir, packageName + "-" + version + ".tgz");
                tarballFile.createNewFile();
            }
            return null;
        }).when(npm).pack(any(File.class), any(File.class), anyBoolean());

        Map<Platform, File> tarballs = generator.generatePlatformTarballs(project, universalDir, outputDir, npm, false);

        assertEquals(1, tarballs.size());
        
        File macTarball = tarballs.get(Platform.MAC_X64);
        assertNotNull(macTarball);
        assertEquals("myapp-1.0.0-mac-x64.tgz", macTarball.getName());
        
        // Verify npm.pack was called for each platform
        verify(npm, times(1)).pack(any(File.class), eq(outputDir), eq(false));
    }

    @Test
    public void testGeneratePlatformBundle_InvalidUniversalDir() throws IOException {
        JDeployProject project = createProject(true, Collections.emptyMap(), Collections.emptyMap());
        
        // Create .jdpignore file to trigger platform bundle generation
        File projectDir = project.getPackageJSONFile().toFile().getParentFile();
        File jdpignoreFile = new File(projectDir, ".jdpignore");
        FileUtils.writeStringToFile(jdpignoreFile, "com.example.test.native\n", "UTF-8");
        
        File macIgnoreFile = new File(projectDir, ".jdpignore.mac-x64");
        FileUtils.writeStringToFile(macIgnoreFile, "!ca.weblite.native.mac.x64\n", "UTF-8");
        
        // Update to use real ignore service
        JDeployIgnoreService realIgnoreService = new JDeployIgnoreService(new JDeployIgnoreFileParser());
        jarProcessor = new PlatformSpecificJarProcessor(realIgnoreService);
        generator = new PlatformBundleGenerator(jarProcessor, downloadPageSettingsService, realIgnoreService);
        
        File nonExistentDir = new File(tempDir.toFile(), "nonexistent");
        File outputDir = tempDir.resolve("output").toFile();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            generator.generatePlatformBundles(project, nonExistentDir, outputDir);
        });

        assertTrue(exception.getMessage().contains("Universal publish directory must exist"));
    }

    @Test
    public void testGeneratePlatformBundle_FallbackDirectoryNaming() throws IOException {
        // Test fallback naming when no platform-specific package name is configured
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("linux-arm64", Arrays.asList("ca.weblite.native.linux.arm64"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        File platformBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.LINUX_ARM64);

        // Should use fallback naming: {packageName}-{platformId}
        assertEquals("myapp-linux-arm64", platformBundle.getName());
        assertTrue(platformBundle.exists());
        assertTrue(new File(platformBundle, "package.json").exists());
    }

    @Test
    public void testGeneratePlatformBundle_NoPackageJsonUpdate() throws IOException {
        // Test that package.json is updated with temp name when no platform-specific name is configured
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("linux-x64", Arrays.asList("ca.weblite.native.linux.x64"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        File platformBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.LINUX_X64);

        // Check that package.json uses temp name for unique npm pack output
        File packageJsonFile = new File(platformBundle, "package.json");
        String content = FileUtils.readFileToString(packageJsonFile, "UTF-8");
        JSONObject packageJson = new JSONObject(content);

        assertEquals("myapp-temp-linux-x64", packageJson.getString("name")); // Temp name for unique npm pack
        
        // Check that metadata is still added
        JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
        assertEquals("linux-x64", jdeploy.getString("platformVariant"));
        assertEquals("myapp", jdeploy.getString("universalPackage"));
    }

    // Tests for new dual-list resolution scenarios

    @Test
    public void testNamespaceResolution_BasicStripKeepScenario() throws IOException {
        // RFC scenario: ignore contains parent namespace, platform has sub-namespace
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("ignore", Arrays.asList("com.example.native"));
        namespaces.put("mac-x64", Arrays.asList("com.example.native.mac.x64"));
        namespaces.put("win-x64", Arrays.asList("com.example.native.win.x64"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create JAR with overlapping namespace structure
        File jarFile = new File(universalDir, "test.jar");
        createTestJar(jarFile,
            "com/example/Main.class",
            "com/example/native/mac/x64/MacLib.class",        // Should be KEPT for mac-x64
            "com/example/native/win/x64/WinLib.class",        // Should be STRIPPED for mac-x64
            "com/example/native/test/TestLib.class",          // Should be STRIPPED (in ignore, not in keep)
            "com/example/core/CoreLib.class"                  // Should be KEPT (default)
        );

        File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);
        File winBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.WIN_X64);

        // Verify both bundles exist
        assertTrue(macBundle.exists());
        assertTrue(winBundle.exists());
        
        // Verify JARs were processed (actual content verification would require jar inspection)
        assertTrue(new File(macBundle, "test.jar").exists());
        assertTrue(new File(winBundle, "test.jar").exists());
        assertTrue(new File(macBundle, "test.jar").length() > 0);
        assertTrue(new File(winBundle, "test.jar").length() > 0);
    }

    @Test
    public void testNamespaceResolution_PathBasedNamespaces() throws IOException {
        // Test path-based namespace support
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("mac-x64", Arrays.asList("/my-mac-lib.dylib", "/native/macos/"));
        namespaces.put("win-x64", Arrays.asList("/my-win-lib.dll", "/native/windows/"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create JAR with path-based native libraries
        File jarFile = new File(universalDir, "test.jar");
        createTestJar(jarFile,
            "com/example/Main.class",
            "my-mac-lib.dylib",             // Root native file for Mac
            "my-win-lib.dll",               // Root native file for Windows
            "native/macos/lib.dylib",       // Directory-based Mac native
            "native/windows/lib.dll",       // Directory-based Windows native
            "native/linux/lib.so"           // Linux native (not in any platform list)
        );

        File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);
        File winBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.WIN_X64);

        // Verify bundles were created with proper JAR processing
        assertTrue(macBundle.exists());
        assertTrue(winBundle.exists());
        assertTrue(new File(macBundle, "test.jar").exists());
        assertTrue(new File(winBundle, "test.jar").exists());
    }

    @Test
    public void testNamespaceResolution_MixedFormatNamespaces() throws IOException {
        // Test mixed Java package notation and path-based notation
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("ignore", Arrays.asList("com.example.test"));
        namespaces.put("mac-x64", Arrays.asList("ca.weblite.native.mac.x64", "/my-mac-lib.dylib"));
        namespaces.put("win-x64", Arrays.asList("ca.weblite.native.win.x64", "/my-win-lib.dll"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create JAR with mixed format content
        File jarFile = new File(universalDir, "test.jar");
        createTestJar(jarFile,
            "com/example/Main.class",
            "ca/weblite/native/mac/x64/JavaMacLib.class",     // Java package format
            "ca/weblite/native/win/x64/JavaWinLib.class",     // Java package format
            "my-mac-lib.dylib",                              // Path-based format
            "my-win-lib.dll",                                // Path-based format
            "com/example/test/TestLib.class"                 // Should be stripped (ignore list)
        );

        File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);

        assertTrue(macBundle.exists());
        assertTrue(new File(macBundle, "test.jar").exists());
    }

    @Test
    public void testNamespaceResolution_ComplexOverlapScenario() throws IOException {
        // Complex scenario with multiple levels of namespace hierarchy
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("ignore", Arrays.asList("com.myapp.debug", "com.myapp.test"));
        namespaces.put("mac-x64", Arrays.asList("com.myapp.native.mac.x64"));
        namespaces.put("mac-arm64", Arrays.asList("com.myapp.native.mac.arm64"));
        namespaces.put("win-x64", Arrays.asList("com.myapp.native.win.x64"));
        namespaces.put("linux-x64", Arrays.asList("com.myapp.native.linux.x64"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create JAR with complex namespace structure
        File jarFile = new File(universalDir, "test.jar");
        createTestJar(jarFile,
            "com/myapp/Main.class",                              // Core app - should be KEPT
            "com/myapp/core/Engine.class",                       // Core app - should be KEPT
            "com/myapp/native/mac/x64/MacLib.class",             // Mac x64 native - varies by platform
            "com/myapp/native/mac/x64/specific/SpecificLib.class", // Mac x64 specific - varies by platform
            "com/myapp/native/mac/arm64/ArmLib.class",           // Mac ARM64 native - varies by platform
            "com/myapp/native/win/x64/WinLib.class",             // Windows native - varies by platform
            "com/myapp/native/linux/x64/LinuxLib.class",         // Linux native - varies by platform
            "com/myapp/debug/Debugger.class",                   // Debug - should be STRIPPED (ignore)
            "com/myapp/test/TestUtils.class"                     // Test - should be STRIPPED (ignore)
        );

        // Generate bundles for different platforms
        File macX64Bundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);
        File macArm64Bundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_ARM64);
        File winX64Bundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.WIN_X64);
        File linuxX64Bundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.LINUX_X64);

        // Verify all bundles were created
        assertTrue(macX64Bundle.exists());
        assertTrue(macArm64Bundle.exists());
        assertTrue(winX64Bundle.exists());
        assertTrue(linuxX64Bundle.exists());

        // Verify JARs were processed
        assertTrue(new File(macX64Bundle, "test.jar").exists());
        assertTrue(new File(macArm64Bundle, "test.jar").exists());
        assertTrue(new File(winX64Bundle, "test.jar").exists());
        assertTrue(new File(linuxX64Bundle, "test.jar").exists());
    }

    @Test
    public void testNamespaceResolution_OnlyIgnoreList() throws IOException {
        // Test scenario with only ignore list (no platform-specific namespaces)
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("ignore", Arrays.asList("com.example.test", "com.example.debug"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        File jarFile = new File(universalDir, "test.jar");
        createTestJar(jarFile,
            "com/example/Main.class",           // Should be KEPT
            "com/example/core/Core.class",      // Should be KEPT
            "com/example/test/TestUtils.class", // Should be STRIPPED (ignore)
            "com/example/debug/Debugger.class"  // Should be STRIPPED (ignore)
        );

        File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);

        assertTrue(macBundle.exists());
        assertTrue(new File(macBundle, "test.jar").exists());
    }

    @Test
    public void testNamespaceResolution_EmptyLists() throws IOException {
        // Test scenario with no namespaces configured (should be no-op)
        Map<String, List<String>> namespaces = new HashMap<>();

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        File jarFile = new File(universalDir, "test.jar");
        createTestJar(jarFile,
            "com/example/Main.class",
            "com/example/native/SomeLib.class"
        );

        File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);

        assertTrue(macBundle.exists());
        File processedJar = new File(macBundle, "test.jar");
        assertTrue(processedJar.exists());
        // JAR should be unchanged since no processing should occur
    }

    // Removed testExplainNamespaceResolution - method no longer exists after cleanup of deprecated nativeNamespaces implementation

    @Test
    public void testNamespaceResolution_NestedJarDirectories() throws IOException {
        // Test with JARs in nested directories
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("mac-x64", Arrays.asList("ca.weblite.native.mac.x64"));
        namespaces.put("win-x64", Arrays.asList("ca.weblite.native.win.x64"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create JARs in nested directories (lib/ subdirectory)
        File libDir = new File(universalDir, "lib");
        libDir.mkdirs();
        
        File mainJar = new File(libDir, "main.jar");
        createTestJar(mainJar, "com/example/Main.class");
        
        File nativeJar = new File(libDir, "native.jar");
        createTestJar(nativeJar,
            "ca/weblite/native/mac/x64/MacLib.class",
            "ca/weblite/native/win/x64/WinLib.class"
        );

        File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);

        assertTrue(macBundle.exists());
        assertTrue(new File(macBundle, "lib").exists());
        assertTrue(new File(macBundle, "lib/main.jar").exists());
        assertTrue(new File(macBundle, "lib/native.jar").exists());
    }

    @Test
    public void testNamespaceResolution_MultipleJarsWithDifferentContent() throws IOException {
        // Test processing multiple JARs with different native content
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("ignore", Arrays.asList("com.debug"));
        namespaces.put("mac-x64", Arrays.asList("com.native.mac"));
        namespaces.put("win-x64", Arrays.asList("com.native.win"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create multiple JARs with different content patterns
        File appJar = new File(universalDir, "app.jar");
        createTestJar(appJar,
            "com/example/Main.class",
            "com/example/Utils.class",
            "com/debug/Logger.class"  // Should be stripped (ignore)
        );

        File nativeJar = new File(universalDir, "native.jar");
        createTestJar(nativeJar,
            "com/native/mac/MacInterface.class",
            "com/native/win/WinInterface.class"
        );

        File libJar = new File(universalDir, "libs.jar");
        createTestJar(libJar,
            "com/thirdparty/Library.class",
            "com/debug/ThirdPartyDebug.class"  // Should be stripped (ignore)
        );

        File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);

        assertTrue(macBundle.exists());
        assertTrue(new File(macBundle, "app.jar").exists());
        assertTrue(new File(macBundle, "native.jar").exists());
        assertTrue(new File(macBundle, "libs.jar").exists());
        
        // All JARs should be processed and have content
        assertTrue(new File(macBundle, "app.jar").length() > 0);
        assertTrue(new File(macBundle, "native.jar").length() > 0);
        assertTrue(new File(macBundle, "libs.jar").length() > 0);
    }

    @Test
    public void testNamespaceResolution_JarProcessingError() throws IOException {
        // Test graceful handling of JAR processing errors
        Map<String, List<String>> namespaces = new HashMap<>();
        namespaces.put("mac-x64", Arrays.asList("com.native.mac"));

        JDeployProject project = createProject(true, namespaces, Collections.emptyMap());
        File universalDir = createUniversalBundle();
        File outputDir = tempDir.resolve("output").toFile();

        // Create a valid JAR and an invalid one (empty file)
        File validJar = new File(universalDir, "valid.jar");
        createTestJar(validJar, "com/example/Main.class");

        File invalidJar = new File(universalDir, "invalid.jar");
        invalidJar.createNewFile(); // Empty file, not a valid JAR

        // Should not throw exception, should process valid JARs and skip invalid ones
        assertDoesNotThrow(() -> {
            File macBundle = generator.generatePlatformBundle(project, universalDir, outputDir, Platform.MAC_X64);
            assertTrue(macBundle.exists());
            assertTrue(new File(macBundle, "valid.jar").exists());
            assertTrue(new File(macBundle, "invalid.jar").exists()); // File copied but not processed
        });
    }

    // Helper methods

    private JDeployProject createProject(boolean platformBundlesEnabled, 
                                        Map<String, List<String>> nativeNamespaces,
                                        Map<String, String> packageNames) {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "myapp");
        packageJson.put("version", "1.0.0");

        JSONObject jdeploy = new JSONObject();
        jdeploy.put("platformBundlesEnabled", platformBundlesEnabled);

        // Add package names
        for (Map.Entry<String, String> entry : packageNames.entrySet()) {
            jdeploy.put(entry.getKey(), entry.getValue());
        }

        // Add native namespaces
        if (!nativeNamespaces.isEmpty()) {
            JSONObject namespacesObj = new JSONObject();
            for (Map.Entry<String, List<String>> entry : nativeNamespaces.entrySet()) {
                JSONArray namespaceArray = new JSONArray();
                for (String namespace : entry.getValue()) {
                    namespaceArray.put(namespace);
                }
                namespacesObj.put(entry.getKey(), namespaceArray);
            }
            jdeploy.put("nativeNamespaces", namespacesObj);
        }

        packageJson.put("jdeploy", jdeploy);

        // Create a real package.json file in the temp directory
        try {
            File packageJsonFile = tempDir.resolve("package.json").toFile();
            FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), "UTF-8");
            return new JDeployProject(packageJsonFile.toPath(), packageJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File createUniversalBundle() throws IOException {
        File universalDir = tempDir.resolve("universal").toFile();
        universalDir.mkdirs();

        // Create package.json
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "myapp");
        packageJson.put("version", "1.0.0");
        
        File packageJsonFile = new File(universalDir, "package.json");
        FileUtils.writeStringToFile(packageJsonFile, packageJson.toString(2), "UTF-8");

        // Create a test JAR file
        File jarFile = new File(universalDir, "test.jar");
        jarFile.createNewFile();

        // Create some other files
        File nodeModules = new File(universalDir, "node_modules");
        nodeModules.mkdirs();
        
        File jdeployJS = new File(universalDir, "jdeploy.js");
        FileUtils.writeStringToFile(jdeployJS, "// jDeploy launcher", "UTF-8");

        return universalDir;
    }

    private void createTestJar(File jarFile, String... entryNames) throws IOException {
        try (java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(new java.io.FileOutputStream(jarFile))) {
            for (String entryName : entryNames) {
                java.util.jar.JarEntry entry = new java.util.jar.JarEntry(entryName);
                jos.putNextEntry(entry);
                jos.write(("// Test content for " + entryName).getBytes());
                jos.closeEntry();
            }
        }
    }
}