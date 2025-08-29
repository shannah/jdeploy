package ca.weblite.jdeploy.publishing.github;

import ca.weblite.jdeploy.JDeploy;
import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.factories.CheerpjServiceFactory;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.*;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.PackageNameService;
import ca.weblite.jdeploy.services.CheerpjService;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.services.ProjectBuilderService;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// Disable on Windows because of issues with file locking and deletion
public class GitHubPublishDriverIntegrationTest {

    private File tempDir;
    private File jarFile;
    private GitHubPublishDriver githubDriver;
    private BasePublishDriver baseDriver;
    private PackageService packageService;
    private PublishingContext publishingContext;
    private PublishTargetInterface target;
    private BundlerSettings bundlerSettings;
    private DownloadPageSettingsService downloadPageSettingsService;

    @Before
    public void setup() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().contains("win"));
        // Create a temporary directory for testing
        tempDir = Files.createTempDirectory("github-publish-driver-test").toFile();
        
        // Create a mock JAR file similar to TextEditor project
        File targetDir = new File(tempDir, "target");
        targetDir.mkdirs();
        jarFile = new File(targetDir, "test-app-1.0-SNAPSHOT.jar");
        
        // Create a simple JAR file with a manifest
        java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(
            new java.io.FileOutputStream(jarFile)
        );
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(java.util.jar.Attributes.Name.MAIN_CLASS, "com.example.TestApp");
        jos.putNextEntry(new java.util.zip.ZipEntry("META-INF/MANIFEST.MF"));
        manifest.write(jos);
        jos.closeEntry();
        jos.close();
        
        // Create icon.png
        FileUtils.copyInputStreamToFile(
            JDeploy.class.getResourceAsStream("icon.png"), 
            new File(targetDir, "icon.png")
        );
        
        // Create real PackageService with mocked dependencies
        Environment environment = mock(Environment.class);
        JarFinder jarFinder = mock(JarFinder.class);
        ClassPathFinder classPathFinder = mock(ClassPathFinder.class);
        CompressionService compressionService = mock(CompressionService.class);
        BundleCodeService bundleCodeService = mock(BundleCodeService.class);
        CopyJarRuleBuilder copyJarRuleBuilder = mock(CopyJarRuleBuilder.class);
        ProjectBuilderService projectBuilderService = mock(ProjectBuilderService.class);
        PackagingConfig packagingConfig = mock(PackagingConfig.class);
        
        // Configure mocks
        when(jarFinder.findJarFile(any())).thenReturn(jarFile);
        when(copyJarRuleBuilder.build(any(), any())).thenReturn(java.util.Collections.emptyList());
        when(projectBuilderService.isBuildSupported(any())).thenReturn(false);
        when(packagingConfig.getJdeployRegistry()).thenReturn("https://npm.jdeploy.com");
        when(compressionService.compress(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        
        packageService = new PackageService(
            environment,
            jarFinder,
            classPathFinder,
            compressionService,
            bundleCodeService,
            copyJarRuleBuilder,
            projectBuilderService,
            packagingConfig
        );
        
        // Create BasePublishDriver that uses our real PackageService
        baseDriver = mock(BasePublishDriver.class);
        doAnswer(invocation -> {
            PublishingContext ctx = invocation.getArgument(0);
            PublishTargetInterface target = invocation.getArgument(1);
            BundlerSettings settings = invocation.getArgument(2);
            
            // Use real PackageService to create installers
            packageService.allInstallers(ctx.packagingContext, settings);
            return null;
        }).when(baseDriver).makePackage(any(), any(), any());
        
        // Mock baseDriver.prepare() to set up publish directory structure
        doAnswer(invocation -> {
            PublishingContext ctx = invocation.getArgument(0);
            // Create the publish directory structure that GitHubPublishDriver.prepare() expects
            File publishDir = ctx.getPublishDir();
            publishDir.mkdirs();
            // Copy package.json to publish directory (this is what BasePublishDriver.prepare() does)
            FileUtils.copyFile(ctx.packagingContext.packageJsonFile, ctx.getPublishPackageJsonFile());
            return null;
        }).when(baseDriver).prepare(any(), any(), any());
        
        // Mock other dependencies for GitHubPublishDriver
        PackageNameService packageNameService = mock(PackageNameService.class);
        CheerpjServiceFactory cheerpjServiceFactory = mock(CheerpjServiceFactory.class);
        CheerpjService cheerpjService = mock(CheerpjService.class);
        GitHubReleaseCreator gitHubReleaseCreator = mock(GitHubReleaseCreator.class);
        downloadPageSettingsService = mock(DownloadPageSettingsService.class);
        
        when(cheerpjServiceFactory.create(any())).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);
        when(packageNameService.getFullPackageName(any(), any())).thenReturn("test-app");
        
        githubDriver = new GitHubPublishDriver(
            baseDriver,
            bundleCodeService,
            packageNameService,
            cheerpjServiceFactory,
            gitHubReleaseCreator,
            downloadPageSettingsService
        );
        
        // Setup test target
        target = mock(PublishTargetInterface.class);
        when(target.getUrl()).thenReturn("https://github.com/test/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        
        bundlerSettings = new BundlerSettings();
    }
    
    @After
    public void cleanup() throws Exception {
        if (tempDir != null && tempDir.exists()) {
            FileUtils.deleteDirectory(tempDir);
        }
    }
    
    private PublishingContext createPublishingContext(boolean generateLegacyBundles) throws Exception {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("version", "1.0.0");
        
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);
        
        jdeploy.put("jar", "target/test-app-1.0-SNAPSHOT.jar");
        jdeploy.put("javaVersion", "11");
        jdeploy.put("javafx", true);
        jdeploy.put("title", "Test App");
        jdeploy.put("generateLegacyBundles", generateLegacyBundles);
        
        File packageJsonFile = new File(tempDir, "package.json");
        FileUtils.write(packageJsonFile, packageJSON.toString(), "UTF-8");
        
        PackagingContext packagingContext = new PackagingContext.Builder()
                .directory(tempDir)
                .packageJsonFile(packageJsonFile)
                .out(System.out)
                .err(System.err)
                .build();
        
        NPM npm = mock(NPM.class);
        // Mock npm.pack() to do nothing - we're not testing NPM functionality
        doNothing().when(npm).pack(any(File.class), any(File.class), anyBoolean());
        
        return new PublishingContext(
                packagingContext,
                false, // alwaysPackageOnPublish
                npm,
                "fake-github-token",
                null,  // githubRepository
                null,  // githubRefName
                null,  // githubRefType
                null   // distTag
        );
    }
    
    private PublishingContext createPublishingContextWithoutGenerateLegacyBundles() throws Exception {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("version", "1.0.0");
        
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);
        
        jdeploy.put("jar", "target/test-app-1.0-SNAPSHOT.jar");
        jdeploy.put("javaVersion", "11");
        jdeploy.put("javafx", true);
        jdeploy.put("title", "Test App");
        // generateLegacyBundles not specified - should default to true
        
        File packageJsonFile = new File(tempDir, "package.json");
        FileUtils.write(packageJsonFile, packageJSON.toString(), "UTF-8");
        
        PackagingContext packagingContext = new PackagingContext.Builder()
                .directory(tempDir)
                .packageJsonFile(packageJsonFile)
                .out(System.out)
                .err(System.err)
                .build();
        
        NPM npm = mock(NPM.class);
        // Mock npm.pack() to do nothing - we're not testing NPM functionality
        doNothing().when(npm).pack(any(File.class), any(File.class), anyBoolean());
        
        return new PublishingContext(
                packagingContext,
                false, // alwaysPackageOnPublish
                npm,
                "fake-github-token",
                null,  // githubRepository
                null,  // githubRefName
                null,  // githubRefType
                null   // distTag
        );
    }
    
    private DownloadPageSettings createDownloadPageSettings() {
        DownloadPageSettings settings = new DownloadPageSettings();
        settings.setEnabledPlatforms(new HashSet<>(Arrays.asList(
            DownloadPageSettings.BundlePlatform.WindowsX64,
            DownloadPageSettings.BundlePlatform.LinuxX64
        )));
        return settings;
    }
    
    @Test
    public void testGenerateLegacyBundlesEnabledInGitHubReleaseFiles() throws Exception {
        publishingContext = createPublishingContext(true);
        
        // Mock download page settings to request Windows and Linux installers
        DownloadPageSettings settings = createDownloadPageSettings();
        when(downloadPageSettingsService.read(any(File.class))).thenReturn(settings);
        
        // Verify that generateLegacyBundles is enabled
        assertTrue("generateLegacyBundles should be enabled", publishingContext.packagingContext.isGenerateLegacyBundles());
        
        // Call makePackage to create installers, then prepare to copy them to release files
        githubDriver.makePackage(publishingContext, target, bundlerSettings);
        githubDriver.prepare(publishingContext, target, bundlerSettings);
        
        File githubReleaseFilesDir = publishingContext.getGithubReleaseFilesDir();
        assertTrue("GitHub release files directory should exist", githubReleaseFilesDir.exists());
        
        // Check for installer files in the GitHub release directory - both new-style and legacy should exist
        File[] releaseFiles = githubReleaseFilesDir.listFiles((dir, name) -> 
            name.contains("win") || name.contains("linux")
        );
        
        assertNotNull("Should have release files", releaseFiles);
        assertTrue("Should have installer files in GitHub release directory", releaseFiles.length > 0);
        
        // Check for both win-x64 and win (legacy) installer files in release directory
        boolean hasWinX64 = Arrays.stream(releaseFiles).anyMatch(f -> f.getName().contains("win-x64"));
        boolean hasWinLegacy = Arrays.stream(releaseFiles).anyMatch(f -> 
            f.getName().contains("win") && !f.getName().contains("win-x64") && !f.getName().contains("win-arm64")
        );
        
        // Check for both linux-x64 and linux (legacy) installer files in release directory
        boolean hasLinuxX64 = Arrays.stream(releaseFiles).anyMatch(f -> f.getName().contains("linux-x64"));
        boolean hasLinuxLegacy = Arrays.stream(releaseFiles).anyMatch(f -> 
            f.getName().contains("linux") && !f.getName().contains("linux-x64") && !f.getName().contains("linux-arm64")
        );
        
        assertTrue("Should have win-x64 installer in GitHub release files when legacy bundles enabled", hasWinX64);
        assertTrue("Should have win legacy installer in GitHub release files when legacy bundles enabled", hasWinLegacy);
        assertTrue("Should have linux-x64 installer in GitHub release files when legacy bundles enabled", hasLinuxX64);
        assertTrue("Should have linux legacy installer in GitHub release files when legacy bundles enabled", hasLinuxLegacy);
    }
    
    @Test
    public void testGenerateLegacyBundlesDisabledInGitHubReleaseFiles() throws Exception {
        publishingContext = createPublishingContext(false);
        
        // Mock download page settings to request Windows and Linux installers
        DownloadPageSettings settings = createDownloadPageSettings();
        when(downloadPageSettingsService.read(any(File.class))).thenReturn(settings);
        
        // Verify that generateLegacyBundles is disabled
        assertFalse("generateLegacyBundles should be disabled", publishingContext.packagingContext.isGenerateLegacyBundles());
        
        // Call makePackage to create installers, then prepare to copy them to release files
        githubDriver.makePackage(publishingContext, target, bundlerSettings);
        githubDriver.prepare(publishingContext, target, bundlerSettings);
        
        File githubReleaseFilesDir = publishingContext.getGithubReleaseFilesDir();
        assertTrue("GitHub release files directory should exist", githubReleaseFilesDir.exists());
        
        // Check for installer files in the GitHub release directory
        File[] releaseFiles = githubReleaseFilesDir.listFiles((dir, name) -> 
            name.contains("win") || name.contains("linux")
        );
        
        assertNotNull("Should have release files", releaseFiles);
        assertTrue("Should have installer files in GitHub release directory", releaseFiles.length > 0);
        
        // Check for win-x64 installer files in release directory (should exist)
        boolean hasWinX64 = Arrays.stream(releaseFiles).anyMatch(f -> f.getName().contains("win-x64"));
        // Check for win legacy installer files in release directory (should NOT exist)
        boolean hasWinLegacy = Arrays.stream(releaseFiles).anyMatch(f -> 
            f.getName().contains("win") && !f.getName().contains("win-x64") && !f.getName().contains("win-arm64")
        );
        
        // Check for linux-x64 installer files in release directory (should exist)
        boolean hasLinuxX64 = Arrays.stream(releaseFiles).anyMatch(f -> f.getName().contains("linux-x64"));
        // Check for linux legacy installer files in release directory (should NOT exist)
        boolean hasLinuxLegacy = Arrays.stream(releaseFiles).anyMatch(f -> 
            f.getName().contains("linux") && !f.getName().contains("linux-x64") && !f.getName().contains("linux-arm64")
        );
        
        assertTrue("Should have win-x64 installer in GitHub release files when legacy bundles disabled", hasWinX64);
        assertFalse("Should NOT have win legacy installer in GitHub release files when legacy bundles disabled", hasWinLegacy);
        assertTrue("Should have linux-x64 installer in GitHub release files when legacy bundles disabled", hasLinuxX64);
        assertFalse("Should NOT have linux legacy installer in GitHub release files when legacy bundles disabled", hasLinuxLegacy);
    }
    
    @Test
    public void testGenerateLegacyBundlesDefaultValueInGitHubReleaseFiles() throws Exception {
        publishingContext = createPublishingContextWithoutGenerateLegacyBundles();
        
        // Mock download page settings to request Windows and Linux installers
        DownloadPageSettings settings = createDownloadPageSettings();
        when(downloadPageSettingsService.read(any(File.class))).thenReturn(settings);
        
        // Verify that generateLegacyBundles defaults to true when not specified
        assertTrue("generateLegacyBundles should default to true", publishingContext.packagingContext.isGenerateLegacyBundles());
        
        // Call makePackage to create installers, then prepare to copy them to release files
        githubDriver.makePackage(publishingContext, target, bundlerSettings);
        githubDriver.prepare(publishingContext, target, bundlerSettings);
        
        File githubReleaseFilesDir = publishingContext.getGithubReleaseFilesDir();
        assertTrue("GitHub release files directory should exist", githubReleaseFilesDir.exists());
        
        // Check for installer files in the GitHub release directory - both new-style and legacy should exist
        File[] releaseFiles = githubReleaseFilesDir.listFiles((dir, name) -> 
            name.contains("win") || name.contains("linux")
        );
        
        assertNotNull("Should have release files", releaseFiles);
        assertTrue("Should have installer files in GitHub release directory", releaseFiles.length > 0);
        
        // Check for both win-x64 and win (legacy) installer files in release directory
        boolean hasWinX64 = Arrays.stream(releaseFiles).anyMatch(f -> f.getName().contains("win-x64"));
        boolean hasWinLegacy = Arrays.stream(releaseFiles).anyMatch(f -> 
            f.getName().contains("win") && !f.getName().contains("win-x64") && !f.getName().contains("win-arm64")
        );
        
        // Check for both linux-x64 and linux (legacy) installer files in release directory
        boolean hasLinuxX64 = Arrays.stream(releaseFiles).anyMatch(f -> f.getName().contains("linux-x64"));
        boolean hasLinuxLegacy = Arrays.stream(releaseFiles).anyMatch(f -> 
            f.getName().contains("linux") && !f.getName().contains("linux-x64") && !f.getName().contains("linux-arm64")
        );
        
        assertTrue("Should have win-x64 installer in GitHub release files when legacy bundles default to enabled", hasWinX64);
        assertTrue("Should have win legacy installer in GitHub release files when legacy bundles default to enabled", hasWinLegacy);
        assertTrue("Should have linux-x64 installer in GitHub release files when legacy bundles default to enabled", hasLinuxX64);
        assertTrue("Should have linux legacy installer in GitHub release files when legacy bundles default to enabled", hasLinuxLegacy);
    }
}