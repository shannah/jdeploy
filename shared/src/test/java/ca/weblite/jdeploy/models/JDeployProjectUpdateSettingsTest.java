package ca.weblite.jdeploy.models;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for update-settings accessors in JDeployProject (appUpdateMode,
 * minLauncherInitialAppVersion, minLauncherInitialAppVersionMode, requireLauncherUpdate).
 */
public class JDeployProjectUpdateSettingsTest {

    private JDeployProject project;
    private JSONObject packageJSON;

    @BeforeEach
    public void setUp() {
        packageJSON = new JSONObject();
    }

    private void createProject() {
        project = new JDeployProject(Paths.get("package.json"), packageJSON);
    }

    private JSONObject jdeploy() {
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);
        return jdeploy;
    }

    @Test
    public void testAppUpdateMode_DefaultAuto() {
        createProject();
        assertEquals("auto", project.getAppUpdateMode());
    }

    @Test
    public void testAppUpdateMode_Prompt() {
        jdeploy().put("appUpdateMode", "prompt");
        createProject();
        assertEquals("prompt", project.getAppUpdateMode());
    }

    @Test
    public void testMinLauncherInitialAppVersion_DefaultEmpty() {
        createProject();
        assertEquals("", project.getMinLauncherInitialAppVersion());
    }

    @Test
    public void testMinLauncherInitialAppVersion_Explicit() {
        jdeploy().put("minLauncherInitialAppVersion", "1.4.0");
        createProject();
        assertEquals("1.4.0", project.getMinLauncherInitialAppVersion());
    }

    @Test
    public void testMinLauncherInitialAppVersionMode_DefaultEmpty() {
        createProject();
        assertEquals("", project.getMinLauncherInitialAppVersionMode());
    }

    @Test
    public void testMinLauncherInitialAppVersionMode_Latest() {
        jdeploy().put("minLauncherInitialAppVersionMode", "latest");
        createProject();
        assertEquals("latest", project.getMinLauncherInitialAppVersionMode());
    }

    @Test
    public void testRequireLauncherUpdate_DefaultFalse() {
        createProject();
        assertFalse(project.isRequireLauncherUpdate());
    }

    @Test
    public void testRequireLauncherUpdate_True() {
        jdeploy().put("requireLauncherUpdate", true);
        createProject();
        assertTrue(project.isRequireLauncherUpdate());
    }

    @Test
    public void testDefaults_WhenNoJdeployObject() {
        createProject();
        assertEquals("auto", project.getAppUpdateMode());
        assertEquals("", project.getMinLauncherInitialAppVersion());
        assertEquals("", project.getMinLauncherInitialAppVersionMode());
        assertFalse(project.isRequireLauncherUpdate());
    }
}
