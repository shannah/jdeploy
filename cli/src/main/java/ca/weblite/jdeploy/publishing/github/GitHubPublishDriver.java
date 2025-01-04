package ca.weblite.jdeploy.publishing.github;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.factories.CheerpjServiceFactory;
import ca.weblite.jdeploy.helpers.GithubReleaseNotesMutator;
import ca.weblite.jdeploy.helpers.PackageInfoBuilder;
import ca.weblite.jdeploy.helpers.PrereleaseHelper;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
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

@Singleton
public class GitHubPublishDriver implements PublishDriverInterface {

    private static final String GITHUB_URL = "https://github.com/";

    private final PublishDriverInterface baseDriver;

    private final BundleCodeService bundleCodeService;

    private final PackageNameService packageNameService;

    private final CheerpjServiceFactory cheerpjServiceFactory;
    @Inject
    public GitHubPublishDriver(
            BasePublishDriver baseDriver,
            BundleCodeService bundleCodeService,
            PackageNameService packageNameService,
            CheerpjServiceFactory cheerpjServiceFactory
    ) {
        this.baseDriver = baseDriver;
        this.bundleCodeService = bundleCodeService;
        this.packageNameService = packageNameService;
        this.cheerpjServiceFactory = cheerpjServiceFactory;
    }

    @Override
    public void publish(PublishingContext context, PublishTargetInterface target) throws IOException {

    }

    @Override
    public void prepare(
            PublishingContext context,
            PublishTargetInterface target,
            BundlerSettings bundlerSettings
    ) throws IOException {

        baseDriver.prepare(context, target, bundlerSettings);
        context.getGithubReleaseFilesDir().mkdirs();
        context.npm.pack(
                context.getPublishDir(),
                context.getGithubReleaseFilesDir(),
                context.packagingContext.exitOnFail
        );
        saveGithubReleaseFiles(context);
        PackageInfoBuilder builder = new PackageInfoBuilder();
        InputStream oldPackageInfo = loadPackageInfo(context, target);
        if (oldPackageInfo != null) {
            builder.load(oldPackageInfo);
        } else {
            builder.setCreatedTime();
        }
        builder.setModifiedTime();
        String version = context.packagingContext.getString("version", "");
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
                        context.packagingContext.getString("name", "")
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


    private String createGithubReleaseNotes(PublishingContext context) {
        return new GithubReleaseNotesMutator(context.directory(), context.err()).createGithubReleaseNotes();
    }

    private void saveGithubReleaseFiles(PublishingContext context) throws IOException {
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
            for (File installerFile : installerFiles.listFiles()) {
                FileUtils.copyFile(installerFile, new File(releaseFilesDir, installerFile.getName().replace(' ', '.')));

            }
        }

        final String releaseNotes = createGithubReleaseNotes(context);
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
}
