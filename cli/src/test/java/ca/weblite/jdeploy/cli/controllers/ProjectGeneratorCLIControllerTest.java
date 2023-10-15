package ca.weblite.jdeploy.cli.controllers;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

public class ProjectGeneratorCLIControllerTest {

    private File parentDirectory;

    private File templateDirectory;

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
        if (templateDirectory != null && templateDirectory.exists()) {
            FileUtils.deleteDirectory(templateDirectory);
        }
    }

    @Test
    public void testGenerateSwing() throws Exception {
        System.out.println("Generating swing....");
        String[] args = new String[]{
                "com.mycompany.myapp.Main",
                "-d", parentDirectory.getAbsolutePath(),
                "-t", "swing"
        };
        ProjectGeneratorCLIController controller = new ProjectGeneratorCLIController(args);
        controller.run();

        File projectDirectory = new File(parentDirectory, "myapp");

        File pomFile = new File(projectDirectory, "pom.xml");

        // Build Maven project
        ProcessBuilder pb = new ProcessBuilder("mvn", "package")
                .directory(projectDirectory)
                .inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();
        assertEquals(0, exitCode);
    }

}
