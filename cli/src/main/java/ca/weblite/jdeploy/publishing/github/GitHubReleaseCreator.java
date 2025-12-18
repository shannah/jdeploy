package ca.weblite.jdeploy.publishing.github;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Singleton;
import javax.inject.Inject;

@Singleton
public class GitHubReleaseCreator {

    @Inject
    public GitHubReleaseCreator() {
        // Default constructor for dependency injection
    }

    public void createRelease(
            String repositoryUrl,
            String githubToken,
            String releaseName,
            String releaseDescription,
            File[] artifacts
    ) throws IOException {
        // Extract owner and repository name from the URL
        String apiUrl = getApiUrl(repositoryUrl);

        // Prepare release payload
        String payload = String.format(
                "{\"tag_name\":\"%s\",\"name\":\"%s\",\"body\":\"%s\"}",
                releaseName, releaseName, escapeJson(releaseDescription)
        );

        // Create release
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl + "/releases").openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes("UTF-8"));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 201) {
            throw new IOException("Failed to create release: " + readStream(connection.getErrorStream()));
        }

        String response = readStream(connection.getInputStream());
        String uploadUrl = parseUploadUrl(response);

        // Upload artifacts
        if (artifacts != null) {
            uploadArtifacts(uploadUrl, githubToken, artifacts);
        }
    }

    public void createRelease(
            String repositoryUrl,
            String githubToken,
            String releaseName,
            File releaseDescriptionFile,
            File[] artifacts
    ) throws IOException {
        String description = new String(Files.readAllBytes(releaseDescriptionFile.toPath()), "UTF-8");
        createRelease(repositoryUrl, githubToken, releaseName, description, artifacts);
    }

    /**
     * Creates a GitHub release atomically with artifacts.
     * Fails if the release already exists (concurrent publish detection).
     *
     * @param repositoryUrl The repository URL (e.g., https://github.com/user/repo)
     * @param githubToken The GitHub token for authentication
     * @param tag The release tag name
     * @param description The release description
     * @param artifacts The artifacts to upload
     * @throws GitHubReleaseAlreadyExistsException if the release already exists (422 response)
     * @throws IOException on other errors
     */
    public void createReleaseAtomic(
            String repositoryUrl,
            String githubToken,
            String tag,
            String description,
            File[] artifacts
    ) throws IOException {
        // Extract owner and repository name from the URL
        String apiUrl = getApiUrl(repositoryUrl);

        // Prepare release payload
        String payload = String.format(
                "{\"tag_name\":\"%s\",\"name\":\"%s\",\"body\":\"%s\"}",
                tag, tag, escapeJson(description)
        );

        // Create release
        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl + "/releases").openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream()) {
            os.write(payload.getBytes("UTF-8"));
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 422) {
            // Release already exists - concurrent publish detected
            throw new GitHubReleaseAlreadyExistsException(
                "Release '" + tag + "' already exists. " +
                "This indicates a concurrent publish - another process created this release " +
                "while this publish was running."
            );
        }
        if (responseCode != 201) {
            throw new IOException("Failed to create release: " + readStream(connection.getErrorStream()));
        }

        String response = readStream(connection.getInputStream());
        String uploadUrl = parseUploadUrl(response);

        // Upload artifacts
        if (artifacts != null && artifacts.length > 0) {
            uploadArtifacts(uploadUrl, githubToken, artifacts);
        }
    }

    public ReleaseResponse fetchReleaseDetails(String repositoryUrl, String githubToken, String releaseTagName) throws IOException {
        String apiUrl = getApiUrl(repositoryUrl);

        HttpURLConnection connection = (HttpURLConnection) new URL(apiUrl + "/releases/tags/" + releaseTagName).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);
        connection.setRequestProperty("Content-Type", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to fetch release details: " + readStream(connection.getErrorStream()));
        }

        String response = readStream(connection.getInputStream());
        return parseReleaseResponse(response);
    }

    /**
     * Downloads an asset from a GitHub release with its ETag for optimistic locking.
     *
     * @param repositoryUrl The repository URL (e.g., https://github.com/user/repo)
     * @param githubToken The GitHub token for authentication
     * @param tag The release tag name
     * @param filename The asset filename to download
     * @return AssetWithETag containing the content, ETag, and asset ID
     * @throws GitHubReleaseNotFoundException if the release doesn't exist (404 on release)
     * @throws GitHubAssetNotFoundException if the asset doesn't exist (404 on asset, but release exists)
     * @throws IOException on other errors (network, 403, 500, etc.)
     */
    public AssetWithETag downloadAssetWithETag(String repositoryUrl, String githubToken, String tag, String filename) throws IOException {
        // First, fetch the release to get the asset details
        ReleaseResponse release;
        try {
            release = fetchReleaseDetails(repositoryUrl, githubToken, tag);
        } catch (IOException e) {
            // Check if it's a 404 (release not found)
            if (e.getMessage().contains("Failed to fetch release details")) {
                throw new GitHubReleaseNotFoundException("Release not found for tag: " + tag, e);
            }
            throw e;
        }

        // Find the asset by filename
        ReleaseAsset targetAsset = null;
        for (ReleaseAsset asset : release.getArtifacts()) {
            if (compareArtifactNames(asset.getFileName(), filename)) {
                targetAsset = asset;
                break;
            }
        }

        if (targetAsset == null) {
            throw new GitHubAssetNotFoundException(
                "Asset '" + filename + "' not found in release '" + tag + "'"
            );
        }

        // Download the asset and capture the ETag
        HttpURLConnection connection = (HttpURLConnection) new URL(targetAsset.getUrl()).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);
        connection.setRequestProperty("Accept", "application/octet-stream");

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Failed to download asset: " + readStream(connection.getErrorStream()));
        }

        // Capture the ETag from response headers
        String etag = connection.getHeaderField("ETag");
        if (etag != null && etag.startsWith("\"") && etag.endsWith("\"")) {
            // Remove quotes from ETag if present
            etag = etag.substring(1, etag.length() - 1);
        }

        // Read the content
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (InputStream is = connection.getInputStream()) {
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
        }

        return new AssetWithETag(buffer.toByteArray(), etag, targetAsset.getId());
    }

    public void uploadArtifacts(ReleaseResponse releaseResponse, String githubToken, File[] files) throws IOException {
        uploadArtifacts(releaseResponse, githubToken, files, false);
    }

    public void uploadArtifacts(ReleaseResponse releaseResponse, String githubToken, File[] files, boolean overwrite) throws IOException {
        for (File file : files) {
            uploadArtifact(releaseResponse, githubToken, file, overwrite);
        }
    }

    /**
     * Uploads an artifact with optimistic locking (conditional upload).
     * Uses If-Match header to ensure the asset hasn't been modified since it was last read.
     *
     * @param release The release to upload to
     * @param githubToken The GitHub token for authentication
     * @param artifact The artifact file to upload
     * @param ifMatchETag The ETag to match (for optimistic locking)
     * @throws ConcurrentPublishException if the ETag doesn't match (412 response)
     * @throws IOException on other errors
     */
    public void uploadArtifactConditional(
            ReleaseResponse release,
            String githubToken,
            File artifact,
            String ifMatchETag
    ) throws IOException {
        if (ifMatchETag == null) {
            throw new IllegalArgumentException(
                "ifMatchETag cannot be null for conditional upload. " +
                "This method should only be called with a valid ETag for optimistic locking."
            );
        }

        // First, delete the existing asset with the same name (if exists)
        deleteExistingAsset(release, githubToken, artifact.getName());

        // Upload the new artifact with If-Match header
        String uploadEndpoint = release.getUploadUrl() + "?name=" + artifact.getName();

        HttpURLConnection connection = (HttpURLConnection) new URL(uploadEndpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setRequestProperty("If-Match", "\"" + ifMatchETag + "\"");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream(); FileInputStream fis = new FileInputStream(artifact)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

        int responseCode = connection.getResponseCode();
        if (responseCode == 412) {
            // Precondition Failed - ETag doesn't match (concurrent modification detected)
            throw new ConcurrentPublishException(
                "Concurrent publish detected: The jdeploy tag was modified during this publish. " +
                "The asset '" + artifact.getName() + "' has been changed by another process."
            );
        }
        if (responseCode != 201) {
            throw new IOException("Failed to upload artifact: " + readStream(connection.getErrorStream()));
        }
    }

    private static String getApiUrl(String repositoryUrl) {
        if (!repositoryUrl.startsWith("https://github.com/")) {
            throw new IllegalArgumentException("Invalid GitHub repository URL");
        }
        return repositoryUrl.replace("https://github.com/", "https://api.github.com/repos/");
    }

    private static String escapeJson(String input) {
        return input.replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    private static String parseUploadUrl(String response) {
        int start = response.indexOf("\"upload_url\":\"") + 14;
        int end = response.indexOf('{', start);
        return response.substring(start, end).replace("{?name,label}", "");
    }

    private static void uploadArtifacts(String uploadUrl, String githubToken, File[] artifacts) throws IOException {
        for (File artifact : artifacts) {
            uploadArtifact(uploadUrl, githubToken, artifact);
        }
    }

    private static void uploadArtifact(ReleaseResponse releaseResponse, String githubToken, File artifact, boolean overwrite) throws IOException {
        if (overwrite) {
            deleteExistingAsset(releaseResponse, githubToken, artifact.getName());
        }
        uploadArtifact(releaseResponse.getUploadUrl(), githubToken, artifact);
    }

    private static void uploadArtifact(String uploadUrl, String githubToken, File artifact) throws IOException {

        String uploadEndpoint = uploadUrl + "?name=" + artifact.getName();

        HttpURLConnection connection = (HttpURLConnection) new URL(uploadEndpoint).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + githubToken);
        connection.setRequestProperty("Content-Type", "application/octet-stream");
        connection.setDoOutput(true);

        try (OutputStream os = connection.getOutputStream(); FileInputStream fis = new FileInputStream(artifact)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }

         int responseCode = connection.getResponseCode();
        if (responseCode != 201) {
            throw new IOException("Failed to upload artifact: " + readStream(connection.getErrorStream()));
        }
    }

    private static void deleteExistingAsset(ReleaseResponse releaseResponse, String githubToken, String fileName) throws IOException {
        for (ReleaseAsset artifact : releaseResponse.getArtifacts()) {
            if (compareArtifactNames(artifact.getFileName(), fileName)) {
                // Delete the asset
                String deleteUrl = artifact.getUrl();
                HttpURLConnection deleteConnection = (HttpURLConnection) new URL(deleteUrl).openConnection();
                deleteConnection.setRequestMethod("DELETE");
                deleteConnection.setRequestProperty("Authorization", "Bearer " + githubToken);

                int deleteResponseCode = deleteConnection.getResponseCode();
                if (deleteResponseCode != 204) {
                    throw new IOException("Failed to delete existing asset: " + readStream(deleteConnection.getErrorStream()));
                }
                break;
            }
        }
    }

    private ReleaseResponse parseReleaseResponse(String response) {
        JSONObject jsonResponse = new JSONObject(response);

        int id = jsonResponse.getInt("id");
        String body = jsonResponse.optString("body", "");
        String uploadUrl = jsonResponse.getString("upload_url").replace("{?name,label}", "");

        JSONArray assets = jsonResponse.optJSONArray("assets");
        List<ReleaseAsset> artifacts = new ArrayList<>();

        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject asset = assets.getJSONObject(i);
                artifacts.add(
                        new ReleaseAsset(
                            asset.getInt("id"),
                            asset.getString("name"),
                            asset.getString("url")
                    )
                );
            }
        }

        return new ReleaseResponse(id, body, artifacts, uploadUrl);
    }

    private static boolean compareArtifactNames(String githubName, String localFileName) {
        String sanitizedGithubName = sanitizeFileName(githubName);
        String sanitizedLocalName = sanitizeFileName(localFileName);
        return sanitizedGithubName.equalsIgnoreCase(sanitizedLocalName);
    }

    private static String sanitizeFileName(String fileName) {
        // Normalize and remove any non-alphanumeric characters except for dots and dashes
        return Normalizer.normalize(fileName, Normalizer.Form.NFD)
                .replaceAll("[^A-Za-z0-9.\\-]", "")
                .toLowerCase();
    }

    public static class ReleaseResponse {
        private final int id;
        private final String description;
        private final List<ReleaseAsset> artifacts;

        private final String uploadUrl;

        public ReleaseResponse(
                int id,
                String description,
                List<ReleaseAsset> artifactFileNames,
                String uploadUrl
        ) {
            this.id = id;
            this.description = description;
            this.artifacts = artifactFileNames;
            this.uploadUrl = uploadUrl;
        }

        public int getId() {
            return id;
        }

        public String getDescription() {
            return description;
        }

        public String getUploadUrl() {
            return uploadUrl;
        }

        public List<ReleaseAsset> getArtifacts() {
            return artifacts;
        }
    }

    public static class ReleaseAsset  {

        private final int id;

        private final String fileName;
        private final String url;

        public ReleaseAsset(int id, String fileName, String url) {
            this.id = id;
            this.fileName = fileName;
            this.url = url;
        }

        public int getId() {
            return id;
        }

        public String getFileName() {
            return fileName;
        }

        public String getUrl() {
            return url;
        }
    }

    /**
     * Represents an asset downloaded with its ETag for optimistic locking.
     */
    public static class AssetWithETag {
        private final byte[] content;
        private final String etag;
        private final long assetId;

        public AssetWithETag(byte[] content, String etag, long assetId) {
            this.content = content;
            this.etag = etag;
            this.assetId = assetId;
        }

        public byte[] getContent() {
            return content;
        }

        public String getETag() {
            return etag;
        }

        public long getAssetId() {
            return assetId;
        }
    }
}
