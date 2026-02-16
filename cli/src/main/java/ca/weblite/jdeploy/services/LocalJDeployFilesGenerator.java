package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.JDeploy;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;

/**
 * Generates a .jdeploy-files directory for local installation mode.
 *
 * This creates the same structure as jdeploy-files.zip but as a directory,
 * with app.xml containing local-package-json and local-bundle attributes
 * that point to the local project files.
 *
 * Directory structure:
 * {outputDir}/.jdeploy-files/
 *   ├── app.xml                    (with local-* attributes)
 *   ├── icon.png
 *   ├── installsplash.png
 *   └── launcher-splash.html       (optional)
 */
@Singleton
public class LocalJDeployFilesGenerator {

    @Inject
    public LocalJDeployFilesGenerator() {
        // Default constructor for dependency injection
    }

    /**
     * Generates .jdeploy-files directory for local installation.
     *
     * @param projectDir The project directory containing package.json
     * @param bundleDir The jdeploy-bundle directory
     * @param outputDir The directory where .jdeploy-files will be created
     * @return The path to the generated .jdeploy-files directory
     * @throws IOException if generation fails
     */
    public File generate(File projectDir, File bundleDir, File outputDir) throws IOException {
        File packageJsonFile = new File(projectDir, "package.json");
        if (!packageJsonFile.exists()) {
            throw new IOException("package.json not found in project directory: " + projectDir);
        }

        // Read package.json
        JSONObject packageJson = new JSONObject(
                FileUtils.readFileToString(packageJsonFile, StandardCharsets.UTF_8)
        );

        String packageName = packageJson.getString("name");
        String version = packageJson.getString("version");

        // Append -local suffix to version
        String localVersion = appendLocalSuffix(version);

        String title = getTitle(packageJson);
        boolean prerelease = isPrerelease(localVersion);

        // Read and encode icon
        File iconFile = new File(projectDir, "icon.png");
        File iconFileToUse = resolveIconFile(iconFile);

        byte[] iconBytes = Files.readAllBytes(iconFileToUse.toPath());
        String iconDataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(iconBytes);

        // Generate app.xml with local paths
        String appXml = generateAppXml(
                title,
                packageName,
                localVersion,
                iconDataUri,
                prerelease,
                packageJsonFile.getAbsolutePath(),
                bundleDir.getAbsolutePath()
        );

        // Create output directory structure
        File jdeployFilesDir = new File(outputDir, ".jdeploy-files");
        jdeployFilesDir.mkdirs();

        // Write app.xml
        File appXmlFile = new File(jdeployFilesDir, "app.xml");
        FileUtils.writeStringToFile(appXmlFile, appXml, StandardCharsets.UTF_8);

        // Copy icon.png
        File destIconFile = new File(jdeployFilesDir, "icon.png");
        FileUtils.copyFile(iconFileToUse, destIconFile);

        // Copy installsplash.png
        File splashFile = new File(projectDir, "installsplash.png");
        File destSplashFile = new File(jdeployFilesDir, "installsplash.png");
        if (splashFile.exists()) {
            FileUtils.copyFile(splashFile, destSplashFile);
        } else {
            // Use default installsplash from resources
            FileUtils.copyInputStreamToFile(
                    JDeploy.class.getResourceAsStream("installsplash.png"),
                    destSplashFile
            );
        }

        // Copy launcher-splash.html if it exists
        File launcherSplashFile = new File(projectDir, "launcher-splash.html");
        if (launcherSplashFile.exists()) {
            File destLauncherSplashFile = new File(jdeployFilesDir, "launcher-splash.html");
            FileUtils.copyFile(launcherSplashFile, destLauncherSplashFile);
        }

        return jdeployFilesDir;
    }

    /**
     * Appends -local suffix to version if not already present.
     */
    private String appendLocalSuffix(String version) {
        if (version.endsWith("-local")) {
            return version;
        }
        if (version.contains("-")) {
            // Already has a prerelease suffix, append .local
            return version + ".local";
        }
        return version + "-local";
    }

    /**
     * Generates the app.xml content with local-* attributes.
     */
    private String generateAppXml(String title, String packageName, String version,
                                   String iconDataUri, boolean prerelease,
                                   String localPackageJson, String localBundle) {
        return String.format(
                "<app title='%s' package='%s' version='%s' icon='%s' fork='false' prerelease='%s' " +
                "local-package-json='%s' local-bundle='%s' />",
                xmlEscape(title),
                xmlEscape(packageName),
                xmlEscape(version),
                xmlEscape(iconDataUri),
                prerelease ? "true" : "false",
                xmlEscape(localPackageJson),
                xmlEscape(localBundle)
        );
    }

    /**
     * Resolves the icon file to use (project icon or default).
     */
    private File resolveIconFile(File projectIconFile) throws IOException {
        if (projectIconFile.exists()) {
            return projectIconFile;
        }
        // Use default icon from resources
        File tempIconFile = File.createTempFile("jdeploy-", "-icon.png");
        tempIconFile.deleteOnExit();
        FileUtils.copyInputStreamToFile(
                JDeploy.class.getResourceAsStream("icon.png"),
                tempIconFile
        );
        return tempIconFile;
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
     * Determines if a version is a prerelease.
     * Versions containing '-' are considered prereleases.
     */
    private boolean isPrerelease(String version) {
        return version.contains("-");
    }
}
