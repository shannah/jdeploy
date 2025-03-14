package ca.weblite.jdeploy.services;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class GithubPagesPublisher {
    private GithubService githubService = new GithubService();
    /**
     * Publishes the contents of a directory to a github pages branch.
     *
     * @param sourceDirectory The directory containing the files to publish.
     * @param repoUrl         The URL of the github repository
     * @param branchName      The branch-name to publish to
     * @param destPath        The destination path within the repo where the files should be published.
     * @throws IOException, InterruptedException
     */
    public void publishToGithubPages(File sourceDirectory, String repoUrl, String branchName, String destPath) throws IOException, InterruptedException {
        if (repoUrl == null) {
            repoUrl = githubService.getRepoURL(sourceDirectory);
        }
        File tempDir = null;
        try {
            // Create a temporary directory
            tempDir = Files.createTempDirectory("tempRepoDir").toFile();
            System.out.println("Cloning branch " + branchName + " of " + repoUrl);
            // Clone the repository to a temporary directory
            ProcessBuilder builder = new ProcessBuilder();
            builder.command("git", "clone", "--depth", "1", "-b", branchName, repoUrl, tempDir.getAbsolutePath())
                    .inheritIO()
                    .start()
                    .waitFor();

            // Copy the contents of sourceDirectory to the temporary directory at destPath
            File destDir = new File(tempDir, destPath);
            if (!isSubdirectoryOrFile(tempDir, destDir)) {
                throw new IOException("Destination path " + destPath + " is not a subdirectory of the repository");
            }
            System.out.println("Copying files from " + sourceDirectory.getAbsolutePath() + " to " + destDir.getAbsolutePath() + "...");
            if (!this.isRepoRootDirectory(destDir)) {
                // For subdirectories, delete the old one.
                // Can't do that for the root directory, because that would delete the .git directory.
                deleteDirectory(destDir);
            }

            copyDirectory(sourceDirectory, destDir);

            // Add, commit and push the changes
            int result = builder.command("git", "add", ".")
                    .directory(destDir)
                    .inheritIO()
                    .start()
                    .waitFor();
            if (result != 0) {
                throw new IOException("Failed to add files to git");
            }
            result = builder.command("git", "status", ".")
                    .directory(destDir)
                    .inheritIO()
                    .start()
                    .waitFor();
            if (result != 0) {
                throw new IOException("Failed to get git status");
            }
            result = builder.command("git", "commit", "-m", "Update GitHub Pages")
                    .directory(tempDir)
                    .inheritIO()
                    .start()
                    .waitFor();
            if (result != 0) {
                throw new IOException("Failed to commit files to git");
            }
            List<String> pushCommand = new ArrayList<>();
            pushCommand.add("git");
            pushCommand.add("push");
            if (getGithubTokenFromEnvironment() != null) {
                pushCommand.add("https://" + getGithubTokenFromEnvironment() + "@"
                        + repoUrl.substring(8));
            }
            result = builder.command(pushCommand)
                    .directory(tempDir)
                    .inheritIO()
                    .start()
                    .waitFor();
            if (result != 0) {
                throw new IOException("Failed to push files to git");
            }
        } finally {
            // Delete the temporary directory
            if (tempDir != null) {
                deleteDirectory(tempDir);
            }
        }
    }

    // Copy the source directory to the destination directory
    private void copyDirectory(File source, File dest) throws IOException {
        if (source.isDirectory()) {
            if (!dest.exists()) {
                dest.mkdir();
            }
            for (String child : source.list()) {
                copyDirectory(new File(source, child), new File(dest, child));
            }
        } else {
            System.out.println("Copying " + source.getAbsolutePath() + " to " + dest.getAbsolutePath() + "...");
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Delete a directory recursively
    private void deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        directoryToBeDeleted.delete();
    }

    private String getGithubTokenFromEnvironment() {
        return System.getenv("GH_TOKEN");
    }

    private boolean isRepoRootDirectory(File directory) {
        return new File(directory, ".git").exists();
    }

    private boolean isSubdirectoryOrFile(File parent, File child) {
        Path parentPath = parent.toPath().normalize().toAbsolutePath();
        Path childPath = child.toPath().normalize().toAbsolutePath();

        return childPath.startsWith(parentPath);
    }
}
