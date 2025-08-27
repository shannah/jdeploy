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
import java.util.List;
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

    @Inject
    public GitHubPublishDriver(
            BasePublishDriver baseDriver,
            BundleCodeService bundleCodeService,
            PackageNameService packageNameService,
            CheerpjServiceFactory cheerpjServiceFactory,
            GitHubReleaseCreator gitHubReleaseCreator,
            DownloadPageSettingsService downloadPageSettingsService
    ) {
        this.baseDriver = baseDriver;
        this.bundleCodeService = bundleCodeService;
        this.packageNameService = packageNameService;
        this.cheerpjServiceFactory = cheerpjServiceFactory;
        this.gitHubReleaseCreator = gitHubReleaseCreator;
        this.downloadPageSettingsService = downloadPageSettingsService;
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
        builder.addVersion(version, new FileInputStream(context.getPublishPackageJsonFile()));
        if (!PrereleaseHelper.isPrereleaseVersion(version)) {
            builder.setLatestVersion(version);
        }
        builder.save(
                new FileOutputStream(new File(context.getGithubReleaseFilesDir(),
                        "package-info.json")
                )
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

    private String getPackageUrl(PublishTargetInterface target) throws UnsupportedEncodingException {
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
}
