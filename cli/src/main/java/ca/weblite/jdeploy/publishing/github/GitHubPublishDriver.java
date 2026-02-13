package ca.weblite.jdeploy.publishing.github;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettings;
import ca.weblite.jdeploy.downloadPage.DownloadPageSettingsService;
import ca.weblite.jdeploy.environment.Environment;
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
import ca.weblite.jdeploy.services.PrebuiltAppRequirementService;
import ca.weblite.jdeploy.services.PrebuiltAppPackager;
import ca.weblite.jdeploy.services.PrebuiltAppBundlerService;
import ca.weblite.jdeploy.models.JDeployProject;
import ca.weblite.jdeploy.models.Platform;
import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import ca.weblite.tools.io.URLUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

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

    private final Environment environment;

    private final ca.weblite.jdeploy.services.JDeployFilesZipGenerator jdeployFilesZipGenerator;

    private final PrebuiltAppRequirementService prebuiltAppRequirementService;

    private final PrebuiltAppPackager prebuiltAppPackager;

    private final PrebuiltAppBundlerService prebuiltAppBundlerService;

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
            JDeployProjectFactory projectFactory,
            Environment environment,
            ca.weblite.jdeploy.services.JDeployFilesZipGenerator jdeployFilesZipGenerator,
            PrebuiltAppRequirementService prebuiltAppRequirementService,
            PrebuiltAppPackager prebuiltAppPackager,
            PrebuiltAppBundlerService prebuiltAppBundlerService
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
        this.environment = environment;
        this.jdeployFilesZipGenerator = jdeployFilesZipGenerator;
        this.prebuiltAppRequirementService = prebuiltAppRequirementService;
        this.prebuiltAppPackager = prebuiltAppPackager;
        this.prebuiltAppBundlerService = prebuiltAppBundlerService;
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
        File packageInfo2 = new File(releaseFiles, "package-info-2.json");

        // Step 1: Download current package-info.json from jdeploy tag (optimistic lock baseline)
        GitHubReleaseCreator.AssetWithETag baseline = null;
        boolean isFirstPublish = false;

        try {
            baseline = gitHubReleaseCreator.downloadAssetWithETag(
                    repositoryUrl,
                    githubToken,
                    "jdeploy",
                    "package-info.json"
            );
            context.out().println("Downloaded baseline package-info.json from jdeploy tag (ETag: " + baseline.getETag() + ")");
        } catch (GitHubReleaseNotFoundException e) {
            // Case A: jdeploy tag/release doesn't exist - first publish
            isFirstPublish = true;
            context.out().println("No existing jdeploy tag found - this is the first publish");
        } catch (GitHubAssetNotFoundException e) {
            // Case B: jdeploy release exists, but package-info.json is missing - FAIL FAST
            throw new IOException(
                    "The jdeploy release exists but package-info.json is missing. " +
                            "This indicates a corrupted or incomplete publish state. " +
                            "To fix this, delete the jdeploy release from GitHub and retry:\n" +
                            "  1. Go to: " + repositoryUrl + "/releases\n" +
                            "  2. Delete the 'jdeploy' release (NOT the tag)\n" +
                            "  3. Run: jdeploy publish",
                    e
            );
        } catch (IOException e) {
            // Case D: Network error, 403, 500, etc. - FAIL FAST
            throw new IOException(
                    "Failed to check for existing package-info.json in jdeploy tag. " +
                            "Cannot proceed safely. Please check network and GitHub access. " +
                            "Error: " + e.getMessage(),
                    e
            );
        }

        // Step 2: Create version-specific GitHub release with all files
        gitHubReleaseCreator.createRelease(
                repositoryUrl,
                githubToken,
                releaseTag,
                releaseNotes,
                releaseFiles.listFiles()
        );
        context.out().println("✓ Created version-specific release: " + releaseTag);

        // Step 3: Copy package-info.json to package-info-2.json for backup
        FileUtils.copyFile(packageInfo, packageInfo2);

        // Step 4: Update 'jdeploy' tag with optimistic lock + Sequential A/B strategy
        try {
            if (isFirstPublish) {
                // Case A: Atomic create - fails if release already exists (concurrent publish)
                context.out().println("Creating jdeploy tag with package-info.json and package-info-2.json...");
                gitHubReleaseCreator.createReleaseAtomic(
                        repositoryUrl,
                        githubToken,
                        "jdeploy",
                        "Release metadata for jDeploy releases",
                        new File[]{packageInfo, packageInfo2}
                );
                context.out().println("✓ Created jdeploy tag with metadata files");
            } else {
                // Case C: Update with optimistic lock (fails if ETag doesn't match)
                context.out().println("Updating jdeploy tag with new package-info.json...");
                GitHubReleaseCreator.ReleaseResponse jdeployRelease = gitHubReleaseCreator.fetchReleaseDetails(
                        repositoryUrl,
                        githubToken,
                        "jdeploy"
                );

                // Upload package-info.json FIRST with optimistic lock
                gitHubReleaseCreator.uploadArtifactConditional(
                        jdeployRelease,
                        githubToken,
                        packageInfo,
                        baseline.getETag()
                );
                context.out().println("✓ Updated package-info.json in jdeploy tag");

                // Upload package-info-2.json SECOND (backup file, no lock check)
                gitHubReleaseCreator.uploadArtifacts(
                        jdeployRelease,
                        githubToken,
                        new File[]{packageInfo2},
                        true
                );
                context.out().println("✓ Updated package-info-2.json in jdeploy tag (backup)");
            }
        } catch (GitHubReleaseAlreadyExistsException e) {
            // Case A: Another process created the jdeploy release concurrently
            throw new IOException(
                    "Concurrent publish detected during first publish. " +
                            "Another publish process created the jdeploy release while this publish was running. " +
                            "The version-specific release (v" + releaseTag + ") was created successfully. " +
                            "Please retry 'jdeploy publish' to update the jdeploy tag.",
                    e
            );
        } catch (ConcurrentPublishException e) {
            // Case C: Another publish modified jdeploy tag since we started
            throw new IOException(
                    "Concurrent publish detected. The jdeploy tag was modified during publish. " +
                            "The version-specific release (v" + releaseTag + ") was created successfully. " +
                            "Please retry 'jdeploy publish' to update the jdeploy tag.",
                    e
            );
        }

        context.out().println("\n✓ GitHub publish completed successfully!");
        context.out().println("  Release: " + repositoryUrl + "/releases/tag/" + releaseTag);
        context.out().println("  Users can now install directly from GitHub releases!");
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

        // Generate prebuilt app tarballs if signing is enabled
        generatePrebuiltApps(context, bundlerSettings);

        // Generate jdeploy-files.zip for GitHub releases
        jdeployFilesZipGenerator.generate(context, target);

        PackageInfoBuilder builder = new PackageInfoBuilder();
        InputStream oldPackageInfo = loadPackageInfo(context, target); // May throw IOException now
        if (oldPackageInfo != null) {
            try {
                builder.load(oldPackageInfo);
                context.out().println("Loaded existing package-info.json with version history");
            } catch (Exception ex) {
                throw new IOException(
                    "CRITICAL: Failed to parse existing package-info.json. " +
                    "The file exists but is corrupted or invalid. " +
                    "Cannot proceed as this would cause data loss.",
                    ex
                );
            }
        } else {
            // Only reached if jdeploy tag doesn't exist and strict mode is off
            context.out().println("Creating new package-info.json for first release");
            builder.setCreatedTime();
        }
        builder.setModifiedTime();
        String version = context.packagingContext.getVersion();
        builder.setVersionTimestamp(version);
        builder.addVersion(version, Files.newInputStream(context.getPublishPackageJsonFile().toPath()));
        if (!PrereleaseHelper.isPrereleaseVersion(version)) {
            builder.setLatestVersion(version);
        }

        File packageInfoFile = new File(context.getGithubReleaseFilesDir(), "package-info.json");
        builder.save(Files.newOutputStream(packageInfoFile.toPath()));

        // Verify the saved file
        verifyPackageInfoIntegrity(context, packageInfoFile, version, oldPackageInfo != null);
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
        // Check if the app has CLI commands defined
        boolean hasCommands = false;
        try {
            JDeployProject project = projectFactory.createProject(context.packagingContext.packageJsonFile.toPath());
            hasCommands = !project.getCommandSpecs().isEmpty();
        } catch (Exception e) {
            // If we can't load the project, assume no commands
            context.err().println("Warning: Could not check for CLI commands: " + e.getMessage());
        }

        String version = context.packagingContext.getVersion();

        return new GithubReleaseNotesMutator(context.directory(), context.err()).createGithubReleaseNotes(
                getRepository(context, target),
                getRefName(context, target),
                getRefType(context, target),
                hasCommands,
                version
        );
    }

    private void saveGithubReleaseFiles(PublishingContext context, PublishTargetInterface target) throws IOException {
        File icon = new File(context.directory(), "icon.png");
        File installSplash = new File(context.directory(),"installsplash.png");
        File launcherSplash = new File(context.directory(),"launcher-splash.html");
        File releaseFilesDir = context.getGithubReleaseFilesDir();
        releaseFilesDir.mkdirs();
        if (icon.exists()) {
            FileUtils.copyFile(icon, new File(releaseFilesDir, icon.getName()));

        }
        if (installSplash.exists()) {
            FileUtils.copyFile(installSplash, new File(releaseFilesDir, installSplash.getName()));
        }
        if (launcherSplash.exists()) {
            FileUtils.copyFile(launcherSplash, new File(releaseFilesDir, launcherSplash.getName()));
        }

        // Copy package.json for version-specific metadata (lighter than package-info.json)
        File packageJson = context.getPublishPackageJsonFile();
        if (packageJson.exists()) {
            FileUtils.copyFile(packageJson, new File(releaseFilesDir, "package.json"));
            context.out().println("Added package.json to release files");
        } else {
            context.out().println("Warning: package.json not found at " + packageJson);
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

    private InputStream loadPackageInfo(PublishingContext context, PublishTargetInterface target) throws IOException {
        String packageInfoUrl = target.getUrl() + "/releases/download/jdeploy/package-info.json";

        // First check if jdeploy tag exists
        boolean jdeployTagExists = checkJdeployTagExists(context, target);

        int maxRetries = 3;
        int retryDelayMs = 2000;
        IOException lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                InputStream stream = URLUtil.openStream(new URL(packageInfoUrl));
                // Validate that we can parse it as JSON
                validatePackageInfoJson(stream);
                // Re-open stream since validation consumed it
                return URLUtil.openStream(new URL(packageInfoUrl));
            } catch (IOException ex) {
                lastException = ex;
                if (attempt < maxRetries) {
                    context.out().println(
                        "Attempt " + attempt + "/" + maxRetries +
                        " failed to load package-info.json from " + packageInfoUrl +
                        ", retrying in " + retryDelayMs + "ms... Error: " + ex.getMessage()
                    );
                    try {
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 2; // Exponential backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("Interrupted while retrying package-info.json load", ie);
                    }
                }
            }
        }

        // All retries exhausted
        if (jdeployTagExists) {
            // jdeploy tag exists but we can't load package-info.json - CRITICAL ERROR
            throw new IOException(
                "CRITICAL: The 'jdeploy' tag exists in the repository but package-info.json " +
                "could not be retrieved or parsed after " + maxRetries + " attempts at " +
                packageInfoUrl + ". This indicates a serious issue. " +
                "Cannot proceed as this would cause data loss. " +
                "Please investigate the jdeploy release and its assets. " +
                "Last error: " + (lastException != null ? lastException.getMessage() : "unknown"),
                lastException
            );
        }

        // Check if strict mode is enabled (require jdeploy tag to exist)
        boolean requireExistingTag = "true".equals(environment.get("JDEPLOY_REQUIRE_EXISTING_TAG"));
        if (requireExistingTag) {
            throw new IOException(
                "CRITICAL: jdeploy tag does not exist but JDEPLOY_REQUIRE_EXISTING_TAG is set to true. " +
                "This project requires an existing jdeploy tag. " +
                "If this is the first release, set require_existing_jdeploy_tag: 'false' in your workflow.",
                lastException
            );
        }

        // jdeploy tag doesn't exist - this is likely the first release
        context.out().println(
            "jdeploy tag not found at " + packageInfoUrl +
            ". This appears to be the first release, creating new package-info.json"
        );
        return null;
    }

    private boolean checkJdeployTagExists(PublishingContext context, PublishTargetInterface target) {
        try {
            String tagsUrl = target.getUrl() + "/releases/tags/jdeploy";
            HttpURLConnection conn = (HttpURLConnection) new URL(tagsUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            return responseCode == 200;
        } catch (Exception ex) {
            context.out().println(
                "Could not check if jdeploy tag exists (assuming it doesn't): " + ex.getMessage()
            );
            return false;
        }
    }

    private void validatePackageInfoJson(InputStream stream) throws IOException {
        try {
            String content = IOUtil.readToString(stream);
            JSONObject json = new JSONObject(content);

            // Validate required fields
            if (!json.has("name")) {
                throw new IOException("package-info.json is missing required 'name' field");
            }
            if (!json.has("versions")) {
                throw new IOException("package-info.json is missing required 'versions' field");
            }

            // Basic structure validation
            json.getJSONObject("versions"); // Will throw if not an object

        } catch (org.json.JSONException ex) {
            throw new IOException("package-info.json is not valid JSON: " + ex.getMessage(), ex);
        }
    }

    private void verifyPackageInfoIntegrity(
        PublishingContext context,
        File packageInfoFile,
        String currentVersion,
        boolean hadPreviousVersions
    ) throws IOException {
        try {
            String content = FileUtils.readFileToString(packageInfoFile, StandardCharsets.UTF_8);
            JSONObject json = new JSONObject(content);

            // Verify current version was added
            if (!json.getJSONObject("versions").has(currentVersion)) {
                throw new IOException(
                    "Verification failed: package-info.json is missing the current version " + currentVersion
                );
            }

            context.out().println(
                "Verification passed: package-info.json contains " +
                json.getJSONObject("versions").length() + " version(s)"
            );

        } catch (org.json.JSONException ex) {
            throw new IOException(
                "Verification failed: Generated package-info.json is invalid JSON",
                ex
            );
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

    /**
     * Generates prebuilt app tarballs for platforms that require them.
     * Prebuilt apps are native bundles (exe, .app) packaged as tarballs with a -bin suffix.
     * This is typically used when Windows signing is enabled.
     *
     * @param context the publishing context
     * @param bundlerSettings the bundler settings from the prepare phase
     */
    private void generatePrebuiltApps(PublishingContext context, BundlerSettings bundlerSettings) {
        try {
            // Load the project to check if prebuilt apps are needed
            JDeployProject project = projectFactory.createProject(context.packagingContext.packageJsonFile.toPath());

            // Check if prebuilt apps generation is enabled
            if (!prebuiltAppRequirementService.isPrebuiltAppsEnabled(project)) {
                context.out().println("Prebuilt apps not enabled, skipping native bundle generation");
                return;
            }

            // Get the platforms that need prebuilt apps
            List<Platform> requiredPlatforms = prebuiltAppRequirementService.getRequiredPlatforms(project);
            if (requiredPlatforms.isEmpty()) {
                context.out().println("No platforms require prebuilt apps");
                return;
            }

            context.out().println("Generating prebuilt apps for " + requiredPlatforms.size() + " platform(s)...");

            // Create AppInfo for the bundler
            ca.weblite.jdeploy.app.AppInfo appInfo = createAppInfoForPrebuiltApps(context, project);

            // Create temp directory for bundle generation
            File tempBundleDir = new File(context.getGithubReleaseFilesDir(), "prebuilt-temp");
            if (tempBundleDir.exists()) {
                FileUtils.deleteDirectory(tempBundleDir);
            }
            tempBundleDir.mkdirs();

            try {
                // Generate native bundles for each required platform
                Map<Platform, File> nativeBundles = prebuiltAppBundlerService.generateNativeBundles(
                        context,
                        bundlerSettings,
                        appInfo,
                        requiredPlatforms,
                        tempBundleDir
                );

                // Package each bundle into a tarball and move to release files dir
                JSONObject packageJson = project.getPackageJSON();
                String appName = packageJson.optString("name", "app");
                String version = packageJson.optString("version", "1.0.0");
                List<String> successfulPlatforms = new java.util.ArrayList<>();

                for (Map.Entry<Platform, File> entry : nativeBundles.entrySet()) {
                    Platform platform = entry.getKey();
                    File bundleDir = entry.getValue();

                    try {
                        // Create tarball using the packager
                        File tarball = prebuiltAppPackager.packageNativeBundleWithoutNpm(
                                bundleDir,
                                appName,
                                version,
                                platform,
                                context.getGithubReleaseFilesDir()
                        );

                        // Generate checksum
                        String checksum = prebuiltAppPackager.generateChecksum(tarball);

                        context.out().println("Created prebuilt app tarball: " + tarball.getName() +
                                " (SHA-256: " + checksum.substring(0, 12) + "...)");
                        successfulPlatforms.add(platform.getIdentifier());

                    } catch (Exception e) {
                        context.err().println("Warning: Failed to package prebuilt app for " +
                                platform.getIdentifier() + ": " + e.getMessage());
                    }
                }

                if (!successfulPlatforms.isEmpty()) {
                    context.out().println("Successfully created " + successfulPlatforms.size() + " prebuilt app tarball(s)");

                    // Embed the successful platforms into the publish package.json
                    embedPrebuiltAppsList(context, successfulPlatforms);
                } else if (!nativeBundles.isEmpty()) {
                    context.err().println("Warning: No prebuilt app tarballs were created successfully");
                }

            } finally {
                // Clean up temp directory
                if (tempBundleDir.exists()) {
                    FileUtils.deleteDirectory(tempBundleDir);
                }
            }

        } catch (Exception e) {
            // Log the error but don't fail the entire publishing process
            context.err().println("Warning: Failed to generate prebuilt apps: " + e.getMessage());
            context.err().println("The standard bundle will still be published to GitHub release");
            e.printStackTrace(context.err());
        }
    }

    /**
     * Creates an AppInfo instance populated with the necessary information for prebuilt app bundling.
     *
     * @param context the publishing context
     * @param project the jDeploy project
     * @return the populated AppInfo
     * @throws IOException if loading app info fails
     */
    private ca.weblite.jdeploy.app.AppInfo createAppInfoForPrebuiltApps(
            PublishingContext context,
            JDeployProject project
    ) throws IOException {
        ca.weblite.jdeploy.app.AppInfo appInfo = new ca.weblite.jdeploy.app.AppInfo();

        // Get values from package.json
        JSONObject packageJson = project.getPackageJSON();
        String name = packageJson.optString("name", "app");
        String version = packageJson.optString("version", "1.0.0");

        // Get display name from jdeploy config or package name
        String displayName = name;
        if (packageJson.has("jdeploy")) {
            JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
            displayName = jdeploy.optString("displayName", jdeploy.optString("title", name));
        }

        // Set basic package info
        appInfo.setNpmPackage(name);
        appInfo.setNpmVersion(version);
        appInfo.setTitle(displayName);

        // Set the app URL to the publish directory (where the jar and icons are)
        File publishDir = context.getPublishDir();
        appInfo.setAppURL(publishDir.toURI().toURL());

        return appInfo;
    }

    /**
     * Embeds the list of successfully generated prebuilt app platforms into the publish package.json.
     * This allows installers to know which platforms have prebuilt apps available.
     *
     * @param context the publishing context
     * @param platforms the list of platform identifiers (e.g., "win-x64", "mac-arm64")
     * @throws IOException if updating package.json fails
     */
    private void embedPrebuiltAppsList(PublishingContext context, List<String> platforms) throws IOException {
        // Load the package.json from the publish directory
        File publishPackageJson = context.getPublishPackageJsonFile();
        if (!publishPackageJson.exists()) {
            context.err().println("Warning: Could not embed prebuiltApps - package.json not found at " +
                    publishPackageJson.getAbsolutePath());
            return;
        }

        String content = FileUtils.readFileToString(publishPackageJson, StandardCharsets.UTF_8);
        JSONObject packageJson = new JSONObject(content);

        // Get or create the jdeploy object
        JSONObject jdeploy;
        if (packageJson.has("jdeploy")) {
            jdeploy = packageJson.getJSONObject("jdeploy");
        } else {
            jdeploy = new JSONObject();
            packageJson.put("jdeploy", jdeploy);
        }

        // Add the prebuiltApps list
        org.json.JSONArray prebuiltApps = new org.json.JSONArray();
        for (String platform : platforms) {
            prebuiltApps.put(platform);
        }
        jdeploy.put("prebuiltApps", prebuiltApps);

        // Save the updated package.json
        FileUtils.writeStringToFile(publishPackageJson, packageJson.toString(2), StandardCharsets.UTF_8);
        context.out().println("Embedded prebuiltApps list into package.json: " + platforms);

        // Also update the package.json in the release files directory (for version-specific metadata)
        File releasePackageJson = new File(context.getGithubReleaseFilesDir(), "package.json");
        if (releasePackageJson.exists()) {
            FileUtils.writeStringToFile(releasePackageJson, packageJson.toString(2), StandardCharsets.UTF_8);
        }
    }
}
