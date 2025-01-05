package ca.weblite.jdeploy.packaging;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CopyRuleEscapeIntegrationTest {

    private File srcDirectory;
    private File destDirectory;
    private PackagingContext context;

    @BeforeEach
    void setup() throws IOException {
        // Create temporary source and destination directories
        srcDirectory = Files.createTempDirectory("copyrule-src").toFile();
        destDirectory = Files.createTempDirectory("copyrule-dest").toFile();
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-package");
        packageJSON.put("version", "1.0.0");
        packageJSON.put("main", "index.js");
        packageJSON.put("scripts", new JSONObject());
        packageJSON.put("dependencies", new JSONObject());
        packageJSON.put("jdeploy", new JSONObject());
        FileUtils.write(new File(srcDirectory, "package.json"), packageJSON.toString(), "UTF-8");
        context = PackagingContext.builder().directory(srcDirectory).build();

        // Populate source directory with files
        new File(srcDirectory, "my-jar-1.0.0.jar").createNewFile();
        new File(srcDirectory, "javafx-runtime.jar").createNewFile();

        // Create nested directory
        File nestedDir = new File(srcDirectory, "libs");
        nestedDir.mkdirs();
        new File(nestedDir, "dependency-1.0.0.jar").createNewFile();
        new File(nestedDir, "dependency-2.0.0.jar").createNewFile();
    }

    @AfterEach
    void cleanup() throws IOException {
        FileUtils.deleteDirectory(srcDirectory);
        FileUtils.deleteDirectory(destDirectory);
    }

    @Test
    void testCopySingleFileWithEscape() throws IOException {
        // Arrange
        String jarFileName = "my-jar-1.0.0.jar";
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), jarFileName, null, true);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, jarFileName).exists());
        assertFalse(new File(destDirectory, "javafx-runtime.jar").exists());
    }

    @Test
    void testCopyFileWithSpecialCharacters() throws IOException {
        // Arrange
        String specialFileName = "my-jar-[1.0.0].jar";
        File specialFile = new File(srcDirectory, specialFileName);
        specialFile.createNewFile();

        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), specialFileName, null, true);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, specialFileName).exists());
    }

    @Test
    void testCopyNestedFileWithEscape() throws IOException {
        // Arrange
        String nestedFilePath = "libs/dependency-1.0.0.jar";
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), nestedFilePath, null, true);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, nestedFilePath).exists());
        assertFalse(new File(destDirectory, "libs/dependency-2.0.0.jar").exists());
    }

    @Test
    void testCopyMultipleSpecificFilesWithEscape() throws IOException {
        // Arrange
        List<String> includes = Arrays.asList("my-jar-1.0.0.jar", "libs/dependency-2.0.0.jar");
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), includes, null, true);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, "my-jar-1.0.0.jar").exists());
        assertTrue(new File(destDirectory, "libs/dependency-2.0.0.jar").exists());
        assertFalse(new File(destDirectory, "javafx-runtime.jar").exists());
        assertFalse(new File(destDirectory, "libs/dependency-1.0.0.jar").exists());
    }

    @Test
    void testCopyExcludingSpecificFileWithEscape() throws IOException {
        // Arrange
        List<String> excludes = Arrays.asList("libs/dependency-1.0.0.jar");
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), null, excludes, true);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, "my-jar-1.0.0.jar").exists());
        assertTrue(new File(destDirectory, "javafx-runtime.jar").exists());
        assertTrue(new File(destDirectory, "libs/dependency-2.0.0.jar").exists());
        assertFalse(new File(destDirectory, "libs/dependency-1.0.0.jar").exists());
    }
}
