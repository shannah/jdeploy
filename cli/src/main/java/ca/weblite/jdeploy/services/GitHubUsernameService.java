package ca.weblite.jdeploy.services;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GitHubUsernameService {
    private GithubTokenService githubTokenService;

    public GitHubUsernameService(GithubTokenService githubTokenService) {
        this.githubTokenService = githubTokenService;
    }

    public  String getGitHubUsername() throws IOException {
        String githubToken = githubTokenService.getToken();
        // Create a URL for the GitHub API endpoint
        URL url = new URL("https://api.github.com/user");

        // Open a connection to the URL
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set up the HTTP GET request
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);

        // Check the response status code
        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            // Read the response content
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                // Parse the JSON response and extract the GitHub username
                JSONObject jsonResponse = new JSONObject(response.toString());
                return jsonResponse.getString("login");
            }
        } else {
            throw new IOException("Failed to retrieve GitHub username. Response code: " + responseCode);
        }
    }
}
