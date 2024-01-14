package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.helpers.JSONHelper;
import ca.weblite.jdeploy.helpers.StringUtils;
import ca.weblite.jdeploy.records.PackageJsonInitializerContext;
import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class PackageJsonInitializerTest {

    private File projectDirectory;

    private PackageJsonInitializer packageJsonInitializer;
    private StringUtils stringUtils;
    private JSONHelper jsonHelper;

    @BeforeEach
    void setUp() throws IOException {
        projectDirectory = File.createTempFile("jdeploy-test", "project");
        projectDirectory.delete();
        if (!projectDirectory.mkdirs()) {
            throw new IOException("Failed to create temp directory");
        }
        packageJsonInitializer = DIContext.get(PackageJsonInitializer.class);
        stringUtils = DIContext.get(StringUtils.class);
        jsonHelper = DIContext.get(JSONHelper.class);
    }

    @AfterEach
    void tearDown() {
        try {
            FileUtils.deleteDirectory(projectDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    @Test
    void prepareWhenPackageJsonDoesNotExistYet() throws Exception {
        PackageJsonInitializerContext context = PackageJsonInitializerContext.builder()
                .setDirectory(projectDirectory)
                .createPackageJsonInitializerContext();

        PackageJsonInitializer.PreparationResult result = packageJsonInitializer.prepare(context);
        assertFalse(result.exists());
        assertFalse(result.getValidationResult().isValid());
        assertFalse(result.isValid());
        assertDefaultJson(result.getProposedPackageJsonToWrite(), false);
    }

    @Test
    void prepareWhenValidPackageJsonExists() throws Exception {
        String validPackageJson = IOUtil.readToString(getClass().getResourceAsStream("validPackageJson.json"));
        FileUtil.writeStringToFile(validPackageJson, new File(projectDirectory, "package.json"));
        PackageJsonInitializerContext context = PackageJsonInitializerContext.builder()
                .setDirectory(projectDirectory)
                .createPackageJsonInitializerContext();

        PackageJsonInitializer.PreparationResult result = packageJsonInitializer.prepare(context);
        assertTrue(result.exists());

        assertEquals(0, result.getValidationResult().getErrors().length);
        assertTrue(result.getValidationResult().isValid());
        assertTrue(result.isValid());
        assertPatchedValidJson(result.getProposedPackageJsonToWrite(), false);


    }

    @Test
    void prepareWhenInvalidPackageJsonExists() throws Exception {
        String validPackageJson = IOUtil.readToString(getClass().getResourceAsStream("validPackageJson.json"));
        JSONObject json = new JSONObject(validPackageJson);
        json.remove("description");
        FileUtil.writeStringToFile(json.toString(2), new File(projectDirectory, "package.json"));
        PackageJsonInitializerContext context = PackageJsonInitializerContext.builder()
                .setDirectory(projectDirectory)
                .createPackageJsonInitializerContext();

        PackageJsonInitializer.PreparationResult result = packageJsonInitializer.prepare(context);
        assertTrue(result.exists());
        assertEquals(1, result.getValidationResult().getErrors().length);
        assertFalse(result.getValidationResult().isValid());
        assertFalse(result.isValid());
        JSONObject override = new JSONObject();
        override.put("description", "");
        assertPatchedValidJson(result.getProposedPackageJsonToWrite(), false, override);
    }

    @Test
    void initializePackageJsonWhenValidPackageJsonAlreadyExists() throws Exception {
        String validPackageJson = IOUtil.readToString(getClass().getResourceAsStream("validPackageJson.json"));
        FileUtil.writeStringToFile(validPackageJson, new File(projectDirectory, "package.json"));
        PackageJsonInitializerContext context = PackageJsonInitializerContext.builder()
                .setDirectory(projectDirectory)
                .createPackageJsonInitializerContext();


        PackageJsonInitializer.InitializePackageJsonResult result = packageJsonInitializer.initializePackageJson(context);
        assertTrue(result.getPackageJsonFile().exists());

        String packageJsonAfter = FileUtil.readFileToString(result.getPackageJsonFile());
        assertEquals(validPackageJson, packageJsonAfter);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void initializePackageWithNoExistingPackageJson(int withJarFile) throws Exception {

        PackageJsonInitializerContext context = PackageJsonInitializerContext.builder()
                .setDirectory(projectDirectory)
                .createPackageJsonInitializerContext();

        if (withJarFile == 1) {
            File jarFile = new File(projectDirectory, "target/my-app.jar");
            jarFile.getParentFile().mkdirs();
            try (InputStream input =getClass().getResourceAsStream("my-app.jar")) {
                FileUtils.copyInputStreamToFile(input , jarFile);
            }
        }

        PackageJsonInitializer.InitializePackageJsonResult result = packageJsonInitializer.initializePackageJson(context);
        assertTrue(result.getPackageJsonFile().exists());

        String packageJsonAfter = FileUtil.readFileToString(result.getPackageJsonFile());
        assertDefaultJson(new JSONObject(packageJsonAfter), withJarFile == 1);

    }

    private void assertDependenciesCorrect(JSONObject json) {
        JSONObject dependencies = json.getJSONObject("dependencies");

        assertEquals("^2.10.0", dependencies.getString("yauzl"));
        assertEquals("^2.0.2", dependencies.getString("command-exists-promise"));
        assertEquals("2.6.7", dependencies.getString("node-fetch"));
        assertEquals("^4.4.8", dependencies.getString("tar"));
        assertEquals("^0.8.4", dependencies.getString("shelljs"));
    }

    private void assertDefaultJson(JSONObject json, boolean withJarFile) {
        assertEquals(withJarFile ? "my-app": projectDirectory.getName(), json.getString("name"));
        json.put("name", "my-app"); // so that we can compare the rest of the json
        assertEquals(
                withJarFile ? "My App" : stringUtils.toTitleCase(projectDirectory.getName()),
                jsonHelper.getAs(json, "jdeploy.title", String.class, null)
        );
        json.getJSONObject("jdeploy").put("title", "My App");
        if (withJarFile) {
            assertEquals("jdeploy-bundle/jdeploy.js", jsonHelper.getAs(json, "bin.my-app", String.class, null));
        } else {
            assertEquals("jdeploy-bundle/jdeploy.js", jsonHelper.getAs(json, "bin." + projectDirectory.getName(), String.class, null));
        }

        json.getJSONObject("bin").put("my-app", "jdeploy-bundle/jdeploy.js");
        json.getJSONObject("bin").remove(projectDirectory.getName());

        assertEquals("", jsonHelper.getAs(json, "description", String.class, null));
        assertEquals("index.js", json.getString("main"));
        assertTrue(json.getBoolean("preferGlobal"));
        assertEquals("", json.getString("repository"));
        assertEquals("1.0.0", json.getString("version"));
        JSONObject jdeploy = json.getJSONObject("jdeploy");
        assertFalse(jdeploy.getBoolean("jdk"));
        if (withJarFile) {
            assertEquals("target/my-app.jar", jdeploy.getString("jar"));
        } else {
            assertEquals("path/to/my-app.jar", jdeploy.getString("jar"));
        }
        assertFalse(jdeploy.getBoolean("javafx"));
        assertEquals(8, jdeploy.getInt("javaVersion"));
        assertDependenciesCorrect(json);
        JSONArray files = json.getJSONArray("files");
        assertEquals(1, files.length());
        assertEquals("jdeploy-bundle", files.getString(0));
    }

    private void assertPatchedValidJson(JSONObject json, boolean withJarFile) {
        assertPatchedValidJson(json, withJarFile, new JSONObject());
    }

    private void assertPatchedValidJson(JSONObject json, boolean withJarFile, JSONObject override) {
        assertEquals("test-swing11", json.getString("name"));
        assertEquals(
                "Test Swing11",
                jsonHelper.getAs(json, "jdeploy.title", String.class, null)
        );

        assertEquals("jdeploy-bundle/jdeploy.js", jsonHelper.getAs(json, "bin.test-swing11", String.class, null));

        if (override.has("description")) {
            assertEquals(override.getString("description"), json.getString("description"));
        } else {
            assertEquals("jDeploy Swing Starter Project", json.getString("description"));
        }
        assertEquals("index.js", json.getString("main"));
        assertTrue(json.getBoolean("preferGlobal"));
        assertEquals("", json.getString("repository"));
        assertEquals("1.0-SNAPSHOT", json.getString("version"));
        JSONObject jdeploy = json.getJSONObject("jdeploy");
        assertFalse(jdeploy.getBoolean("jdk"));
        assertEquals("target/test-swing11-1.0-SNAPSHOT.jar", jdeploy.getString("jar"));

        assertFalse(jdeploy.getBoolean("javafx"));
        assertEquals("17", jdeploy.getString("javaVersion"));
        assertDependenciesCorrect(json);
        JSONArray files = json.getJSONArray("files");
        assertEquals(1, files.length());
        assertEquals("jdeploy-bundle", files.getString(0));
    }
}