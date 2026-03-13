package ca.weblite.jdeploy.installer.prebuilt;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrebuiltArtifactInfoTest {

    @Test
    void testFromJson_withAllFields() {
        JSONObject json = new JSONObject();
        json.put("url", "https://example.com/bundle.jar");
        json.put("sha256", "abc123");
        JSONObject cli = new JSONObject();
        cli.put("url", "https://example.com/bundle-cli.jar");
        cli.put("sha256", "def456");
        json.put("cli", cli);

        PrebuiltArtifactInfo info = PrebuiltArtifactInfo.fromJson(json);

        assertNotNull(info);
        assertEquals("https://example.com/bundle.jar", info.getUrl());
        assertEquals("abc123", info.getSha256());
        assertTrue(info.hasCli());
        assertEquals("https://example.com/bundle-cli.jar", info.getCliUrl());
        assertEquals("def456", info.getCliSha256());
    }

    @Test
    void testFromJson_withoutCli() {
        JSONObject json = new JSONObject();
        json.put("url", "https://example.com/bundle.jar");
        json.put("sha256", "abc123");

        PrebuiltArtifactInfo info = PrebuiltArtifactInfo.fromJson(json);

        assertNotNull(info);
        assertEquals("https://example.com/bundle.jar", info.getUrl());
        assertEquals("abc123", info.getSha256());
        assertFalse(info.hasCli());
        assertNull(info.getCliUrl());
        assertNull(info.getCliSha256());
    }

    @Test
    void testFromJson_missingUrl() {
        JSONObject json = new JSONObject();
        json.put("sha256", "abc123");

        PrebuiltArtifactInfo info = PrebuiltArtifactInfo.fromJson(json);
        assertNull(info);
    }

    @Test
    void testFromJson_missingSha256() {
        JSONObject json = new JSONObject();
        json.put("url", "https://example.com/bundle.jar");

        PrebuiltArtifactInfo info = PrebuiltArtifactInfo.fromJson(json);
        assertNull(info);
    }

    @Test
    void testFromJson_nullInput() {
        PrebuiltArtifactInfo info = PrebuiltArtifactInfo.fromJson(null);
        assertNull(info);
    }

    @Test
    void testFromJson_emptyObject() {
        PrebuiltArtifactInfo info = PrebuiltArtifactInfo.fromJson(new JSONObject());
        assertNull(info);
    }
}
