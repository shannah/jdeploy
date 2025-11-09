package ca.weblite.jdeploy.installer.npm;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class NPMPackageTest {

    @Test
    public void testGetLatestVersionWithVPrefix() {
        // Create a mock package-info.json with versions
        JSONObject packageInfo = new JSONObject();

        // Add versions object
        JSONObject versions = new JSONObject();
        versions.put("2.0.36", new JSONObject().put("description", "Test version"));
        versions.put("2.0.35", new JSONObject().put("description", "Older version"));
        packageInfo.put("versions", versions);

        // Add time object (required for sorting)
        JSONObject time = new JSONObject();
        time.put("2.0.36", "2024-01-02T00:00:00.000Z");
        time.put("2.0.35", "2024-01-01T00:00:00.000Z");
        packageInfo.put("time", time);

        // Add dist-tags (required for "latest")
        JSONObject distTags = new JSONObject();
        distTags.put("latest", "2.0.36");
        packageInfo.put("dist-tags", distTags);

        NPMPackage pkg = new NPMPackage(packageInfo);

        // Test 1: Version with "v" prefix should match version without "v" prefix
        NPMPackageVersion result = pkg.getLatestVersion(false, "v2.0.36");
        assertNotNull("Should find version 2.0.36 when searching for v2.0.36", result);
        assertEquals("Version should be 2.0.36", "2.0.36", result.getVersion());

        // Test 2: Version without "v" prefix should still work
        result = pkg.getLatestVersion(false, "2.0.36");
        assertNotNull("Should find version 2.0.36 when searching for 2.0.36", result);
        assertEquals("Version should be 2.0.36", "2.0.36", result.getVersion());

        // Test 3: Non-existent version should return null
        result = pkg.getLatestVersion(false, "v9.9.9");
        assertNull("Should not find non-existent version v9.9.9", result);
    }

    @Test
    public void testGetLatestVersionSpecialVersions() {
        JSONObject packageInfo = new JSONObject();

        // Add versions object with special versions
        JSONObject versions = new JSONObject();
        versions.put("vnext", new JSONObject().put("description", "Next version"));
        versions.put("2.0.36", new JSONObject().put("description", "Regular version"));
        packageInfo.put("versions", versions);

        // Add time object
        JSONObject time = new JSONObject();
        time.put("vnext", "2024-01-02T00:00:00.000Z");
        time.put("2.0.36", "2024-01-01T00:00:00.000Z");
        packageInfo.put("time", time);

        // Add dist-tags
        JSONObject distTags = new JSONObject();
        distTags.put("latest", "2.0.36");
        packageInfo.put("dist-tags", distTags);

        NPMPackage pkg = new NPMPackage(packageInfo);

        // Test: "vnext" should NOT be normalized (not followed by digit)
        NPMPackageVersion result = pkg.getLatestVersion(false, "vnext");
        assertNotNull("Should find vnext without stripping 'v'", result);
        assertEquals("Version should be vnext", "vnext", result.getVersion());
    }

    @Test
    public void testGetLatestVersionBranchRelease() {
        JSONObject packageInfo = new JSONObject();

        // Add versions object with branch-based release
        JSONObject versions = new JSONObject();
        versions.put("0.0.0-master-snapshot", new JSONObject().put("description", "Branch version"));
        packageInfo.put("versions", versions);

        // Add time object
        JSONObject time = new JSONObject();
        time.put("0.0.0-master-snapshot", "2024-01-01T00:00:00.000Z");
        packageInfo.put("time", time);

        // Add dist-tags
        JSONObject distTags = new JSONObject();
        distTags.put("latest", "0.0.0-master-snapshot");
        packageInfo.put("dist-tags", distTags);

        NPMPackage pkg = new NPMPackage(packageInfo);

        // Test: Branch release should work
        NPMPackageVersion result = pkg.getLatestVersion(false, "0.0.0-master-snapshot");
        assertNotNull("Should find branch release", result);
        assertEquals("Version should be 0.0.0-master-snapshot", "0.0.0-master-snapshot", result.getVersion());
    }
}
