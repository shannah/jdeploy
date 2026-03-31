package ca.weblite.jdeploy.publishing.jpm;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.helpers.PackageInfoBuilder;
import ca.weblite.jdeploy.helpers.PrereleaseHelper;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishDriverInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.BundleCodeService;
import ca.weblite.jdeploy.services.PackageNameService;
import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Publish driver for JPM (jDeploy Package Manager).
 *
 * Uses the JPM REST API to publish releases and upload assets.
 * API base URL is configurable via the JPM_BASE_URL environment variable
 * (default: https://packages.jdeploy.com).
 * Authentication is via API key from the JPM_API_KEY environment variable,
 * sent as a Bearer token.
 */
@Singleton
public class JpmPublishDriver implements PublishDriverInterface {

    private static final String DEFAULT_JPM_BASE_URL = "https://packages.jdeploy.com";

    private final PublishDriverInterface baseDriver;
    private final BundleCodeService bundleCodeService;
    private final PackageNameService packageNameService;
    private final Environment environment;

    @Inject
    public JpmPublishDriver(
            BasePublishDriver baseDriver,
            BundleCodeService bundleCodeService,
            PackageNameService packageNameService,
            Environment environment
    ) {
        this.baseDriver = baseDriver;
        this.bundleCodeService = bundleCodeService;
        this.packageNameService = packageNameService;
        this.environment = environment;
    }

    @Override
    public void publish(
            PublishingContext context,
            PublishTargetInterface target,
            OneTimePasswordProviderInterface otpProvider
    ) throws IOException {
        String apiKey = getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("JPM_API_KEY environment variable is required for publishing to JPM");
        }

        String baseUrl = getBaseUrl();
        String[] ownerRepo = extractOwnerRepo(target);
        String owner = ownerRepo[0];
        String repo = ownerRepo[1];
        String releaseTag = context.packagingContext.getVersion();
        File releaseFiles = context.getGithubReleaseFilesDir();
        File packageInfo = new File(releaseFiles, "package-info.json");

        // Step 1: Create/update version-specific release
        createOrUpdateRelease(baseUrl, apiKey, owner, repo, releaseTag);
        context.out().println("Created/updated release: " + releaseTag);

        // Step 2: Upload all assets to the version-specific release
        File[] files = releaseFiles.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    uploadAsset(baseUrl, apiKey, owner, repo, releaseTag, file);
                    context.out().println("  Uploaded: " + file.getName());
                }
            }
        }
        context.out().println("Uploaded assets to release: " + releaseTag);

        // Step 3: Update package-info on the 'jdeploy' tag release
        createOrUpdateRelease(baseUrl, apiKey, owner, repo, "jdeploy");
        uploadAsset(baseUrl, apiKey, owner, repo, "jdeploy", packageInfo);

        // Also upload package-info-2.json as backup
        File packageInfo2 = new File(releaseFiles, "package-info-2.json");
        if (packageInfo2.exists()) {
            uploadAsset(baseUrl, apiKey, owner, repo, "jdeploy", packageInfo2);
        }
        context.out().println("Updated package-info.json on jdeploy tag");

        context.out().println("\nJPM publish completed successfully!");
        context.out().println("  Release: " + baseUrl + "/" + owner + "/" + repo + "/releases/" + releaseTag);
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

        // Pack the tarball into the release files directory
        context.npm.pack(
                context.getPublishDir(),
                context.getGithubReleaseFilesDir(),
                context.packagingContext.exitOnFail
        );

        // Copy icon, splash, and installer files
        copyReleaseAssets(context);

        // Build package-info.json
        PackageInfoBuilder builder = new PackageInfoBuilder();
        InputStream oldPackageInfo = loadPackageInfo(target);
        if (oldPackageInfo != null) {
            try {
                builder.load(oldPackageInfo);
                context.out().println("Loaded existing package-info.json with version history");
            } catch (Exception ex) {
                throw new IOException(
                        "Failed to parse existing package-info.json from JPM. Cannot proceed.", ex
                );
            }
        } else {
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

        // Copy package-info.json to package-info-2.json for backup
        File packageInfo2 = new File(context.getGithubReleaseFilesDir(), "package-info-2.json");
        FileUtils.copyFile(packageInfoFile, packageInfo2);

        // Register package name
        bundleCodeService.fetchJdeployBundleCode(
                packageNameService.getFullPackageName(
                        target,
                        context.packagingContext.getName()
                )
        );
        context.out().println("Release files created in " + context.getGithubReleaseFilesDir());
    }

    @Override
    public void makePackage(
            PublishingContext context,
            PublishTargetInterface target,
            BundlerSettings bundlerSettings
    ) throws IOException {
        if (target.getType() != PublishTargetType.JPM) {
            throw new IllegalArgumentException("JpmPublishDriver requires a JPM publish target.");
        }

        String[] ownerRepo = extractOwnerRepo(target);
        bundlerSettings.setSource("jpm:" + ownerRepo[0] + "/" + ownerRepo[1]);
        baseDriver.makePackage(context, target, bundlerSettings);
    }

    @Override
    public JSONObject fetchPackageInfoFromPublicationChannel(
            String packageName, PublishTargetInterface target
    ) throws IOException {
        String baseUrl = getBaseUrl();
        String[] ownerRepo = extractOwnerRepo(target);
        String url = baseUrl + "/api/v1/" + ownerRepo[0] + "/" + ownerRepo[1]
                + "/releases/download/jdeploy/package-info.json";

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);

        String apiKey = getApiKey();
        if (apiKey != null && !apiKey.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        }

        if (conn.getResponseCode() != 200) {
            throw new IOException(
                    "Failed to fetch package info for " + packageName + ". " + conn.getResponseMessage()
            );
        }
        return new JSONObject(IOUtil.readToString(conn.getInputStream()));
    }

    @Override
    public boolean isVersionPublished(
            String packageName, String version, PublishTargetInterface target
    ) {
        try {
            JSONObject json = fetchPackageInfoFromPublicationChannel(packageName, target);
            return json.has("versions") && json.getJSONObject("versions").has(version);
        } catch (Exception ex) {
            return false;
        }
    }

    // ---- Private helpers ----

    private String getBaseUrl() {
        String url = environment.get("JPM_BASE_URL");
        if (url == null || url.isEmpty()) {
            return DEFAULT_JPM_BASE_URL;
        }
        // Remove trailing slash
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String getApiKey() {
        return environment.get("JPM_API_KEY");
    }

    /**
     * Extract owner and repo from the publish target.
     * Target URL format: "jpm:owner/repo"
     */
    private String[] extractOwnerRepo(PublishTargetInterface target) {
        String url = target.getUrl();
        String path = url;
        if (path.startsWith("jpm:")) {
            path = path.substring("jpm:".length());
        }
        // Remove leading slash if present
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        String[] parts = path.split("/", 2);
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException(
                    "Invalid JPM target URL: " + url + ". Expected format: jpm:owner/repo"
            );
        }
        return parts;
    }

    /**
     * Create or update a release via PUT.
     */
    private void createOrUpdateRelease(
            String baseUrl, String apiKey, String owner, String repo, String tag
    ) throws IOException {
        String url = baseUrl + "/api/v1/" + owner + "/" + repo + "/releases/" + tag;
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        JSONObject body = new JSONObject();
        body.put("tag_name", tag);
        body.put("name", tag);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            String errorBody = "";
            try {
                errorBody = IOUtil.readToString(conn.getErrorStream());
            } catch (Exception ignored) {}
            throw new IOException(
                    "Failed to create/update JPM release " + tag + ": "
                            + responseCode + " " + conn.getResponseMessage()
                            + (errorBody.isEmpty() ? "" : "\n" + errorBody)
            );
        }
    }

    /**
     * Upload an asset to a release via multipart POST.
     */
    private void uploadAsset(
            String baseUrl, String apiKey, String owner, String repo, String tag, File file
    ) throws IOException {
        String url = baseUrl + "/api/v1/" + owner + "/" + repo + "/releases/" + tag + "/assets";
        String boundary = "----jDeployBoundary" + System.currentTimeMillis();

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8), true);

            // Write file part
            writer.append("--").append(boundary).append("\r\n");
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                    .append(file.getName()).append("\"\r\n");
            writer.append("Content-Type: application/octet-stream\r\n");
            writer.append("\r\n");
            writer.flush();

            Files.copy(file.toPath(), os);
            os.flush();

            writer.append("\r\n");
            writer.append("--").append(boundary).append("--\r\n");
            writer.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            String errorBody = "";
            try {
                errorBody = IOUtil.readToString(conn.getErrorStream());
            } catch (Exception ignored) {}
            throw new IOException(
                    "Failed to upload asset " + file.getName() + " to JPM release " + tag + ": "
                            + responseCode + " " + conn.getResponseMessage()
                            + (errorBody.isEmpty() ? "" : "\n" + errorBody)
            );
        }
    }

    /**
     * Load existing package-info.json from JPM for the jdeploy tag.
     * Returns null if not found (first publish).
     */
    private InputStream loadPackageInfo(PublishTargetInterface target) {
        try {
            String baseUrl = getBaseUrl();
            String[] ownerRepo = extractOwnerRepo(target);
            String url = baseUrl + "/api/v1/" + ownerRepo[0] + "/" + ownerRepo[1]
                    + "/releases/download/jdeploy/package-info.json";

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setUseCaches(false);

            String apiKey = getApiKey();
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }

            if (conn.getResponseCode() == 200) {
                return conn.getInputStream();
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    /**
     * Copy icon, splash, installer files, and package.json to release directory.
     */
    private void copyReleaseAssets(PublishingContext context) throws IOException {
        File releaseFilesDir = context.getGithubReleaseFilesDir();

        File icon = new File(context.directory(), "icon.png");
        if (icon.exists()) {
            FileUtils.copyFile(icon, new File(releaseFilesDir, icon.getName()));
        }

        File installSplash = new File(context.directory(), "installsplash.png");
        if (installSplash.exists()) {
            FileUtils.copyFile(installSplash, new File(releaseFilesDir, installSplash.getName()));
        }

        File launcherSplash = new File(context.directory(), "launcher-splash.html");
        if (launcherSplash.exists()) {
            FileUtils.copyFile(launcherSplash, new File(releaseFilesDir, launcherSplash.getName()));
        }

        File packageJson = context.getPublishPackageJsonFile();
        if (packageJson.exists()) {
            FileUtils.copyFile(packageJson, new File(releaseFilesDir, "package.json"));
        }

        File installerFiles = context.packagingContext.getInstallersDir();
        if (installerFiles.isDirectory()) {
            File[] installers = installerFiles.listFiles();
            if (installers != null) {
                for (File installerFile : installers) {
                    FileUtils.copyFile(
                            installerFile,
                            new File(releaseFilesDir, installerFile.getName().replace(' ', '.'))
                    );
                }
            }
        }
    }
}
