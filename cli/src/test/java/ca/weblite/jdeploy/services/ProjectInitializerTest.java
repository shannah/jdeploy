package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.helpers.JSONHelper;
import ca.weblite.jdeploy.helpers.StringUtils;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ProjectInitializerTest {

    private File projectDirectory;

    private ProjectInitializer projectInitializer;

    private StringUtils stringUtils;

    private JSONHelper jsonHelper;

    private PackageJsonValidator packageJsonValidator;



    @BeforeEach
    void setUp() throws IOException {
        projectDirectory = File.createTempFile("jdeploy-test", "project");
        projectDirectory.delete();
        if (!projectDirectory.mkdirs()) {
            throw new IOException("Failed to create temp directory");
        }
        projectInitializer = DIContext.get(ProjectInitializer.class);
        stringUtils = DIContext.get(StringUtils.class);
        jsonHelper = DIContext.get(JSONHelper.class);
        packageJsonValidator = DIContext.get(PackageJsonValidator.class);
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
    void testPrepareOnEmptyDirectory() throws Exception {
        ProjectInitializer.Context context = new ProjectInitializer.ContextBuilder()
                .setDirectory(projectDirectory)
                .build();

        ProjectInitializer.PreparationResult result = projectInitializer.prepare(context);
        assertFalse(result.isPackageJsonExists());
        assertFalse(result.isPackageJsonIsValid());
        assertFalse(result.isGithubWorkflowExists());
    }

    @Test
    void testPrepareOnDirectoryWithPackageJson() throws Exception {
        File packageJsonFile = new File(projectDirectory, "package.json");
        FileUtils.writeStringToFile(packageJsonFile, "{}");
        ProjectInitializer.Context context = new ProjectInitializer.ContextBuilder()
                .setDirectory(projectDirectory)
                .build();

        ProjectInitializer.PreparationResult result = projectInitializer.prepare(context);
        assertTrue(result.isPackageJsonExists());
        assertFalse(result.isPackageJsonIsValid());
        assertFalse(result.isGithubWorkflowExists());
    }

    @Test
    void testInitializeProject() throws Exception {
        File packageJsonFile = new File(projectDirectory, "package.json");
        FileUtils.writeStringToFile(packageJsonFile, "{}");
        ProjectInitializer.Context context = new ProjectInitializer.ContextBuilder()
                .setDirectory(projectDirectory)
                .build();

        ProjectInitializer.PreparationResult result = projectInitializer.prepare(context);
        assertTrue(result.isPackageJsonExists());
        assertFalse(result.isPackageJsonIsValid());
        assertFalse(result.isGithubWorkflowExists());

        ProjectInitializer.InitializeProjectResult initResult = projectInitializer.initializeProject(context);

        packageJsonValidator.validate(packageJsonFile);

    }

}