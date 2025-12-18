package ca.weblite.jdeploy.packaging;

import ca.weblite.tools.io.FileUtil;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CopyJarRuleBuilderIntegrationTest {

    private static File tempDir;
    private static File jarFile;
    private static ClassPathFinder classPathFinder;
    private static CopyJarRuleBuilder copyJarRuleBuilder;
    private static PackagingContext context;

    @BeforeClass
    public static void setup() throws IOException {
        // Create a temporary directory for testing
        tempDir = Files.createTempDirectory("copy-jar-rule-builder-test").toFile();
        JSONObject packageJSON = new JSONObject();
        packageJSON.put("name", "test-package");
        packageJSON.put("version", "1.0.0");
        packageJSON.put("main", "index.js");
        packageJSON.put("scripts", new JSONObject());
        packageJSON.put("dependencies", new JSONObject());
        packageJSON.put("jdeploy", new JSONObject());
        FileUtils.write(new File(tempDir, "package.json"), packageJSON.toString(), "UTF-8");

        // Create a mock JAR file
        jarFile = new File(tempDir, "test.jar");
        jarFile.createNewFile();

        // Mock ClassPathFinder
        classPathFinder = mock(ClassPathFinder.class);

        // Create an instance of CopyJarRuleBuilder
        copyJarRuleBuilder = new CopyJarRuleBuilder(classPathFinder);

        // Mock PackagingContext
        context = mock(PackagingContext.class);
        when(context.getList("mavenDependencies", true)).thenReturn(Collections.emptyList());
        when(context.getString("javafx", "false")).thenReturn("false");
        when(context.getString("stripJavaFXFiles", "true")).thenReturn("true");
        when(context.getString("javafxVersion", "")).thenReturn("");
    }

    @AfterClass
    public static void cleanup() throws IOException {
        FileUtils.deleteDirectory(tempDir);
    }

    @Test
    public void testBuildWithBasicJarFile() throws IOException {
        // Arrange
        when(classPathFinder.findClassPath(jarFile)).thenReturn(Collections.emptyList().toArray(new String[0]));

        // Act
        List<CopyRule> rules = copyJarRuleBuilder.build(context, jarFile);

        // Assert
        assertNotNull(rules);
        assertEquals(1, rules.size());
        CopyRule rule = rules.get(0);
        assertEquals(jarFile.getParent(), rule.dir);
        assertEquals(jarFile.getName(), rule.includes.get(0));
    }

    @Test
    public void testBuildWithClassPath() throws IOException {
        // Arrange
        File dependency = new File(tempDir, "dependency.jar");
        dependency.createNewFile();
        when(classPathFinder.findClassPath(jarFile)).thenReturn(Collections.singletonList(dependency.getPath()).toArray(new String[0]));

        // Act
        List<CopyRule> rules = copyJarRuleBuilder.build(context, jarFile);

        // Assert
        assertNotNull(rules);
        assertEquals(2, rules.size());
        assertTrue(rules.stream().anyMatch(rule -> rule.includes.contains(CopyRule.escapeGlob(dependency.getAbsolutePath()))));
    }

    @Test
    public void testBuildWithJavaFXFilesExcluded() throws IOException {
        // Arrange
        File javafxJar = new File(tempDir, "javafx-runtime.jar");
        javafxJar.createNewFile();
        when(classPathFinder.findClassPath(jarFile)).thenReturn(Collections.singletonList(javafxJar.getPath()).toArray(new String[0]));
        when(context.getString("javafx", "false")).thenReturn("true");
        when(context.getString("stripJavaFXFiles", "true")).thenReturn("true");

        // Act
        List<CopyRule> rules = copyJarRuleBuilder.build(context, jarFile);

        // Assert
        assertNotNull(rules);
        assertEquals(1, rules.size());
        assertFalse(rules.stream().anyMatch(rule -> rule.includes.contains(javafxJar.getName())));
    }

    @Test
    public void testBuildWithMavenDependencies() throws IOException {
        // Arrange
        File dependency = new File(tempDir, "dependency.jar");
        dependency.createNewFile();
        File strippedDependency = new File(tempDir, "dependency.jar.stripped");
        when(classPathFinder.findClassPath(jarFile)).thenReturn(Collections.singletonList(dependency.getPath()).toArray(new String[0]));
        when(context.getList("mavenDependencies", true)).thenReturn(Collections.singletonList("maven-dependency"));

        // Act
        List<CopyRule> rules = copyJarRuleBuilder.build(context, jarFile);

        // Assert
        assertNotNull(rules);
        assertTrue(strippedDependency.exists());
        assertTrue(rules.stream().anyMatch(rule -> rule.includes.contains(CopyRule.escapeGlob(strippedDependency.getPath()))));
    }
}
