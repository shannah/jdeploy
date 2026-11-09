package ca.weblite.jdeploy.cli.controllers;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectGeneratorCLIControllerTest {

    private File parentDirectory;

    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("In setUp");
        parentDirectory = Files.createTempDirectory("jdeploy-test").toFile();
        System.out.println("Parent directory is " + parentDirectory.getAbsolutePath());

    }

    @AfterEach
    public void tearDown() throws Exception {
        if (parentDirectory != null && parentDirectory.exists()) {
            FileUtils.deleteDirectory(parentDirectory);
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    public void testGenerateSwing() throws Exception {
        String[] args = new String[]{
                "com.mycompany.myapp.Main",
                "-d", parentDirectory.getAbsolutePath(),
                "-t", "swing"
        };
        ProjectGeneratorCLIController controller = new ProjectGeneratorCLIController(args);
        controller.run();

        File projectDirectory = new File(parentDirectory, "myapp");

        File pomFile = new File(projectDirectory, "pom.xml");

        // Build Maven project. Capture its output rather than inheriting the console:
        // under Surefire the inherited output never reaches the build log, so a failed
        // build would otherwise give no reason.
        ProcessBuilder pb = new ProcessBuilder("mvn", "-B", "package")
                .directory(projectDirectory)
                .redirectErrorStream(true);
        Process p = pb.start();
        String output = IOUtils.toString(p.getInputStream(), StandardCharsets.UTF_8);
        int exitCode = p.waitFor();
        assertEquals(0, exitCode, "mvn package failed for the generated project:\n" + output);
    }

    @Test
    public void testGenerateSwingWithCheerpj() throws Exception {
        String[] args = new String[]{
                "com.mycompany.myapp.Main",
                "-d", parentDirectory.getAbsolutePath(),
                "-t", "swing",
                "--with-cheerpj"
        };
        ProjectGeneratorCLIController controller = new ProjectGeneratorCLIController(args);
        controller.run();

        File projectDirectory = new File(parentDirectory, "myapp");

        JSONObject packageJSON = new JSONObject(FileUtils.readFileToString(new File(projectDirectory, "package.json"), "UTF-8"));
        assertTrue(packageJSON.has("jdeploy"));
        JSONObject jdeploy = packageJSON.getJSONObject("jdeploy");
        assertTrue(jdeploy.has("cheerpj"));
        JSONObject cheerpj = jdeploy.getJSONObject("cheerpj");
        assertTrue(cheerpj.has("githubPages"));
        JSONObject githubPages = cheerpj.getJSONObject("githubPages");
        assertTrue(githubPages.has("enabled") && githubPages.getBoolean("enabled"));
        assertTrue(githubPages.has("branch") && githubPages.getString("branch").equals("gh-pages"));
        assertTrue(githubPages.has("path") && githubPages.getString("path").equals("app"));
    }
}
