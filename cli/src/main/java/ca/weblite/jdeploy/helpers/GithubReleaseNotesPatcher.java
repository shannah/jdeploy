package ca.weblite.jdeploy.helpers;

import ca.weblite.jdeploy.environment.Environment;
import org.apache.http.client.methods.*;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.*;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class GithubReleaseNotesPatcher {

    private final Environment env;

    public GithubReleaseNotesPatcher() {
        this(new Environment());
    }

    public GithubReleaseNotesPatcher(Environment env) {
        this.env = env;
    }

    public void patch(String newReleaseNotes) throws IOException{
        patch(env.get("GITHUB_REF_NAME"), newReleaseNotes, env.get("GITHUB_TOKEN"), env.get("GITHUB_REPOSITORY"));
    }

    /**
     * Gets the current release notes for the current release tag.
     * Uses environment variables to get the release tag and GitHub token.

     * @return The current release notes in the release.
     *
     * @throws IOException
     */
    public String get() throws IOException {
        return get(env.get("GITHUB_REF_NAME"), env.get("GITHUB_TOKEN"), env.get("GITHUB_REPOSITORY"));
    }

    /**
     * Gets the current release notes for a given release tag.
     *
     * @param releaseTag  The tag of the release to get.
     * @param githubToken The GitHub token with appropriate permissions.
     * @param repository  The repository in the format "owner/repo".
     * @return The release notes content.
     * @throws IOException If an I/O error occurs.
     */
    public String get(String releaseTag, String githubToken, String repository) throws IOException {
        // Validate inputs
        if (releaseTag == null || releaseTag.isEmpty()) {
            throw new IllegalArgumentException("releaseTag cannot be null or empty");
        }
        if (githubToken == null || githubToken.isEmpty()) {
            throw new IllegalArgumentException("githubToken cannot be null or empty");
        }
        if (repository == null || !repository.contains("/")) {
            throw new IllegalArgumentException("Invalid repository format. Expected 'owner/repo'.");
        }

        // Extract owner and repo from repository
        String[] repoParts = repository.split("/", 2);
        String owner = repoParts[0];
        String repo = repoParts[1];

        // Create HttpClient
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            // Construct the URL to get the release by tag
            String getReleaseUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/tags/"
                    + URLEncoder.encode(releaseTag, StandardCharsets.UTF_8.name());

            // Build the GET request
            HttpGet getRequest = new HttpGet(getReleaseUrl);
            getRequest.setHeader("Accept", "application/vnd.github+json");
            getRequest.setHeader("Authorization", "Bearer " + githubToken);
            getRequest.setHeader("X-GitHub-Api-Version", "2022-11-28");

            // Execute the request and handle the response
            try (CloseableHttpResponse getResponse = httpClient.execute(getRequest)) {
                int statusCode = getResponse.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    String errorBody = EntityUtils.toString(getResponse.getEntity(), StandardCharsets.UTF_8);
                    throw new IOException("Failed to get release: HTTP " + statusCode + " - " + errorBody);
                }

                String responseBody = EntityUtils.toString(getResponse.getEntity(), StandardCharsets.UTF_8);

                // Parse the response to extract the release notes
                JSONObject releaseJson = new JSONObject(responseBody);
                String releaseNotes = releaseJson.optString("body", "");

                return releaseNotes;
            }
        } finally {
            httpClient.close();
        }
    }

    /**
     * Patches the GitHub release notes for a given release tag.
     *
     * @param releaseTag      The tag of the release to update.
     * @param newReleaseNotes The new release notes content.
     * @param githubToken     The GitHub token with appropriate permissions.
     * @param repository      The repository in the format "owner/repo".
     * @throws IOException If an I/O error occurs.
     */
    public void patch(String releaseTag, String newReleaseNotes, String githubToken, String repository)
            throws IOException {
        // Validate inputs
        if (releaseTag == null || releaseTag.isEmpty()) {
            throw new IllegalArgumentException("releaseTag cannot be null or empty");
        }
        if (newReleaseNotes == null) {
            throw new IllegalArgumentException("newReleaseNotes cannot be null");
        }
        if (githubToken == null || githubToken.isEmpty()) {
            throw new IllegalArgumentException("githubToken cannot be null or empty");
        }
        if (repository == null || !repository.contains("/")) {
            throw new IllegalArgumentException("Invalid repository format. Expected 'owner/repo'.");
        }

        // Extract owner and repo from repository
        String[] repoParts = repository.split("/", 2);
        String owner = repoParts[0];
        String repo = repoParts[1];

        // Create HttpClient
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            // First, get the release by tag to find the release ID
            String getReleaseUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/tags/"
                    + URLEncoder.encode(releaseTag, "UTF-8");

            HttpGet getRequest = new HttpGet(getReleaseUrl);
            getRequest.setHeader("Accept", "application/vnd.github+json");
            getRequest.setHeader("Authorization", "Bearer " + githubToken);
            getRequest.setHeader("X-GitHub-Api-Version", "2022-11-28");

            CloseableHttpResponse getResponse = httpClient.execute(getRequest);
            int getStatusCode = getResponse.getStatusLine().getStatusCode();
            if (getStatusCode != 200) {
                String errorBody = EntityUtils.toString(getResponse.getEntity(), StandardCharsets.UTF_8);
                throw new IOException("Failed to get release: HTTP " + getStatusCode + " - " + errorBody);
            }
            String getResponseBody = EntityUtils.toString(getResponse.getEntity(), StandardCharsets.UTF_8);
            getResponse.close();

            // Parse the response to extract release ID
            JSONObject releaseJson = new JSONObject(getResponseBody);
            if (!releaseJson.has("id")) {
                throw new IOException("Failed to get release ID from response");
            }
            long releaseId = releaseJson.getLong("id");

            // Prepare the PATCH request to update the release body
            String patchReleaseUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/" + releaseId;

            // Build the JSON body using org.json
            JSONObject bodyJson = new JSONObject();
            bodyJson.put("body", newReleaseNotes);
            String newReleaseNotesJson = bodyJson.toString();

            HttpPatch patchRequest = new HttpPatch(patchReleaseUrl);
            patchRequest.setHeader("Accept", "application/vnd.github+json");
            patchRequest.setHeader("Authorization", "Bearer " + githubToken);
            patchRequest.setHeader("X-GitHub-Api-Version", "2022-11-28");
            patchRequest.setHeader("Content-Type", "application/json; charset=UTF-8");

            StringEntity entity = new StringEntity(newReleaseNotesJson, StandardCharsets.UTF_8);
            patchRequest.setEntity(entity);

            CloseableHttpResponse patchResponse = httpClient.execute(patchRequest);
            int patchStatusCode = patchResponse.getStatusLine().getStatusCode();
            if (patchStatusCode != 200) {
                String errorBody = EntityUtils.toString(patchResponse.getEntity(), StandardCharsets.UTF_8);
                throw new IOException("Failed to patch release: HTTP " + patchStatusCode + " - " + errorBody);
            }

            // Optionally read the response body (if needed)
            String patchResponseBody = EntityUtils.toString(patchResponse.getEntity(), StandardCharsets.UTF_8);
            patchResponse.close();

        } finally {
            httpClient.close();
        }
    }
}
