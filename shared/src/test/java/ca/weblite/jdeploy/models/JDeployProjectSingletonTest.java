package ca.weblite.jdeploy.models;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for singleton mode accessors in JDeployProject.
 */
public class JDeployProjectSingletonTest {

    private JDeployProject project;
    private JSONObject packageJSON;

    @BeforeEach
    public void setUp() {
        packageJSON = new JSONObject();
    }

    private void createProject() {
        project = new JDeployProject(Paths.get("package.json"), packageJSON);
    }

    @Test
    public void testIsSingleton_DefaultFalse() {
        createProject();
        assertFalse(project.isSingleton());
    }

    @Test
    public void testIsSingleton_WhenTrue() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("singleton", true);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertTrue(project.isSingleton());
    }

    @Test
    public void testIsSingleton_WhenExplicitlyFalse() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("singleton", false);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        assertFalse(project.isSingleton());
    }

    @Test
    public void testSetSingleton_EnablesMode() {
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setSingleton(true);

        assertTrue(jdeploy.has("singleton"));
        assertTrue(jdeploy.getBoolean("singleton"));
    }

    @Test
    public void testSetSingleton_DisablesMode_RemovesField() {
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("singleton", true);
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setSingleton(false);

        assertFalse(jdeploy.has("singleton"));
    }

    @Test
    public void testSetSingleton_DisablesMode_WhenNotPresent() {
        JSONObject jdeploy = new JSONObject();
        packageJSON.put("jdeploy", jdeploy);

        createProject();
        project.setSingleton(false);

        assertFalse(jdeploy.has("singleton"));
    }
}
