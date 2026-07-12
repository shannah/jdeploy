package ca.weblite.jdeploy.publishing;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BasePublishDriver#resolveMinInitialAppVersionSentinel} — the
 * publish-time resolution of the "auto-set to latest" minimum initial app version.
 */
@DisplayName("BasePublishDriver minInitialAppVersion sentinel resolution")
public class BasePublishDriverSentinelTest {

    @Test
    @DisplayName("Resolves the latest sentinel to the published version")
    void testResolvesLatestSentinel() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("version", "2.5.0");
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("minLauncherInitialAppVersionMode", "latest");
        packageJSON.put("jdeploy", jdeploy);

        BasePublishDriver.resolveMinInitialAppVersionSentinel(packageJSON, jdeploy);

        assertEquals("2.5.0", jdeploy.getString("minLauncherInitialAppVersion"));
        assertFalse(jdeploy.has("minLauncherInitialAppVersionMode"),
                "sentinel mode key should be removed after resolution");
    }

    @Test
    @DisplayName("Leaves an explicit minimum version untouched")
    void testLeavesExplicitVersion() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("version", "2.5.0");
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("minLauncherInitialAppVersion", "1.0.0");
        packageJSON.put("jdeploy", jdeploy);

        BasePublishDriver.resolveMinInitialAppVersionSentinel(packageJSON, jdeploy);

        assertEquals("1.0.0", jdeploy.getString("minLauncherInitialAppVersion"));
        assertFalse(jdeploy.has("minLauncherInitialAppVersionMode"));
    }

    @Test
    @DisplayName("Does nothing when no sentinel and no explicit version are present")
    void testNoOpWhenAbsent() {
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("version", "2.5.0");
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);

        BasePublishDriver.resolveMinInitialAppVersionSentinel(packageJSON, jdeploy);

        assertFalse(jdeploy.has("minLauncherInitialAppVersion"));
        assertFalse(jdeploy.has("minLauncherInitialAppVersionMode"));
    }

    @Test
    @DisplayName("Does not resolve the sentinel when the version is missing")
    void testNoVersion() {
        JSONObject packageJSON = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("minLauncherInitialAppVersionMode", "latest");
        packageJSON.put("jdeploy", jdeploy);

        BasePublishDriver.resolveMinInitialAppVersionSentinel(packageJSON, jdeploy);

        // With no version to substitute, the sentinel is preserved unresolved.
        assertEquals("latest", jdeploy.getString("minLauncherInitialAppVersionMode"));
        assertFalse(jdeploy.has("minLauncherInitialAppVersion"));
    }
}
