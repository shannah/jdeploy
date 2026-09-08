package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.BundleConstants;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.gui.MenuBarBuilder;

import java.io.File;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GithubReleaseNotesMutator implements BundleConstants {
    private static final String JDEPLOY_WEBSITE_URL = MenuBarBuilder.JDEPLOY_WEBSITE_URL;

    /**
     * The installer bundles, in the order that they are listed in the release notes,
     * mapped to the label used for their download link.
     */
    private static final Map<String, String> INSTALLER_BUNDLE_LABELS;
    static {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put(BUNDLE_MAC_ARM64, "Mac (Apple Silicon)");
        labels.put(BUNDLE_MAC_X64, "Mac (Intel)");
        labels.put(BUNDLE_WIN, "Windows (x64)");
        labels.put(BUNDLE_WIN_ARM64, "Windows (arm64)");
        labels.put(BUNDLE_LINUX, "Linux (x64)");
        labels.put(BUNDLE_LINUX_ARM64, "Linux (arm64)");
        INSTALLER_BUNDLE_LABELS = Collections.unmodifiableMap(labels);
    }

    private final File directory;

    private final PrintStream err;

    private final Environment env;

    public GithubReleaseNotesMutator(File directory) {
        this(directory, System.err);
    }

    public GithubReleaseNotesMutator(File directory, PrintStream err) {
        this(directory, err, new Environment());
    }

    public GithubReleaseNotesMutator(File directory, PrintStream err, Environment env) {
        this.directory = directory;
        this.err = err;
        this.env = env;
    }

    public String createGithubReleaseNotes() {
        return createGithubReleaseNotes(
                env.get("GITHUB_REPOSITORY"),
                env.get("GITHUB_REF_NAME"),
                env.get("GITHUB_REF_TYPE")
        );
    }

    public String createGithubReleaseNotes(
            final String repo,
            final String branchTag,
            final String refType
    ) {
        return createGithubReleaseNotes(repo, branchTag, refType, false, null);
    }

    public String createGithubReleaseNotes(
            final String repo,
            final String branchTag,
            final String refType,
            final boolean hasCommands,
            final String version
    ) {
        return createGithubReleaseNotes(repo, branchTag, refType, hasCommands, version, null);
    }

    /**
     * Creates the jDeploy section of the GitHub release notes.
     *
     * @param repo The repository that the release notes belong to.  Used for the CLI installation links.
     * @param branchTag The branch or tag name of the release.
     * @param refType Either "branch" or "tag".
     * @param hasCommands Whether the app defines CLI commands.
     * @param version The version being released.
     * @param downloadRepo The repository that hosts the installer assets.  This may differ from
     *                     {@code repo} when the action publishes its release files to a separate
     *                     target repository.  If null, {@code repo} is used.
     * @return The release notes markdown.
     */
    public String createGithubReleaseNotes(
            final String repo,
            final String branchTag,
            final String refType,
            final boolean hasCommands,
            final String version,
            final String downloadRepo
    ) {
        final String releasesPrefix = "/releases/download/";
        final String assetRepo = downloadRepo == null ? repo : downloadRepo;
        final Map<String, File> installerFiles = getInstallerFiles();
        StringBuilder notes = new StringBuilder();
        notes.append("## Application Installers");
        if ("branch".equals(refType)) {
            notes.append(" for latest snapshot of ").append(branchTag).append(" branch");
        } else {
            notes.append(" latest release");
        }
        notes.append("\n\n");

        for (Map.Entry<String, String> entry : INSTALLER_BUNDLE_LABELS.entrySet()) {
            final String bundleName = entry.getKey();
            final File file = installerFiles.get(bundleName);
            if (file == null) {
                continue;
            }
            notes.append("* [").append(entry.getValue()).append("](https://github.com/")
                    .append(assetRepo).append(releasesPrefix).append(branchTag).append("/")
                    .append(urlencodeFileNameForGithubRelease(file.getName()))
                    .append(")")
                    .append("<!-- id:").append(bundleName).append("-link -->")
                    .append("\n");
        }

        // Only show CLI installation section if the app has commands defined
        if (hasCommands) {
            notes.append("\n## CLI Installation\n\n");

            if ("branch".equals(refType)) {
                // Branch release - include branch in URL
                String baseUrl = JDEPLOY_WEBSITE_URL + "gh/" + repo + "/" + branchTag;

                notes.append("### Interactive\n");
                notes.append("```bash\n");
                notes.append("/bin/bash -c \"$(curl -fsSL '").append(baseUrl).append("/install.sh')\"\n");
                notes.append("```\n");
                notes.append("Launches graphical installer\n\n");

                notes.append("### Headless\n");
                notes.append("```bash\n");
                notes.append("/bin/bash -c \"$(curl -fsSL '").append(baseUrl).append("/install.sh?headless=true')\"\n");
                notes.append("```\n");
                notes.append("For CI/CD and automated deployments\n\n");

                if (version != null && !version.isEmpty()) {
                    notes.append("### Version-Pinned Headless\n");
                    notes.append("```bash\n");
                    notes.append("/bin/bash -c \"$(curl -fsSL '").append(baseUrl).append("/").append(version).append("/install.sh?headless=true')\"\n");
                    notes.append("```\n");
                    notes.append("Install specific version ").append(version).append("\n\n");
                }

                notes.append("See [download page](").append(baseUrl).append(") for more download options.\n\n");
            } else {
                // Tag release - no branch in URL
                String baseUrl = JDEPLOY_WEBSITE_URL + "gh/" + repo;

                notes.append("### Interactive\n");
                notes.append("```bash\n");
                notes.append("/bin/bash -c \"$(curl -fsSL '").append(baseUrl).append("/install.sh')\"\n");
                notes.append("```\n");
                notes.append("Launches graphical installer\n\n");

                notes.append("### Headless\n");
                notes.append("```bash\n");
                notes.append("/bin/bash -c \"$(curl -fsSL '").append(baseUrl).append("/install.sh?headless=true')\"\n");
                notes.append("```\n");
                notes.append("For CI/CD and automated deployments\n\n");

                if (version != null && !version.isEmpty()) {
                    notes.append("### Version-Pinned Headless\n");
                    notes.append("```bash\n");
                    notes.append("/bin/bash -c \"$(curl -fsSL '").append(baseUrl).append("/").append(version).append("/install.sh?headless=true')\"\n");
                    notes.append("```\n");
                    notes.append("Install specific version ").append(version).append("\n\n");
                }

                notes.append("See [download page](").append(baseUrl).append(") for more download options.\n\n");
            }
        }

        return notes.toString();
    }

    /**
     * Modifies bundle link in GitHub release notes.
     *
     * @param releaseNotes The release notes contents as generated by createGithubReleaseNotes()
     * @param bundleName   The bundle name. One of BUNDLE_MAC_X64, BUNDLE_MAC_ARM64, BUNDLE_WIN, BUNDLE_LINUX constants
     * @param bundleUrl    The URL to change the link to.
     * @return The modified release notes
     */
    public String updateLinkInGithubReleaseNotes(String releaseNotes, String bundleName, String bundleUrl) {
        // Build the HTML comment identifier
        String idComment = "<!-- id:" + bundleName + "-link -->";

        // Construct the regex pattern to find the markdown link with the specific id
        String regex = "(\\* \\[.*?\\]\\()(.*?)(\\))(" + Pattern.quote(idComment) + ")";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(releaseNotes);

        // Use StringBuffer for efficient string manipulation
        StringBuffer updatedReleaseNotes = new StringBuffer();

        while (matcher.find()) {
            String prefix = matcher.group(1); // The part before the URL
            String suffix = matcher.group(3) + matcher.group(4); // The closing parenthesis and id comment

            // Construct the replacement string with the new URL
            String replacement = prefix + bundleUrl + suffix;

            // Append the replacement to the result
            matcher.appendReplacement(updatedReleaseNotes, Matcher.quoteReplacement(replacement));
        }
        // Append the remaining part of the release notes
        matcher.appendTail(updatedReleaseNotes);

        return updatedReleaseNotes.toString();
    }

    /**
     * Finds the installer artifacts in the github-release-files directory.  These are the
     * platform installers that end users download, as opposed to the other release files
     * (platform bundles, package-info.json, icons, etc..).
     *
     * @return A map of bundle name (e.g. "mac-arm64") to the installer file for that bundle,
     * in the order that they are listed in the release notes.  Bundles without an installer
     * are omitted.
     */
    public Map<String, File> getInstallerFiles() {
        final File releaseFilesDir = getGithubReleaseFilesDir();
        final Map<String, File> installerFiles = new LinkedHashMap<>();
        if (!releaseFilesDir.isDirectory()) {
            return installerFiles;
        }
        for (String bundleName : INSTALLER_BUNDLE_LABELS.keySet()) {
            findInstallerFile(releaseFilesDir, bundleName)
                    .ifPresent(file -> installerFiles.put(bundleName, file));
        }

        return installerFiles;
    }

    private Optional<File> findInstallerFile(final File releaseFilesDir, final String bundleName) {
        final File[] matches = releaseFilesDir.listFiles(
                (dir, name) -> name.contains(bundleName) && !name.endsWith(bundleName + ".tgz")
        );

        return Arrays.stream(Objects.requireNonNull(matches)).findFirst();
    }

    private File getGithubReleaseFilesDir() {
        return new File(directory, "jdeploy" + File.separator + "github-release-files");
    }

    private String urlencodeFileNameForGithubRelease(String str) {
        str = str.replace(" ", ".");
        try {
            return URLEncoder.encode(str, "UTF-8");
        } catch (Exception ex) {
            err.println("Failed to encode string "+str);
            ex.printStackTrace(err);
            return str;
        }
    }
}
