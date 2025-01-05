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
class CopyRuleIntegrationTest {

    private File srcDirectory;
    private File destDirectory;
    private PackagingContext context;

    @BeforeAll
    void setup() throws IOException {
        // Create temporary source and destination directories
        srcDirectory = Files.createTempDirectory("copyrule-src").toFile();
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-package");
        packageJSON.put("version", "1.0.0");
        packageJSON.put("main", "index.js");
        packageJSON.put("scripts", new JSONObject());
        packageJSON.put("dependencies", new JSONObject());
        packageJSON.put("jdeploy", new JSONObject());
        FileUtils.write(new File(srcDirectory, "package.json"), packageJSON.toString(), "UTF-8");
        destDirectory = Files.createTempDirectory("copyrule-dest").toFile();
        context = PackagingContext
                .builder()
                .directory(srcDirectory)
                .build();

        // Populate source directory with test files and directories
        new File(srcDirectory, "include-me.txt").createNewFile();
        new File(srcDirectory, "exclude-me.txt").createNewFile();
        new File(srcDirectory, "subdir").mkdirs();
        new File(srcDirectory, "subdir/include-me-too.txt").createNewFile();
    }

    @AfterAll
    void cleanup() throws IOException {
        FileUtils.deleteDirectory(srcDirectory);
        FileUtils.deleteDirectory(destDirectory);
    }

    @Test
    void testCopyWithIncludes() throws IOException {
        // Arrange
        List<String> includes = Arrays.asList("include-me.txt", "subdir/**");
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), includes, null, false);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, "include-me.txt").exists());
        assertTrue(new File(destDirectory, "subdir/include-me-too.txt").exists());
        assertFalse(new File(destDirectory, "exclude-me.txt").exists());
    }

    @Test
    void testCopyWithExcludes() throws IOException {
        // Arrange
        List<String> excludes = Arrays.asList("exclude-me.txt", "subdir/**");
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), null, excludes, false);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, "include-me.txt").exists());
        assertFalse(new File(destDirectory, "exclude-me.txt").exists());
        assertFalse(new File(destDirectory, "subdir/include-me-too.txt").exists());
    }

    @Test
    void testCopyWithIncludesAndExcludes() throws IOException {
        // Arrange
        List<String> includes = Arrays.asList("**"); // Include everything
        List<String> excludes = Arrays.asList("exclude-me.txt"); // Exclude specific file
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), includes, excludes, false);

        // Act
        copyRule.copyTo(destDirectory);

        // Assert
        assertTrue(new File(destDirectory, "include-me.txt").exists());
        assertFalse(new File(destDirectory, "exclude-me.txt").exists());
        assertTrue(new File(destDirectory, "subdir/include-me-too.txt").exists());
    }

    @Test
    void testCopyToNonExistentDestination() {
        // Arrange
        File nonExistentDest = new File(destDirectory, "non-existent-dest");
        List<String> includes = Arrays.asList("**");
        CopyRule copyRule = new CopyRule(context, srcDirectory.getPath(), includes, null, false);

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> copyRule.copyTo(nonExistentDest));
        assertTrue(exception.getMessage().contains("Destination directory of copy rule does not exist"));
    }

    @Test
    void testCopyFromNonExistentSource() {
        // Arrange
        File nonExistentSource = new File(srcDirectory, "non-existent-src");
        List<String> includes = Arrays.asList("**");
        CopyRule copyRule = new CopyRule(context, nonExistentSource.getPath(), includes, null, false);

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> copyRule.copyTo(destDirectory));
        assertTrue(exception.getMessage().contains("Source directory of copy rule does not exist"));
    }
}
