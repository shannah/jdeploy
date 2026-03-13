package ca.weblite.jdeploy.installer.prebuilt;

import org.json.JSONObject;

public class PrebuiltArtifactInfo {
    private final String url;
    private final String sha256;
    private final String cliUrl;
    private final String cliSha256;

    public PrebuiltArtifactInfo(String url, String sha256, String cliUrl, String cliSha256) {
        this.url = url;
        this.sha256 = sha256;
        this.cliUrl = cliUrl;
        this.cliSha256 = cliSha256;
    }

    public static PrebuiltArtifactInfo fromJson(JSONObject artifactJson) {
        if (artifactJson == null) {
            return null;
        }
        String url = artifactJson.optString("url", null);
        String sha256 = artifactJson.optString("sha256", null);
        if (url == null || sha256 == null) {
            return null;
        }

        String cliUrl = null;
        String cliSha256 = null;
        JSONObject cli = artifactJson.optJSONObject("cli");
        if (cli != null) {
            cliUrl = cli.optString("url", null);
            cliSha256 = cli.optString("sha256", null);
        }

        return new PrebuiltArtifactInfo(url, sha256, cliUrl, cliSha256);
    }

    public String getUrl() {
        return url;
    }

    public String getSha256() {
        return sha256;
    }

    public String getCliUrl() {
        return cliUrl;
    }

    public String getCliSha256() {
        return cliSha256;
    }

    public boolean hasCli() {
        return cliUrl != null && cliSha256 != null;
    }
}
