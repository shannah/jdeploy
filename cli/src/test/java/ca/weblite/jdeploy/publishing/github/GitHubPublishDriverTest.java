package ca.weblite.jdeploy.publishing.github;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.factories.CheerpjServiceFactory;
import ca.weblite.jdeploy.npm.NPM;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.CheerpjService;
import ca.weblite.jdeploy.services.PackageNameService;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// Disable tests on Windows due to file path issues with some tests
@ExtendWith(MockitoExtension.class)
@DisabledOnOs(OS.WINDOWS)
class GitHubPublishDriverTest {

    @Mock
    private BasePublishDriver baseDriver;
    
    @Mock
    private BundleCodeService bundleCodeService;
    
    @Mock
    private PackageNameService packageNameService;
    
    @Mock
    private CheerpjServiceFactory cheerpjServiceFactory;
    
    @Mock
    private GitHubReleaseCreator gitHubReleaseCreator;
    
    @Mock
    private DownloadPageSettingsService downloadPageSettingsService;
    
    @Mock
    private PublishTargetInterface target;
    
    @Mock
    private BundlerSettings bundlerSettings;
    
    @Mock
    private CheerpjService cheerpjService;
    
    @Mock
    private OneTimePasswordProviderInterface otpProvider;

    @TempDir
    File tempDir;

    private GitHubPublishDriver driver;
    private File packageJsonFile;
    private File releaseFilesDir;
    private PackagingContext packagingContext;
    private PublishingContext publishingContext;
    private NPM npm;
    private PrintStream out;
    private PrintStream err;

    @BeforeEach
    void setUp() throws IOException {
        driver = new GitHubPublishDriver(
                baseDriver,
                bundleCodeService,
                packageNameService,
                cheerpjServiceFactory,
                gitHubReleaseCreator,
                downloadPageSettingsService
        );

        packageJsonFile = new File(tempDir, "package.json");
        releaseFilesDir = new File(tempDir, "release-files");
        releaseFilesDir.mkdirs();

        // Create real objects instead of mocking final fields
        Map<String, Object> packageJsonMap = new HashMap<>();
        packageJsonMap.put("name", "test-app");
        packageJsonMap.put("version", "1.0.0");
        
        out = new PrintStream(System.out);
        err = new PrintStream(System.err);
        npm = mock(NPM.class);

        packagingContext = new PackagingContext(
                tempDir,
                packageJsonMap,
                packageJsonFile,
                false, // alwaysClean
                false, // doNotStripJavaFXFiles
                null,  // bundlesOverride
                null,  // installersOverride
                null,  // keyProvider
                null,  // packageSigningService  
                out,
                err,
                System.in,
                true,  // exitOnFail
                false  // isBuildRequired
        );

        publishingContext = new PublishingContext(
                packagingContext,
                false, // alwaysPackageOnPublish
                npm,
                null,  // githubToken
                null,  // githubRepository
                null,  // githubRefName
                null,  // githubRefType
                null   // distTag
        );
    }

    private DownloadPageSettings createDownloadPageSettings(DownloadPageSettings.BundlePlatform... platforms) {
        DownloadPageSettings settings = new DownloadPageSettings();
        Set<DownloadPageSettings.BundlePlatform> platformSet = new HashSet<>(Arrays.asList(platforms));
        settings.setEnabledPlatforms(platformSet);
        return settings;
    }

    @Test
    @DisplayName("Should process single platform successfully")
    void shouldProcessSinglePlatformSuccessfully() throws IOException {
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        
        DownloadPageSettings settings = createDownloadPageSettings(DownloadPageSettings.BundlePlatform.MacX64);
        when(downloadPageSettingsService.read(packageJsonFile)).thenReturn(settings);

        driver.makePackage(publishingContext, target, bundlerSettings);

        verify(baseDriver).makePackage(any(PublishingContext.class), eq(target), eq(bundlerSettings));
        verify(bundlerSettings).setSource("https://github.com/user/repo");
        verify(bundlerSettings).setCompressBundles(true);
        verify(bundlerSettings).setDoNotZipExeInstaller(true);
    }

    @Test
    @DisplayName("Should process multiple platforms successfully")
    void shouldProcessMultiplePlatformsSuccessfully() throws IOException {
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        
        DownloadPageSettings settings = createDownloadPageSettings(
                DownloadPageSettings.BundlePlatform.MacX64,
                DownloadPageSettings.BundlePlatform.WindowsX64,
                DownloadPageSettings.BundlePlatform.LinuxX64
        );
        when(downloadPageSettingsService.read(packageJsonFile)).thenReturn(settings);

        driver.makePackage(publishingContext, target, bundlerSettings);

        verify(baseDriver).makePackage(any(PublishingContext.class), eq(target), eq(bundlerSettings));
        verify(bundlerSettings).setSource("https://github.com/user/repo");
    }

    @Test
    @DisplayName("Should throw exception when no supported platforms are selected")
    void shouldThrowExceptionWhenNoSupportedPlatformsSelected() throws IOException {
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        
        DownloadPageSettings settings = createDownloadPageSettings(
                DownloadPageSettings.BundlePlatform.MacHighSierra,
                DownloadPageSettings.BundlePlatform.DebianX64
        );
        when(downloadPageSettingsService.read(packageJsonFile)).thenReturn(settings);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            driver.makePackage(publishingContext, target, bundlerSettings);
        });

        assertEquals("No installers found for the selected platforms. " +
                "Please ensure that your package.json has the correct downloadPageSettings.", 
                exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when target is not GitHub")
    void shouldThrowExceptionWhenTargetIsNotGitHub() throws IOException {
        when(target.getType()).thenReturn(PublishTargetType.NPM);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            driver.makePackage(publishingContext, target, bundlerSettings);
        });

        assertEquals("prepare-github-release requires the source to be a github repository.", 
                exception.getMessage());
    }

    @Test
    @DisplayName("Should handle All platform by processing all supported platforms")
    void shouldHandleAllPlatformByProcessingAllSupportedPlatforms() throws IOException {
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        
        DownloadPageSettings settings = createDownloadPageSettings(DownloadPageSettings.BundlePlatform.All);
        when(downloadPageSettingsService.read(packageJsonFile)).thenReturn(settings);

        driver.makePackage(publishingContext, target, bundlerSettings);

        verify(baseDriver).makePackage(any(PublishingContext.class), eq(target), eq(bundlerSettings));
        verify(bundlerSettings).setSource("https://github.com/user/repo");
    }

    @Test
    @DisplayName("Should filter out unsupported platforms")
    void shouldFilterOutUnsupportedPlatforms() throws IOException {
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        when(target.getType()).thenReturn(PublishTargetType.GITHUB);
        
        DownloadPageSettings settings = createDownloadPageSettings(
                DownloadPageSettings.BundlePlatform.MacX64,
                DownloadPageSettings.BundlePlatform.MacHighSierra, // unsupported
                DownloadPageSettings.BundlePlatform.WindowsX64,
                DownloadPageSettings.BundlePlatform.DebianX64 // unsupported
        );
        when(downloadPageSettingsService.read(packageJsonFile)).thenReturn(settings);

        // Should not throw exception since there are supported platforms
        assertDoesNotThrow(() -> {
            driver.makePackage(publishingContext, target, bundlerSettings);
        });

        verify(baseDriver).makePackage(any(PublishingContext.class), eq(target), eq(bundlerSettings));
    }

    @Test
    @DisplayName("Should publish with valid GitHub token")
    void shouldPublishWithValidGitHubToken() throws IOException {
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        
        PublishingContext contextWithToken = new PublishingContext(
                packagingContext, false, npm, "valid-token", null, null, null, null);

        // Create actual release files instead of trying to mock listFiles()
        File packageInfoFile = new File(releaseFilesDir, "package-info.json");
        FileUtils.writeStringToFile(packageInfoFile, "{\"name\":\"test-package\"}", StandardCharsets.UTF_8);

        File releaseNotes = new File(releaseFilesDir, "jdeploy-release-notes.md");
        FileUtils.writeStringToFile(releaseNotes, "Release notes", StandardCharsets.UTF_8);

        PublishingContext spiedContext = spy(contextWithToken);
        when(spiedContext.getGithubReleaseFilesDir()).thenReturn(releaseFilesDir);

        driver.publish(spiedContext, target, otpProvider);

        verify(gitHubReleaseCreator).createRelease(
                eq("https://github.com/user/repo"),
                eq("valid-token"),
                eq("1.0.0"),
                eq(releaseNotes),
                any(File[].class)
        );
    }

    @Test
    @DisplayName("Should throw exception when GitHub token is missing")
    void shouldThrowExceptionWhenGitHubTokenIsMissing() throws IOException {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            driver.publish(publishingContext, target, otpProvider);
        });

        assertEquals("GitHub token is required for publishing to GitHub", exception.getMessage());
    }

    @Test
    @DisplayName("Should prepare release files and call base driver")
    void shouldPrepareReleaseFilesAndCallBaseDriver() throws IOException {
        when(target.getUrl()).thenReturn("https://github.com/user/repo");
        
        File publishDir = new File(tempDir, "publish");
        publishDir.mkdirs();

        // Create the proper directory structure that GithubReleaseNotesMutator expects
        // It looks for files in directory + "/jdeploy/github-release-files"
        File jdeployDir = new File(tempDir, "jdeploy");
        File githubReleaseDir = new File(jdeployDir, "github-release-files");
        githubReleaseDir.mkdirs();

        // Create some mock bundle files that the GithubReleaseNotesMutator expects
        File macBundle = new File(githubReleaseDir, "test-app-mac-x64.zip");
        File winBundle = new File(githubReleaseDir, "test-app-win-x64.zip");
        FileUtils.writeStringToFile(macBundle, "mock bundle", StandardCharsets.UTF_8);
        FileUtils.writeStringToFile(winBundle, "mock bundle", StandardCharsets.UTF_8);

        // Create the publish package.json file that PackageInfoBuilder.addVersion() expects
        File publishPackageJsonFile = new File(publishDir, "package.json");
        String packageJsonContent = "{\"name\":\"test-app\",\"version\":\"1.0.0\",\"jdeploy\":{}}";
        FileUtils.writeStringToFile(publishPackageJsonFile, packageJsonContent, StandardCharsets.UTF_8);

        // Create a context with GitHub repository information
        PublishingContext contextWithRepo = new PublishingContext(
                packagingContext, false, npm, null, "https://github.com/user/repo", null, null, null);

        PublishingContext spiedContext = spy(contextWithRepo);
        when(spiedContext.getPublishDir()).thenReturn(publishDir);
        when(spiedContext.getPublishPackageJsonFile()).thenReturn(publishPackageJsonFile);
        when(spiedContext.getGithubReleaseFilesDir()).thenReturn(githubReleaseDir);
        
        when(cheerpjServiceFactory.create(any(PackagingContext.class))).thenReturn(cheerpjService);
        when(cheerpjService.isEnabled()).thenReturn(false);
        when(packageNameService.getFullPackageName(target, "test-app")).thenReturn("full-package-name");

        driver.prepare(spiedContext, target, bundlerSettings);

        verify(baseDriver).prepare(spiedContext, target, bundlerSettings);
        verify(npm).pack(publishDir, githubReleaseDir, true);
        verify(bundleCodeService).fetchJdeployBundleCode("full-package-name");
    }

    @Test
    @DisplayName("Should check if version is published")
    void shouldCheckIfVersionIsPublished() throws IOException {
        JSONObject packageInfo = new JSONObject();
        JSONObject versions = new JSONObject();
        versions.put("1.0.0", new JSONObject());
        packageInfo.put("versions", versions);

        GitHubPublishDriver spyDriver = spy(driver);
        doReturn(packageInfo).when(spyDriver).fetchPackageInfoFromPublicationChannel("test-package", target);

        boolean isPublished = spyDriver.isVersionPublished("test-package", "1.0.0", target);
        assertTrue(isPublished);

        boolean isNotPublished = spyDriver.isVersionPublished("test-package", "2.0.0", target);
        assertFalse(isNotPublished);
    }

    @Test
    @DisplayName("Should return false when checking version fails")
    void shouldReturnFalseWhenCheckingVersionFails() throws IOException {
        GitHubPublishDriver spyDriver = spy(driver);
        doThrow(new IOException("Network error")).when(spyDriver)
                .fetchPackageInfoFromPublicationChannel("test-package", target);

        boolean isPublished = spyDriver.isVersionPublished("test-package", "1.0.0", target);
        assertFalse(isPublished);
    }

    @Test
    @DisplayName("Should validate GitHub URL format")
    void shouldValidateGitHubURLFormat() {
        when(target.getUrl()).thenReturn("https://bitbucket.org/user/repo");

        assertThrows(IllegalArgumentException.class, () -> {
            driver.fetchPackageInfoFromPublicationChannel("test-package", target);
        });
    }
}