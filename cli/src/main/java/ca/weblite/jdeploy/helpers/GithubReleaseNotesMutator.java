package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.BundleConstants;
import ca.weblite.jdeploy.environment.Environment;
import ca.weblite.jdeploy.gui.JDeployProjectEditor;

import java.io.File;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GithubReleaseNotesMutator implements BundleConstants {
    private static final String JDEPLOY_WEBSITE_URL = JDeployProjectEditor.JDEPLOY_WEBSITE_URL;
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
        final String releasesPrefix = "/releases/download/";
        final File releaseFilesDir = getGithubReleaseFilesDir();
        final Optional<File> macIntelBundle = Arrays.stream(
                Objects.requireNonNull(releaseFilesDir.listFiles((dir, name) -> name.contains(BUNDLE_MAC_X64) && !name.endsWith(BUNDLE_MAC_X64 + ".tgz")))
        ).findFirst();
        final Optional<File> macArmBundle = Arrays.stream(
                Objects.requireNonNull(releaseFilesDir.listFiles((dir, name) -> name.contains(BUNDLE_MAC_ARM64) && !name.endsWith(BUNDLE_MAC_ARM64 + ".tgz")))
        ).findFirst();
        final Optional<File> winBundle = Arrays.stream(
                Objects.requireNonNull(releaseFilesDir.listFiles((dir, name) -> name.contains(BUNDLE_WIN) && !name.endsWith(BUNDLE_WIN + ".tgz")))
        ).findFirst();
        final Optional<File> winArmBundle = Arrays.stream(
                Objects.requireNonNull(releaseFilesDir.listFiles((dir, name) -> name.contains(BUNDLE_WIN_ARM64) && !name.endsWith(BUNDLE_WIN_ARM64 + ".tgz")))
        ).findFirst();
        final Optional<File> linuxBundle = Arrays.stream(
                Objects.requireNonNull(releaseFilesDir.listFiles((dir, name) -> name.contains(BUNDLE_LINUX) && !name.endsWith(BUNDLE_LINUX + ".tgz")))
        ).findFirst();
        final Optional<File> linuxArmBundle = Arrays.stream(
                Objects.requireNonNull(releaseFilesDir.listFiles((dir, name) -> name.contains(BUNDLE_LINUX_ARM64) && !name.endsWith(BUNDLE_LINUX_ARM64 + ".tgz")))
        ).findFirst();
        StringBuilder notes = new StringBuilder();
        notes.append("## Application Installers");
        if ("branch".equals(refType)) {
            notes.append(" for latest snapshot of ").append(branchTag).append(" branch");
        } else {
            notes.append(" latest release");
        }
        notes.append("\n\n");

        macArmBundle.ifPresent(file -> notes.append("* [Mac (Apple Silicon)](https://github.com/")
                .append(repo).append(releasesPrefix).append(branchTag).append("/")
                .append(urlencodeFileNameForGithubRelease(file.getName()))
                .append(")")
                .append("<!-- id:").append(BUNDLE_MAC_ARM64).append("-link -->")
                .append("\n"));
        macIntelBundle.ifPresent(file -> notes.append("* [Mac (Intel)](https://github.com/")
                .append(repo).append(releasesPrefix).append(branchTag).append("/")
                .append(urlencodeFileNameForGithubRelease(file.getName()))
                .append(")")
                .append("<!-- id:").append(BUNDLE_MAC_X64).append("-link -->")
                .append("\n"));
        winBundle.ifPresent(file -> notes.append("* [Windows (x64)](https://github.com/")
                .append(repo).append(releasesPrefix).append(branchTag).append("/")
                .append(urlencodeFileNameForGithubRelease(file.getName()))
                .append(")")
                .append("<!-- id:").append(BUNDLE_WIN).append("-link -->")
                .append("\n"));
        winArmBundle.ifPresent(file -> notes.append("* [Windows (arm64)](https://github.com/")
                .append(repo).append(releasesPrefix).append(branchTag).append("/")
                .append(urlencodeFileNameForGithubRelease(file.getName()))
                .append(")")
                .append("<!-- id:").append(BUNDLE_WIN_ARM64).append("-link -->")
                .append("\n"));
        linuxBundle.ifPresent(file -> notes.append("* [Linux (x64)](https://github.com/")
                .append(repo).append(releasesPrefix).append(branchTag).append("/")
                .append(urlencodeFileNameForGithubRelease(file.getName()))
                .append(")")
                .append("<!-- id:").append(BUNDLE_LINUX).append("-link -->")
                .append("\n"));
        linuxArmBundle.ifPresent(file -> notes.append("* [Linux (arm64)](https://github.com/")
                .append(repo).append(releasesPrefix).append(branchTag).append("/")
                .append(urlencodeFileNameForGithubRelease(file.getName()))
                .append(")")
                .append("<!-- id:").append(BUNDLE_LINUX_ARM64).append("-link -->")
                .append("\n"));

        if ("branch".equals(refType)) {
            notes.append("\nOr launch app installer via command-line on Linux, Mac, or Windows:\n\n");
            notes.append("```bash\n");
            notes.append("/bin/bash -c \"$(curl -fsSL ").append(JDEPLOY_WEBSITE_URL).append("gh/")
                    .append(repo).append("/").append(branchTag).append("/install.sh)\"\n");
            notes.append("```\n");
            notes.append("\nSee [download page](").append(JDEPLOY_WEBSITE_URL).append("gh/").append(repo).append("/").append(branchTag).append(") for more download options.\n\n");
        } else {
            notes.append("\nOr launch app installer via command-line on Linux, Mac, or Windows:\n\n");
            notes.append("```bash\n");
            notes.append("/bin/bash -c \"$(curl -fsSL ").append(JDEPLOY_WEBSITE_URL).append("gh/").append(repo).append("/install.sh)\"\n");
            notes.append("```\n");
            notes.append("\nSee [download page](").append(JDEPLOY_WEBSITE_URL).append("gh/").append(repo).append(") for more download options.\n\n");
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
