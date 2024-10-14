package ca.weblite.jdeploy.helpers;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This test is meant to be run explicitly, as it requires a valid GitHub token and repository.
 *
 * Use:
 * TEST_GITHUB_REPOSITORY=owner/github-release-notes-patcher-test-repo
 * TEST_GITHUB_TOKEN=<your-github-token>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(OrderAnnotation.class)
public class GithubReleaseNotesPatcherTest {

    private String githubToken;
    private String repository;
    private String owner;
    private String repoName;
    private String releaseTag;
    private long releaseId;

    @BeforeAll
    public void init() {
        // Retrieve environment variables
        githubToken = System.getenv("TEST_GITHUB_TOKEN");
        repository = System.getenv("TEST_GITHUB_REPOSITORY");

        // Check if required environment variables are set
        Assumptions.assumeTrue(githubToken != null && !githubToken.isEmpty(), "TEST_GITHUB_TOKEN is not set");
        Assumptions.assumeTrue(repository != null && !repository.isEmpty(), "TEST_GITHUB_REPOSITORY is not set");

        // Extract owner and repo
        String[] repoParts = repository.split("/", 2);
        Assumptions.assumeTrue(repoParts.length == 2, "Invalid TEST_GITHUB_REPOSITORY format. Expected 'owner/repo'.");

        owner = repoParts[0];
        repoName = repoParts[1];
    }

    @BeforeEach
    public void setUp() throws IOException {
        // Generate a unique tag for the dummy release
        releaseTag = "test-release-" + UUID.randomUUID().toString();

        // Create a dummy release
        releaseId = createDummyRelease();
    }

    @AfterEach
    public void tearDown() throws IOException {
        if (githubToken != null && !githubToken.isEmpty() && releaseId != 0) {
            // Delete the dummy release
            deleteRelease(releaseId);

            // Delete the tag associated with the release
            deleteTag(releaseTag);
        }
    }

    @Test
    @Order(1)
    public void testGetReleaseNotes() throws IOException {
        GithubReleaseNotesPatcher patcher = new GithubReleaseNotesPatcher();

        // Get the current release notes
        String releaseNotes = patcher.get(releaseTag, githubToken, repository);

        // Verify that the release notes match the initial content
        assertEquals("Initial release notes.", releaseNotes, "Release notes do not match the initial content");
    }

    @Test
    @Order(2)
    public void testPatchAndGetReleaseNotes() throws IOException {
        GithubReleaseNotesPatcher patcher = new GithubReleaseNotesPatcher();

        String newReleaseNotes = "Updated release notes content.";

        // Patch the release notes
        patcher.patch(releaseTag, newReleaseNotes, githubToken, repository);

        // Retrieve the release notes using the get() method
        String updatedReleaseNotes = patcher.get(releaseTag, githubToken, repository);

        // Verify that the release notes have been updated
        assertEquals(newReleaseNotes, updatedReleaseNotes, "Release notes were not updated correctly");
    }

    // Helper method to create a dummy release
    private long createDummyRelease() throws IOException {
        String createReleaseUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/releases";

        JSONObject requestBody = new JSONObject();
        requestBody.put("tag_name", releaseTag);
        requestBody.put("name", "Dummy Release for Testing");
        requestBody.put("body", "Initial release notes.");
        requestBody.put("draft", false);
        requestBody.put("prerelease", false);

        HttpURLConnection conn = (HttpURLConnection) new URL(createReleaseUrl).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("Authorization", "Bearer " + githubToken);
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(requestBody.toString().getBytes("UTF-8"));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 201) { // 201 Created
            String errorBody = readStream(conn.getErrorStream());
            throw new IOException("Failed to create dummy release: HTTP " + responseCode + " - " + errorBody);
        }

        String responseBody = readStream(conn.getInputStream());
        JSONObject responseJson = new JSONObject(responseBody);
        return responseJson.getLong("id");
    }

    // Helper method to delete a release by ID
    private void deleteRelease(long releaseId) throws IOException {
        String deleteReleaseUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/releases/" + releaseId;

        HttpURLConnection conn = (HttpURLConnection) new URL(deleteReleaseUrl).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("Authorization", "Bearer " + githubToken);
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        int responseCode = conn.getResponseCode();
        if (responseCode != 204) { // 204 No Content
            String errorBody = readStream(conn.getErrorStream());
            throw new IOException("Failed to delete dummy release: HTTP " + responseCode + " - " + errorBody);
        }
    }

    // Helper method to delete a tag by name
    private void deleteTag(String tagName) throws IOException {
        String deleteRefUrl = "https://api.github.com/repos/" + owner + "/" + repoName + "/git/refs/tags/" + URLEncoder.encode(tagName, "UTF-8");

        HttpURLConnection conn = (HttpURLConnection) new URL(deleteRefUrl).openConnection();
        conn.setRequestMethod("DELETE");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("Authorization", "Bearer " + githubToken);
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");

        int responseCode = conn.getResponseCode();
        if (responseCode != 204) { // 204 No Content
            String errorBody = readStream(conn.getErrorStream());
            // The tag might not exist if the release creation failed; ignore this error
            System.err.println("Failed to delete tag: HTTP " + responseCode + " - " + errorBody);
        }
    }

    // Helper method to read an InputStream into a String
    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            StringBuilder responseBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                responseBuilder.append(line).append("\n");
            }
            return responseBuilder.toString().trim();
        }
    }
}
