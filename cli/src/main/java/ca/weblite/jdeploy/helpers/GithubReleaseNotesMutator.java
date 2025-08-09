package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.BundleConstants;
import ca.weblite.jdeploy.environment.Environment;

import java.io.File;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GithubReleaseNotesMutator implements BundleConstants {

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
        final Optional<File> macIntelBundle = Arrays.asList(
                releaseFilesDir.listFiles((dir, name) ->  name.contains(BUNDLE_MAC_X64))
        ).stream().findFirst();
        final Optional<File> macArmBundle = Arrays.asList(
                releaseFilesDir.listFiles((dir, name) ->  name.contains(BUNDLE_MAC_ARM64))
        ).stream().findFirst();
        final Optional<File> winBundle = Arrays.asList(
                releaseFilesDir.listFiles((dir, name) ->  name.contains(BUNDLE_WIN))
        ).stream().findFirst();
        final Optional<File> winArmBundle = Arrays.asList(
                releaseFilesDir.listFiles((dir, name) ->  name.contains(BUNDLE_WIN_ARM64))
        ).stream().findFirst();
        final Optional<File> linuxBundle = Arrays.asList(
                releaseFilesDir.listFiles((dir, name) ->  name.contains(BUNDLE_LINUX))
        ).stream().findFirst();
        final Optional<File> linuxArmBundle = Arrays.asList(
                releaseFilesDir.listFiles((dir, name) ->  name.contains(BUNDLE_LINUX_ARM64))
        ).stream().findFirst();
        StringBuilder notes = new StringBuilder();
        notes.append("## Application Installers");
        if ("branch".equals(refType)) {
            notes.append(" for latest snapshot of ").append(branchTag).append(" branch");
        } else {
            notes.append(" latest release");
        }
        notes.append("\n\n");

        if (macArmBundle.isPresent()) {
            notes.append("* [Mac (Apple Silicon)](https://github.com/")
                    .append(repo).append(releasesPrefix).append(branchTag).append("/")
                    .append(urlencodeFileNameForGithubRelease(macArmBundle.get().getName()))
                    .append(")")
                    .append("<!-- id:").append(BUNDLE_MAC_ARM64).append("-link -->")
                    .append("\n");
        }
        if (macIntelBundle.isPresent()) {
            notes.append("* [Mac (Intel)](https://github.com/")
                    .append(repo).append(releasesPrefix).append(branchTag).append("/")
                    .append(urlencodeFileNameForGithubRelease(macIntelBundle.get().getName()))
                    .append(")")
                    .append("<!-- id:").append(BUNDLE_MAC_X64).append("-link -->")
                    .append("\n");
        }
        if (winBundle.isPresent()) {
            notes.append("* [Windows (x64)](https://github.com/")
                    .append(repo).append(releasesPrefix).append(branchTag).append("/")
                    .append(urlencodeFileNameForGithubRelease(winBundle.get().getName()))
                    .append(")")
                    .append("<!-- id:").append(BUNDLE_WIN).append("-link -->")
                    .append("\n");
        }
        if (winArmBundle.isPresent()) {
            notes.append("* [Windows (arm64)](https://github.com/")
                    .append(repo).append(releasesPrefix).append(branchTag).append("/")
                    .append(urlencodeFileNameForGithubRelease(winArmBundle.get().getName()))
                    .append(")")
                    .append("<!-- id:").append(BUNDLE_WIN_ARM64).append("-link -->")
                    .append("\n");
        }
        if (linuxBundle.isPresent()) {
            notes.append("* [Linux (x64)](https://github.com/")
                    .append(repo).append(releasesPrefix).append(branchTag).append("/")
                    .append(urlencodeFileNameForGithubRelease(linuxBundle.get().getName()))
                    .append(")")
                    .append("<!-- id:").append(BUNDLE_LINUX).append("-link -->")
                    .append("\n");
        }
        if (linuxArmBundle.isPresent()) {
            notes.append("* [Linux (arm64)](https://github.com/")
                    .append(repo).append(releasesPrefix).append(branchTag).append("/")
                    .append(urlencodeFileNameForGithubRelease(linuxArmBundle.get().getName()))
                    .append(")")
                    .append("<!-- id:").append(BUNDLE_LINUX_ARM64).append("-link -->")
                    .append("\n");
        }

        if ("branch".equals(refType)) {
            notes.append("\nOr launch app installer via command-line on Linux, Mac, or Windows:\n\n");
            notes.append("```bash\n");
            notes.append("/bin/bash -c \"$(curl -fsSL https://www.jdeploy.com/gh/")
                    .append(repo).append("/").append(branchTag).append("/install.sh)\"\n");
            notes.append("```\n");
            notes.append("\nSee [download page](https://www.jdeploy.com/gh/").append(repo).append("/").append(branchTag).append(") for more download options.\n\n");
        } else {
            notes.append("\nOr launch app installer via command-line on Linux, Mac, or Windows:\n\n");
            notes.append("```bash\n");
            notes.append("/bin/bash -c \"$(curl -fsSL https://www.jdeploy.com/gh/").append(repo).append("/install.sh)\"\n");
            notes.append("```\n");
            notes.append("\nSee [download page](https://www.jdeploy.com/gh/").append(repo).append(") for more download options.\n\n");
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
