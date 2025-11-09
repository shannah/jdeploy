package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishing.PublishingContext;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Generates jdeploy-files.zip for GitHub releases.
 *
 * This zip file contains the essential assets needed by jDeploy installers:
 * - app.xml: Application metadata in minimal format
 * - icon.png: Application icon
 * - installsplash.png: Installer splash screen (optional)
 * - launcher-splash.html: Launcher splash HTML (optional)
 *
 * The zip structure matches the server-side format:
 * {packageName}-{version}/.jdeploy-files/...
 */
@Singleton
public class JDeployFilesZipGenerator {

    @Inject
    public JDeployFilesZipGenerator() {
        // Default constructor for dependency injection
    }

    /**
     * Generates jdeploy-files.zip and writes it to the GitHub release files directory.
     *
     * @param context The publishing context
     * @param target The publish target
     * @throws IOException if generation fails
     */
    public void generate(PublishingContext context, PublishTargetInterface target) throws IOException {
        // 1. Read package.json
        JSONObject packageJson = new JSONObject(
                FileUtils.readFileToString(context.packagingContext.packageJsonFile, StandardCharsets.UTF_8)
        );

        String packageName = packageJson.getString("name");
        String version = packageJson.getString("version");
        String title = getTitle(packageJson);
        String source = getSource(packageJson, target);
        boolean prerelease = isPrerelease(version);

        // 2. Read and encode icon
        File iconFile = new File(context.directory(), "icon.png");
        if (!iconFile.exists()) {
            throw new IOException("Required file not found: icon.png\n" +
                    "Please add an icon.png file to your project root directory.");
        }
        byte[] iconBytes = Files.readAllBytes(iconFile.toPath());
        String iconDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(iconBytes);

        // 3. Generate app.xml
        String appXml = generateAppXml(title, packageName, source, version, iconDataUri, prerelease);

        // 4. Create zip archive
        File outputZip = new File(context.getGithubReleaseFilesDir(), "jdeploy-files.zip");
        String baseDir = packageName + "-" + version + "/.jdeploy-files/";

        try (FileOutputStream fos = new FileOutputStream(outputZip);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Add directory entry
            zos.putNextEntry(new ZipEntry(baseDir));
            zos.closeEntry();

            // Add app.xml
            addStringToZip(zos, baseDir + "app.xml", appXml);

            // Add icon.png
            addFileToZip(zos, baseDir + "icon.png", iconFile);

            // Add installsplash.png (optional)
            File splashFile = new File(context.directory(), "installsplash.png");
            if (splashFile.exists()) {
                addFileToZip(zos, baseDir + "installsplash.png", splashFile);
            }

            // Add launcher-splash.html (optional)
            File launcherSplashFile = new File(context.directory(), "launcher-splash.html");
            if (launcherSplashFile.exists()) {
                addFileToZip(zos, baseDir + "launcher-splash.html", launcherSplashFile);
            }
        }

        context.out().println("Generated jdeploy-files.zip (" + formatFileSize(outputZip.length()) + ")");
    }

    /**
     * Generates the minimal app.xml content.
     * Format: <app title='...' package='...' source='...' version='...' icon='...' fork='false' prerelease='...' />
     */
    private String generateAppXml(String title, String packageName, String source,
                                   String version, String iconDataUri, boolean prerelease) {
        return String.format(
                "<app title='%s' package='%s' source='%s' version='%s' icon='%s' fork='false' prerelease='%s' />",
                xmlEscape(title),
                xmlEscape(packageName),
                xmlEscape(source),
                xmlEscape(version),
                xmlEscape(iconDataUri),
                prerelease ? "true" : "false"
        );
    }

    /**
     * Escapes special XML characters in attribute values.
     */
    private String xmlEscape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * Gets the application title from package.json.
     * Priority: jdeploy.title > name
     */
    private String getTitle(JSONObject packageJson) {
        if (packageJson.has("jdeploy")) {
            JSONObject jdeploy = packageJson.getJSONObject("jdeploy");
            if (jdeploy.has("title")) {
                return jdeploy.getString("title");
            }
        }
        return packageJson.getString("name");
    }

    /**
     * Gets the source URL from package.json.
     * Priority: jdeploy.source > repository.url > target URL
     */
    private String getSource(JSONObject packageJson, PublishTargetInterface target) {
        // Fall back to target URL
        return target.getUrl();
    }

    /**
     * Determines if a version is a prerelease.
     * Versions containing '-' are considered prereleases (e.g., 1.0.0-beta, 1.0.0-SNAPSHOT)
     */
    private boolean isPrerelease(String version) {
        return version.contains("-");
    }

    /**
     * Adds a string content to the zip as an entry.
     */
    private void addStringToZip(ZipOutputStream zos, String entryName, String content) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    /**
     * Adds a file to the zip as an entry.
     */
    private void addFileToZip(ZipOutputStream zos, String entryName, File file) throws IOException {
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(file.lastModified());
        zos.putNextEntry(entry);
        Files.copy(file.toPath(), zos);
        zos.closeEntry();
    }

    /**
     * Formats a file size in human-readable format.
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
