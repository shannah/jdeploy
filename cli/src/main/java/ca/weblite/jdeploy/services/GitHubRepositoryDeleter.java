package ca.weblite.jdeploy.services;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitHubRepositoryDeleter {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/USERNAME/REPO_NAME";

    private GithubTokenService githubTokenService;

    private GitHubUsernameService gitHubUsernameService;

    public GitHubRepositoryDeleter(GithubTokenService githubTokenService, GitHubUsernameService gitHubUsernameService) {
        this.githubTokenService = githubTokenService;
        this.gitHubUsernameService = gitHubUsernameService;
    }

    public void deleteGitHubRepository(String repositoryName) throws IOException {
        String apiUrl = GITHUB_API_URL
                .replace("USERNAME", gitHubUsernameService.getGitHubUsername())
                .replace("REPO_NAME", repositoryName);

        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            // Set up the HTTP DELETE request
            connection.setRequestMethod("DELETE");
            connection.setRequestProperty("Authorization", "Bearer " + githubTokenService.getToken());

            // Check the response status
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                // Repository deleted successfully
            } else {
                throw new IOException("Failed to delete repository '" + repositoryName + "'. Response code: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

}
