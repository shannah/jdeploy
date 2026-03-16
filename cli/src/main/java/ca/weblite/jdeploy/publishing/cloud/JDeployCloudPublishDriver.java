package ca.weblite.jdeploy.publishing.cloud;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.archive.JDeployArchiveGenerator;
import ca.weblite.jdeploy.archive.JDeployArchiveValidator;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.BasePublishDriver;
import ca.weblite.jdeploy.publishing.OneTimePasswordProviderInterface;
import ca.weblite.jdeploy.publishing.PublishDriverInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import ca.weblite.jdeploy.services.DefaultBundleService;
import ca.weblite.tools.io.IOUtil;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;

/**
 * Publish driver for jDeploy Cloud.
 *
 * Generates a .jdeploy archive from the prepared publish context
 * and uploads it to the jDeploy Cloud server via HTTP PUT.
 */
@Singleton
public class JDeployCloudPublishDriver implements PublishDriverInterface {

    private static final String DEFAULT_CLOUD_URL = "https://cloud.jdeploy.com";
    private static final String JDEPLOY_CLOUD_URL_ENV = "JDEPLOY_CLOUD_URL";
    private static final String JDEPLOY_CLOUD_TOKEN_ENV = "JDEPLOY_CLOUD_TOKEN";

    private final BasePublishDriver baseDriver;
    private final JDeployArchiveGenerator archiveGenerator;
    private final JDeployArchiveValidator archiveValidator;
    private final DefaultBundleService defaultBundleService;

    @Inject
    public JDeployCloudPublishDriver(
            BasePublishDriver baseDriver,
            JDeployArchiveGenerator archiveGenerator,
            JDeployArchiveValidator archiveValidator,
            DefaultBundleService defaultBundleService
    ) {
        this.baseDriver = baseDriver;
        this.archiveGenerator = archiveGenerator;
        this.archiveValidator = archiveValidator;
        this.defaultBundleService = defaultBundleService;
    }

    @Override
    public void publish(
            PublishingContext context,
            PublishTargetInterface target,
            OneTimePasswordProviderInterface otpProvider
    ) throws IOException {
        String token = getToken();
        if (token == null || token.isEmpty()) {
            throw new IOException(
                    "jDeploy Cloud token not set. " +
                    "Set the " + JDEPLOY_CLOUD_TOKEN_ENV + " environment variable or run 'jdeploy cloud-login'."
            );
        }

        String packageName = context.packagingContext.getName();
        String version = context.packagingContext.getVersion();

        // Generate the archive
        context.out().println("Generating .jdeploy archive...");
        File universalTarball = findUniversalTarball(context);
        File archiveFile = archiveGenerator.generate(
                context, universalTarball, null, null, null
        );

        // Validate the archive before uploading
        JDeployArchiveValidator.ValidationResult validation = archiveValidator.validate(archiveFile);
        if (!validation.isValid()) {
            StringBuilder sb = new StringBuilder("Archive validation failed:\n");
            for (String error : validation.getErrors()) {
                sb.append("  - ").append(error).append("\n");
            }
            throw new IOException(sb.toString());
        }

        // Upload the archive
        String cloudUrl = getCloudUrl();
        String uploadUrl = cloudUrl + "/api/v1/packages/"
                + URLEncoder.encode(packageName, "UTF-8")
                + "/versions/"
                + URLEncoder.encode(version, "UTF-8");

        context.out().println("Publishing " + packageName + "@" + version + " to jDeploy Cloud...");
        context.out().println("  URL: " + uploadUrl);

        HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setRequestProperty("Content-Length", String.valueOf(archiveFile.length()));
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(true);

        try (OutputStream os = conn.getOutputStream();
             InputStream fis = Files.newInputStream(archiveFile.toPath())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        int responseCode = conn.getResponseCode();
        if (responseCode == 201) {
            String responseBody = IOUtil.readToString(conn.getInputStream());
            JSONObject response = new JSONObject(responseBody);
            context.out().println("Published successfully!");
            context.out().println("  Package: " + response.optString("package", packageName));
            context.out().println("  Version: " + response.optString("version", version));
            if (response.has("tarball_url")) {
                context.out().println("  Tarball: " + response.getString("tarball_url"));
            }
        } else if (responseCode == 409) {
            throw new IOException(
                    "Version " + version + " of " + packageName + " is already published. " +
                    "Bump the version in package.json and try again."
            );
        } else if (responseCode == 403) {
            throw new IOException(
                    "Permission denied. You don't have access to publish package '" + packageName + "'. " +
                    "This package may be owned by another account."
            );
        } else if (responseCode == 401) {
            throw new IOException(
                    "Authentication failed. Your jDeploy Cloud token may be expired or invalid. " +
                    "Run 'jdeploy cloud-login' to re-authenticate."
            );
        } else {
            String errorBody = "";
            try {
                errorBody = IOUtil.readToString(conn.getErrorStream());
            } catch (Exception ignored) {}
            throw new IOException(
                    "jDeploy Cloud publish failed with HTTP " + responseCode + ": " +
                    conn.getResponseMessage() + "\n" + errorBody
            );
        }
    }

    @Override
    public void prepare(
            PublishingContext context,
            PublishTargetInterface target,
            BundlerSettings bundlerSettings
    ) throws IOException {
        baseDriver.prepare(context, target, bundlerSettings);
        defaultBundleService.processDefaultBundle(context);
    }

    @Override
    public void makePackage(
            PublishingContext context,
            PublishTargetInterface target,
            BundlerSettings bundlerSettings
    ) throws IOException {
        baseDriver.makePackage(context, target, bundlerSettings);
    }

    @Override
    public JSONObject fetchPackageInfoFromPublicationChannel(
            String packageName,
            PublishTargetInterface target
    ) throws IOException {
        String cloudUrl = getCloudUrl();
        String url = cloudUrl + "/api/v1/packages/"
                + URLEncoder.encode(packageName, "UTF-8")
                + "/package-info.json";

        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setUseCaches(false);

        if (conn.getResponseCode() == 404) {
            return null;
        }
        if (conn.getResponseCode() != 200) {
            throw new IOException(
                    "Failed to fetch package info for " + packageName +
                    " from jDeploy Cloud: " + conn.getResponseMessage()
            );
        }
        return new JSONObject(IOUtil.readToString(conn.getInputStream()));
    }

    @Override
    public boolean isVersionPublished(
            String packageName,
            String version,
            PublishTargetInterface target
    ) {
        try {
            JSONObject info = fetchPackageInfoFromPublicationChannel(packageName, target);
            if (info == null) return false;
            return info.has("versions") && info.getJSONObject("versions").has(version);
        } catch (Exception ex) {
            return false;
        }
    }

    // --- Private helpers ---

    private String getCloudUrl() {
        String url = System.getenv(JDEPLOY_CLOUD_URL_ENV);
        return (url != null && !url.isEmpty()) ? url : DEFAULT_CLOUD_URL;
    }

    private String getToken() {
        return System.getenv(JDEPLOY_CLOUD_TOKEN_ENV);
    }

    private File findUniversalTarball(PublishingContext context) {
        File publishDir = context.getPublishDir();
        File[] tgzFiles = publishDir.listFiles((dir, name) -> name.endsWith(".tgz"));
        if (tgzFiles != null && tgzFiles.length > 0) {
            return tgzFiles[0];
        }
        // Also check the github release files dir
        File releaseDir = context.getGithubReleaseFilesDir();
        if (releaseDir.exists()) {
            tgzFiles = releaseDir.listFiles((dir, name) -> name.endsWith(".tgz"));
            if (tgzFiles != null && tgzFiles.length > 0) {
                return tgzFiles[0];
            }
        }
        return null;
    }
}
