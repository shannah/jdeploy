package ca.weblite.jdeploy.publishing.github;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.factories.CheerpjServiceFactory;
import ca.weblite.jdeploy.helpers.GithubReleaseNotesMutator;
import ca.weblite.jdeploy.helpers.PackageInfoBuilder;
import ca.weblite.jdeploy.helpers.PrereleaseHelper;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishDriverInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.CheerpjService;
import ca.weblite.jdeploy.services.PackageNameService;
import ca.weblite.jdeploy.services.PlatformBundleGenerator;
import ca.weblite.jdeploy.services.DefaultBundleService;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static ca.weblite.jdeploy.BundleConstants.*;

@Singleton
public class GitHubPublishDriver implements PublishDriverInterface {

    private static final String GITHUB_URL = "https://github.com/";

    private final PublishDriverInterface baseDriver;

    private final BundleCodeService bundleCodeService;

    private final PackageNameService packageNameService;

    private final CheerpjServiceFactory cheerpjServiceFactory;

    private GitHubReleaseCreator gitHubReleaseCreator;

    private final DownloadPageSettingsService downloadPageSettingsService;

    private final PlatformBundleGenerator platformBundleGenerator;
    
    private final DefaultBundleService defaultBundleService;
    
    private final JDeployProjectFactory projectFactory;

    @Inject
    public GitHubPublishDriver(
            BasePublishDriver baseDriver,
            BundleCodeService bundleCodeService,
            PackageNameService packageNameService,
            CheerpjServiceFactory cheerpjServiceFactory,
            GitHubReleaseCreator gitHubReleaseCreator,
            DownloadPageSettingsService downloadPageSettingsService,
            PlatformBundleGenerator platformBundleGenerator,
            DefaultBundleService defaultBundleService,
            JDeployProjectFactory projectFactory
    ) {
        this.baseDriver = baseDriver;
        this.bundleCodeService = bundleCodeService;
        this.packageNameService = packageNameService;
        this.cheerpjServiceFactory = cheerpjServiceFactory;
        this.gitHubReleaseCreator = gitHubReleaseCreator;
        this.downloadPageSettingsService = downloadPageSettingsService;
        this.platformBundleGenerator = platformBundleGenerator;
        this.defaultBundleService = defaultBundleService;
        this.projectFactory = projectFactory;
    }

    @Override
    public void publish(PublishingContext context,
                        PublishTargetInterface target,
                        OneTimePasswordProviderInterface otpProvider
    ) throws IOException {
        String githubToken = context.getGithubToken();
        if (githubToken == null) {
            throw new IllegalArgumentException("GitHub token is required for publishing to GitHub");
        }

        String repositoryUrl = target.getUrl(); // e.g. https://github.com/username/repo
        String releaseTag = context.packagingContext.getVersion();
        File releaseFiles = context.getGithubReleaseFilesDir();
        // jdeploy-release-notes.md
        File releaseNotes = new File(releaseFiles, "jdeploy-release-notes.md");
        File packageInfo = new File(releaseFiles, "package-info.json");

        gitHubReleaseCreator.createRelease(
                repositoryUrl,
                githubToken,
                releaseTag,
                releaseNotes,
                releaseFiles.listFiles()
        );

        GitHubReleaseCreator.ReleaseResponse releaseDetails = null;
        try {
            releaseDetails = gitHubReleaseCreator
                    .fetchReleaseDetails(
                            repositoryUrl,
                            githubToken,
                            "jdeploy"
                    );
            gitHubReleaseCreator.uploadArtifacts(
                    releaseDetails,
                    githubToken,
                    new File[]{packageInfo},
                    true
            );
        } catch (IOException ioe) {
            if (releaseDetails == null) {
                // Create a new release
                gitHubReleaseCreator.createRelease(
                        repositoryUrl,
                        githubToken,
                        "jdeploy",
                        "Release metadata for jDeploy releases",
                        new File[]{packageInfo}
                );
            }
        }
    }

    @Override
    public void prepare(
            PublishingContext context,
            PublishTargetInterface target,
            BundlerSettings bundlerSettings
    ) throws IOException {

        baseDriver.prepare(context, target, bundlerSettings);
        if (context.getGithubReleaseFilesDir().exists()) {
            FileUtils.deleteDirectory(context.getGithubReleaseFilesDir());
        }
        context.getGithubReleaseFilesDir().mkdirs();

        // Generate platform-specific tarballs if platform bundles are enabled
        generatePlatformSpecificTarballs(context);
        
        // Process the default bundle AFTER platform bundles to apply global ignore rules
        defaultBundleService.processDefaultBundle(context);
        context.npm.pack(
                context.getPublishDir(),
                context.getGithubReleaseFilesDir(),
                context.packagingContext.exitOnFail
        );

        saveGithubReleaseFiles(context, target);
        PackageInfoBuilder builder = new PackageInfoBuilder();
        InputStream oldPackageInfo = loadPackageInfo(context, target);
        if (oldPackageInfo != null) {
            builder.load(oldPackageInfo);
        } else {
            builder.setCreatedTime();
        }
        builder.setModifiedTime();
        String version = context.packagingContext.getVersion();
        builder.setVersionTimestamp(version);
        builder.addVersion(version, Files.newInputStream(context.getPublishPackageJsonFile().toPath()));
        if (!PrereleaseHelper.isPrereleaseVersion(version)) {
            builder.setLatestVersion(version);
        }
        builder.save(
                Files.newOutputStream(new File(context.getGithubReleaseFilesDir(),
                        "package-info.json").toPath())
        );
        // Trigger register of package name

        bundleCodeService.fetchJdeployBundleCode(
                packageNameService.getFullPackageName(
                        target,
                        context.packagingContext.getName()
                )
        );
        context.out().println("Release files created in " + context.getGithubReleaseFilesDir());
        CheerpjService cheerpjService = getCheerpjService(context);
        if (cheerpjService.isEnabled()) {
            context.out().println("CheerpJ detected, uploading to CheerpJ CDN...");
            cheerpjService.execute();
        }
    }

    @Override
    public void makePackage(
            PublishingContext context,
            PublishTargetInterface target,
            BundlerSettings bundlerSettings
    ) throws IOException {
        bundlerSettings.setSource(target.getUrl());

        if (target.getType() != PublishTargetType.GITHUB) {
            throw new IllegalArgumentException("prepare-github-release requires the source to be a github repository.");
        }

        bundlerSettings.setCompressBundles(true);
        bundlerSettings.setDoNotZipExeInstaller(true);
        baseDriver.makePackage(
                context.withPackagingContext(
                        context
                                .packagingContext
                                .withInstallers(
                                        getInstallers(context)
                                )
                        ),
                target,
                bundlerSettings
        );

    }

    @Override
    public JSONObject fetchPackageInfoFromPublicationChannel(String packageName, PublishTargetInterface target) throws IOException {
        URL u = new URL(getPackageUrl(target));
        HttpURLConnection conn = (HttpURLConnection)u.openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);
        if (conn.getResponseCode() != 200) {
            throw new IOException("Failed to fetch Package info for package "+packageName+". "+conn.getResponseMessage());
        }
        return new JSONObject(IOUtil.readToString(conn.getInputStream()));
    }

    @Override
    public boolean isVersionPublished(String packageName, String version, PublishTargetInterface target) {
        try {
            JSONObject jsonObject = fetchPackageInfoFromPublicationChannel(packageName, target);
            return jsonObject.has("versions") && jsonObject.getJSONObject("versions").has(version);
        } catch (Exception ex) {
            return false;
        }
    }

    private String getPackageUrl(PublishTargetInterface target) {
        if (!target.getUrl().startsWith(GITHUB_URL)) {
            throw new IllegalArgumentException(
                    "GitHub driver only supports target URLs starting with " + GITHUB_URL + " but received " +
                            target.getUrl() + " instead."
            );
        }
        return target.getUrl() + "/releases/download/jdeploy/package-info.json";
    }


    private String createGithubReleaseNotes(PublishingContext context, PublishTargetInterface target) {
        return new GithubReleaseNotesMutator(context.directory(), context.err()).createGithubReleaseNotes(
                getRepository(context, target),
                getRefName(context, target),
                getRefType(context, target)
        );
    }

    private void saveGithubReleaseFiles(PublishingContext context, PublishTargetInterface target) throws IOException {
        File icon = new File(context.directory(), "icon.png");
        File installSplash = new File(context.directory(),"installsplash.png");
        File releaseFilesDir = context.getGithubReleaseFilesDir();
        releaseFilesDir.mkdirs();
        if (icon.exists()) {
            FileUtils.copyFile(icon, new File(releaseFilesDir, icon.getName()));

        }
        if (installSplash.exists()) {
            FileUtils.copyFile(installSplash, new File(releaseFilesDir, installSplash.getName()));
        }

        File installerFiles = context.packagingContext.getInstallersDir();
        if (installerFiles.isDirectory()) {
            for (File installerFile : Objects.requireNonNull(installerFiles.listFiles())) {
                FileUtils.copyFile(installerFile, new File(releaseFilesDir, installerFile.getName().replace(' ', '.')));

            }
        }

        final String releaseNotes = createGithubReleaseNotes(context, target);
        FileUtil.writeStringToFile(releaseNotes, new File(releaseFilesDir, "jdeploy-release-notes.md"));

        context.out().println("Assets copied to " + releaseFilesDir);
    }

    private InputStream loadPackageInfo(PublishingContext context, PublishTargetInterface target) {
        String packageInfoUrl = target.getUrl() + "/releases/download/jdeploy/package-info.json";
        try {
            return URLUtil.openStream(new URL(packageInfoUrl));
        } catch (IOException ex) {
            context.out().println(
                    "Failed to open stream for existing package-info.json at " + packageInfoUrl +
                            ". Perhaps it doesn't exist yet"
            );
            return null;
        }
    }

    private CheerpjService getCheerpjService(PublishingContext context) throws IOException{
        return  cheerpjServiceFactory.create(context.packagingContext);
    }

    private String getRepository(PublishingContext context, PublishTargetInterface target) {
        if (context.githubRepository != null) {
            return context.githubRepository;
        }

        return target.getUrl().replace(GITHUB_URL, "");
    }

    private String getRefName(PublishingContext context, PublishTargetInterface target) {
        if (context.githubRefName != null) {
            return context.githubRefName;
        }

        return context.packagingContext.getVersion();
    }

    private String getRefType(PublishingContext context, PublishTargetInterface target) {
        if (context.githubRefType != null) {
            return context.githubRefType;
        }

        return "tag";
    }

    private String[] getInstallers(PublishingContext context) {
        DownloadPageSettings downloadPageSettings = downloadPageSettingsService.read(
                context.packagingContext.packageJsonFile
        );
        List<String> installers = downloadPageSettings.getResolvedPlatforms().stream().map(
                platform -> {
                    switch (platform) {
                        case MacX64:
                            return BUNDLE_MAC_X64;
                        case MacArm64:
                            return BUNDLE_MAC_ARM64;
                        case WindowsX64:
                            return BUNDLE_WIN;
                        case WindowsArm64:
                            return BUNDLE_WIN_ARM64;
                        case LinuxX64:
                            return BUNDLE_LINUX;
                        case LinuxArm64:
                            return BUNDLE_LINUX_ARM64;
                        case Default:
                        case All:
                        case MacHighSierra:
                        case DebianX64:
                        case DebianArm64:
                        default:
                            return "";
                    }
                }
        ).collect(Collectors.toList());
        installers.removeIf(String::isEmpty);
        if (installers.isEmpty()) {
            throw new IllegalArgumentException("No installers found for the selected platforms. " +
                    "Please ensure that your package.json has the correct downloadPageSettings.");
        }

        return installers.toArray(new String[0]);
    }

    /**
     * Generates platform-specific tarballs and processes the default bundle according to RFC rules.
     * 
     * Default Bundle Processing:
     * - Strip ignore namespaces (debugging, testing libraries) 
     * - Keep ALL platform-specific namespaces (users can choose platform at runtime)
     * 
     * Platform-Specific Bundles:
     * - Use dual-list resolution (strip ignore + other platforms, keep target platform)
     * 
     * Creates additional .tgz files alongside the processed universal tarball for GitHub releases.
     */
    private void generatePlatformSpecificTarballs(PublishingContext context) throws IOException {
        try {
            // Load the project configuration from package.json
            JDeployProject project = projectFactory.createProject(context.packagingContext.packageJsonFile.toPath());
            
            // Check if platform bundles are enabled and needed
            if (!platformBundleGenerator.shouldGeneratePlatformBundles(project)) {
                context.out().println("Platform bundles not enabled or not needed, skipping platform-specific processing");
                return;
            }
            
            // Note: Default bundle is already processed before packing in the prepare() method
            
            // Generate platform-specific tarballs
            List<Platform> platforms = platformBundleGenerator.getPlatformsForBundleGeneration(project);
            context.out().println("Generating platform-specific tarballs for " + platforms.size() + " platforms...");
            
            Map<Platform, File> tarballs = platformBundleGenerator.generatePlatformTarballs(
                    project,
                    context.getPublishDir(),
                    context.getGithubReleaseFilesDir(),
                    context.npm,
                    context.packagingContext.exitOnFail
            );
            
            // Log the generated tarballs
            for (Map.Entry<Platform, File> entry : tarballs.entrySet()) {
                Platform platform = entry.getKey();
                File tarball = entry.getValue();
                context.out().println("Generated platform-specific tarball: " + tarball.getName() + 
                                    " for " + platform.getIdentifier());
            }
            
            if (tarballs.isEmpty()) {
                context.out().println("No platform-specific tarballs were generated");
            } else {
                context.out().println("Successfully generated " + tarballs.size() + 
                                    " platform-specific tarballs for GitHub release");
            }
            
        } catch (Exception e) {
            // Log the error but don't fail the entire publishing process
            // The universal bundle will still be available
            context.err().println("Warning: Failed to generate platform-specific tarballs: " + e.getMessage());
            context.err().println("Universal bundle will still be published to GitHub release");
            e.printStackTrace(context.err());
        }
    }
}
