package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.builders.ProjectGeneratorRequestBuilder;
import ca.weblite.jdeploy.dtos.ProjectGeneratorRequest;
import ca.weblite.jdeploy.tests.helpers.MavenBuilder;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Map;

import static ca.weblite.jdeploy.cli.util.JavaVersionHelper.getJavaVersion;
import static org.junit.jupiter.api.Assertions.*;

class ProjectGeneratorTest {
    private File parentDirectory;

    private File templateDirectory;

    private ProjectGenerator projectGenerator;

    @BeforeEach
    public void setUp() throws Exception {
        parentDirectory = Files.createTempDirectory("jdeploy-test").toFile();
        templateDirectory = Files.createTempDirectory("jdeploy-test-template").toFile();
        projectGenerator = new DIContext().getInstance(ProjectGenerator.class);
        createTemplate(templateDirectory);

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
    public void testGenerate() {
        ProjectGeneratorRequestBuilder params = new ProjectGeneratorRequestBuilder();
        params.setParentDirectory(parentDirectory);
        params.setTemplateDirectory(templateDirectory.getPath());
        String mainClass = "MyClass";
        String packageName = "com.mycompany.myapp";
        String expectedGroupId = "com.mycompany";
        String expectedArtifactId = "myapp";
        params.setMagicArg(packageName + "." + mainClass);
        ProjectGeneratorRequest request = params.build();
        assertPrivateFieldEquals(request, "mainClassName", mainClass);
        assertPrivateFieldEquals(request, "packageName", packageName);
        assertPrivateFieldEquals(request, "parentDirectory", parentDirectory);
        assertPrivateFieldEquals(request, "templateDirectory", templateDirectory.getPath());
        assertPrivateFieldEquals(request, "groupId", expectedGroupId);
        assertPrivateFieldEquals(request, "artifactId", expectedArtifactId);
        assertPrivateFieldEquals(request, "projectName", "myapp");
        try {
            projectGenerator.generate(params.build());

        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        File projectDirectory = new File(parentDirectory, "myapp");
        File pomFile = new File(projectDirectory, "pom.xml");
        File mainClassFile = new File(projectDirectory, "src/main/java/com/mycompany/myapp/MyClass.java");
        File testClassFile = new File(projectDirectory, "src/test/java/com/mycompany/myapp/TestClass.java");
        File resourceFile = new File(projectDirectory, "src/main/resources/com/mycompany/myapp/resource.txt");
        File testResourceFile = new File(projectDirectory, "src/test/resources/com/mycompany/myapp/test-resource.txt");
        assertTrue(projectDirectory.exists());
        assertTrue(projectDirectory.isDirectory());
        assertTrue(pomFile.exists());
        assertTrue(mainClassFile.exists());
        assertTrue(testClassFile.exists());
        assertTrue(resourceFile.exists());
        assertTrue(testResourceFile.exists());


    }

    @Test
    @Disabled
    public void testGenerateJavafxOnStevesComputer() throws Exception{
        Assumptions.assumeTrue(getJavaVersion() >= 17, "Java version is less than 17");

        File javafxTemplate = new File("/Users/shannah/cn1_files/jdeploy-project-templates/javafx");
        ProjectGeneratorRequestBuilder params = new ProjectGeneratorRequestBuilder();
        params.setMagicArg("com.mycompany.myapp.Main");
        params.setParentDirectory(parentDirectory);
        params.setTemplateDirectory(javafxTemplate.getPath());
        ProjectGenerator generator = projectGenerator;
        generator.generate(params.build());
        File projectDirectory = new File(parentDirectory, "myapp");
        FileUtils.copyDirectory(projectDirectory, new File(System.getProperty("user.home") + "/Desktop/myapp"));
    }

    @Test
    public void testGenerateJavafx() throws Exception{
        Assumptions.assumeTrue(getJavaVersion() >= 17, "Java version is less than 17");

        ProjectGeneratorRequestBuilder params = new ProjectGeneratorRequestBuilder();
        params.setMagicArg("com.mycompany.myapp.Main");
        params.setParentDirectory(parentDirectory);
        params.setTemplateName("javafx");
        ProjectGenerator generator = projectGenerator;
        generator.generate(params.build());
        File projectDirectory = new File(parentDirectory, "myapp");
        DIContext.getInstance().getInstance(MavenBuilder.class).buildMavenProject(projectDirectory.getAbsolutePath());
    }

    @Test
    public void testGenerateSwing() throws Exception{
        ProjectGeneratorRequestBuilder params = new ProjectGeneratorRequestBuilder();
        params.setMagicArg("com.mycompany.myapp.Main");
        params.setParentDirectory(parentDirectory);
        params.setTemplateName("swing");
        ProjectGenerator generator = projectGenerator;
        generator.generate(params.build());
        File projectDirectory = new File(parentDirectory, "myapp");
        // Build Maven project
        DIContext.getInstance().getInstance(MavenBuilder.class).buildMavenProject(projectDirectory.getAbsolutePath());
    }

    @Test
    public void testGenerateWithCheerpj() throws Exception{
        ProjectGeneratorRequestBuilder params = new ProjectGeneratorRequestBuilder();
        params.setMagicArg("com.mycompany.myapp.Main");
        params.setParentDirectory(parentDirectory);
        params.setTemplateName("swing");
        params.setWithCheerpj(true);

        projectGenerator.generate(params.build());
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

    private void createTemplate(File templateDirectory) {
        File srcDir = new File(templateDirectory, "src");
        srcDir.mkdirs();
        File mainDir = new File(srcDir, "main");
        mainDir.mkdirs();
        File javaDir = new File(mainDir, "java");
        javaDir.mkdirs();
        File resourcesDir = new File(mainDir, "resources");
        resourcesDir.mkdirs();
        File testDir = new File(srcDir, "test");
        testDir.mkdirs();
        File javaTestDir = new File(testDir, "java");
        javaTestDir.mkdirs();
        File resourcesTestDir = new File(testDir, "resources");
        resourcesTestDir.mkdirs();

        File pomFile = new File(templateDirectory, "pom.xml");
        File srcPackageDir = new File(javaDir, "{{ packagePath }}");
        srcPackageDir.mkdirs();
        File mainClassFile = new File(srcPackageDir, "{{ mainClass }}.java");
        File testPackageDir = new File(javaTestDir, "{{ packagePath }}");
        testPackageDir.mkdirs();
        File testClassFile = new File(testPackageDir, "TestClass.java");
        File resourcePackageDir = new File(resourcesDir, "{{ packagePath }}");
        resourcePackageDir.mkdirs();
        File resourceFile = new File(resourcePackageDir, "resource.txt");
        File testResourcePackageDir = new File(resourcesTestDir, "{{ packagePath }}");
        testResourcePackageDir.mkdirs();
        File testResourceFile = new File(testResourcePackageDir, "test-resource.txt");

        try {
            FileUtils.writeStringToFile(pomFile, getPom());
            FileUtils.writeStringToFile(mainClassFile, getMainClass());
            FileUtils.writeStringToFile(testClassFile, getTestClass());
            FileUtils.writeStringToFile(resourceFile, getResource());
            FileUtils.writeStringToFile(testResourceFile, getTestResource());
        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }

    private String getPom() {
        return "<?xml version=\"1.0\"?>" +
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n" +
                "         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                "         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0\n" +
                "         http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n" +
                "    <modelVersion>4.0.0</modelVersion>\n" +
                "    <groupId>com.mycompany.myapp</groupId>\n" +
                "    <artifactId>myapp</artifactId>\n" +
                "    <version>1.0-SNAPSHOT</version>\n" +
                "    <properties>\n" +
                "        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>\n" +
                "        <maven.compiler.source>1.8</maven.compiler.source>\n" +
                "        <maven.compiler.target>1.8</maven.compiler.target>\n" +
                "    </properties>\n" +
                "    <dependencies>\n" +
                "        <dependency>\n" +
                "            <groupId>org.junit.jupiter</groupId>\n" +
                "            <artifactId>junit-jupiter-api</artifactId>\n" +
                "            <version>5.5.2</version>\n" +
                "            <scope>test</scope>\n" +
                "        </dependency>\n" +
                "        <dependency>\n" +
                "            <groupId>org.junit.jupiter</groupId>\n" +
                "            <artifactId>junit-jupiter-engine</artifactId>\n" +
                "            <version>5.5.2</version>\n" +
                "            <scope>test</scope>\n" +

                "        </dependency>\n" +
                "    </dependencies>\n" +
                "    <build>\n" +
                "        <plugins>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-compiler-plugin</artifactId>\n" +
                "                <version>3.8.1</version>\n" +
                "                <configuration>\n" +
                "                    <release>8</release>\n" +
                "                </configuration>\n" +
                "            </plugin>\n" +
                "            <plugin>\n" +
                "                <groupId>org.apache.maven.plugins</groupId>\n" +
                "                <artifactId>maven-surefire-plugin</artifactId>\n" +
                "                <version>2.22.2</version>\n" +
                "            </plugin>\n" +
                "        </plugins>\n" +
                "    </build>\n" +
                "</project>\n";

    }

    private String getMainClass() {
        return "package {{ packageName }};\n" +
                "\n" +
                "public class {{ mainClass }} {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(\"Hello World!\");\n" +
                "    }\n" +
                "}\n";
    }

    private String getTestClass() {
        return "package {{ packageName }};\n" +
                "\n" +
                "import org.junit.jupiter.api.Test;\n" +
                "\n" +
                "import static org.junit.jupiter.api.Assertions.*;\n" +
                "\n" +
                "class TestClass {\n" +
                "    @Test\n" +
                "    public void testSomething() {\n" +
                "        assertTrue(true);\n" +
                "    }\n" +
                "}\n";
    }

    private String getResource() {
        return "This is a resource file";
    }

    private String getTestResource() {
        return "This is a test resource file";
    }

    private void assertPrivateFieldEquals(Object obj, String fieldName, Object expectedValue) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            assertEquals(expectedValue, field.get(obj));
        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
    }


}

