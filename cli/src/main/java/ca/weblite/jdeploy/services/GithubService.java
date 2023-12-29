package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.PackageInfoBuilder;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import java.io.File;
import java.io.IOException;
import java.util.Set;

public class GithubService {

    private String token;

    public PackageInfoBuilder createPackageInfoBuilder(String source) {
        PackageInfoBuilder builder = new PackageInfoBuilder();
        builder.setSource(source);

        return builder;
    }

    public String getRepoURL(File repoDir) throws IOException {
        ProcessBuilder builder = new ProcessBuilder();
        builder.command("git", "config", "--get", "remote.origin.url")
                .directory(repoDir);
        Process process = builder.start();
        Scanner scanner = new Scanner(process.getInputStream());
        String url = scanner.nextLine();
        scanner.close();
        return url;
    }

    public String getFirstRemoteHttpsUrl(String repositoryPath) {
        File gitDir = new File(repositoryPath, ".git");
        if (!gitDir.exists()) {
            return null;
        }
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()) {
            Config storedConfig = repository.getConfig();
            Set<String> remotes = storedConfig.getSubsections("remote");

            if (!remotes.isEmpty()) {
                String remoteName = remotes.iterator().next();
                String url = storedConfig.getString("remote", remoteName, "url");

                if (url != null && !url.startsWith("https://")) {
                    url = convertToHttpsUrl(url);
                }
                url = url.replaceFirst("\\.git$", "");

                return url;
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }

        return null;
    }

    public boolean isRepoPrivateOrDoesNotExist(String repoUrl) {
        try {
            URL url = new URL(repoUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("HEAD");
            connection.connect();

            int responseCode = connection.getResponseCode();
            connection.disconnect();

            // Assuming 404 indicates a private repo or a repo that does not exist
            return responseCode == HttpURLConnection.HTTP_NOT_FOUND;
        } catch (Exception e) {
            e.printStackTrace();
            return false; // In case of error, handle accordingly
        }
    }

    private String convertToHttpsUrl(String url) {
        // Assuming the URL is an SSH URL, typical for GitHub
        if (url.startsWith("git@")) {
            url = url.replaceFirst("git@", "https://");
            url = url.replaceFirst(":", "/");
            url = url.replaceFirst(".git$", "");
        }

        return url;
    }

}
