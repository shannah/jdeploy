package ca.weblite.jdeploy.services;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProjectTypeDetectionServiceTest {

    private ProjectTypeDetectionService service;
    
    @TempDir
    File tempDir;
    
    @BeforeEach
    void setUp() {
        service = new ProjectTypeDetectionService();
    }
    
    @Test
    void testDetectMavenProject() throws IOException {
        File pomFile = new File(tempDir, "pom.xml");
        FileUtils.writeStringToFile(pomFile, 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <modelVersion>4.0.0</modelVersion>\n" +
            "    <groupId>com.example</groupId>\n" +
            "    <artifactId>test-app</artifactId>\n" +
            "    <version>1.0.0</version>\n" +
            "</project>", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.MAVEN, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.PLAIN_JAVA, projectType.getFramework());
        assertFalse(projectType.isMultiModule());
    }
    
    @Test
    void testDetectGradleProject() throws IOException {
        File buildFile = new File(tempDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, 
            "plugins {\n" +
            "    id 'java'\n" +
            "}\n" +
            "dependencies {\n" +
            "    implementation 'org.springframework.boot:spring-boot-starter:2.7.0'\n" +
            "}", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.GRADLE, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.SPRING_BOOT, projectType.getFramework());
        assertFalse(projectType.isMultiModule());
    }
    
    @Test
    void testDetectAntProject() throws IOException {
        File buildFile = new File(tempDir, "build.xml");
        FileUtils.writeStringToFile(buildFile, 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project name=\"test-project\" default=\"compile\">\n" +
            "    <target name=\"compile\">\n" +
            "        <javac srcdir=\"src\" destdir=\"build/classes\"/>\n" +
            "    </target>\n" +
            "</project>", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.ANT, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.PLAIN_JAVA, projectType.getFramework());
        assertFalse(projectType.isMultiModule());
    }
    
    @Test
    void testDetectJavaFXProject() throws IOException {
        File pomFile = new File(tempDir, "pom.xml");
        FileUtils.writeStringToFile(pomFile, 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">\n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>org.openjfx</groupId>\n" +
            "            <artifactId>javafx-controls</artifactId>\n" +
            "            <version>17.0.2</version>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "</project>", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.MAVEN, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.JAVAFX, projectType.getFramework());
        assertTrue(service.isJavaFXProject(tempDir));
    }
    
    @Test
    void testDetectComposeMultiplatformProject() throws IOException {
        File buildFile = new File(tempDir, "build.gradle.kts");
        FileUtils.writeStringToFile(buildFile, 
            "plugins {\n" +
            "    kotlin(\"multiplatform\")\n" +
            "    id(\"org.jetbrains.compose\")\n" +
            "}\n" +
            "kotlin {\n" +
            "    jvm()\n" +
            "    sourceSets {\n" +
            "        val commonMain by getting {\n" +
            "            dependencies {\n" +
            "                implementation(compose.desktop.currentOs)\n" +
            "            }\n" +
            "        }\n" +
            "    }\n" +
            "}", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.GRADLE, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.COMPOSE_MULTIPLATFORM, projectType.getFramework());
        assertTrue(service.isComposeMultiplatformProject(tempDir));
    }
    
    @Test
    void testDetectLibGDXProject() throws IOException {
        File buildFile = new File(tempDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, 
            "dependencies {\n" +
            "    implementation \"com.badlogicgames.gdx:gdx:1.11.0\"\n" +
            "    implementation \"com.badlogicgames.gdx:gdx-backend-lwjgl3:1.11.0\"\n" +
            "}", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.GRADLE, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.LIBGDX, projectType.getFramework());
    }
    
    @Test
    void testDetectFXGLProject() throws IOException {
        File pomFile = new File(tempDir, "pom.xml");
        FileUtils.writeStringToFile(pomFile, 
            "<project>\n" +
            "    <dependencies>\n" +
            "        <dependency>\n" +
            "            <groupId>com.github.almasb</groupId>\n" +
            "            <artifactId>fxgl</artifactId>\n" +
            "            <version>17.2</version>\n" +
            "        </dependency>\n" +
            "    </dependencies>\n" +
            "</project>", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.MAVEN, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.FXGL, projectType.getFramework());
    }
    
    @Test
    void testDetectMavenMultiModule() throws IOException {
        File pomFile = new File(tempDir, "pom.xml");
        FileUtils.writeStringToFile(pomFile, 
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project>\n" +
            "    <packaging>pom</packaging>\n" +
            "    <modules>\n" +
            "        <module>core</module>\n" +
            "        <module>web</module>\n" +
            "    </modules>\n" +
            "</project>", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.MAVEN, projectType.getBuildTool());
        assertTrue(projectType.isMultiModule());
    }
    
    @Test
    void testDetectGradleMultiModule() throws IOException {
        File settingsFile = new File(tempDir, "settings.gradle");
        FileUtils.writeStringToFile(settingsFile, 
            "rootProject.name = 'multi-module-app'\n" +
            "include ':core'\n" +
            "include ':web'\n" +
            "include ':desktop'", StandardCharsets.UTF_8);
        
        File buildFile = new File(tempDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, "// root build file", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.GRADLE, projectType.getBuildTool());
        assertTrue(projectType.isMultiModule());
    }
    
    @Test
    void testDetectAntMultiModule() throws IOException {
        File buildFile = new File(tempDir, "build.xml");
        FileUtils.writeStringToFile(buildFile, "<project name=\"root\"/>", StandardCharsets.UTF_8);
        
        File coreDir = new File(tempDir, "core");
        coreDir.mkdirs();
        File coreBuildFile = new File(coreDir, "build.xml");
        FileUtils.writeStringToFile(coreBuildFile, "<project name=\"core\"/>", StandardCharsets.UTF_8);
        
        File webDir = new File(tempDir, "web");
        webDir.mkdirs();
        File webBuildFile = new File(webDir, "build.xml");
        FileUtils.writeStringToFile(webBuildFile, "<project name=\"web\"/>", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.ANT, projectType.getBuildTool());
        assertTrue(projectType.isMultiModule());
    }
    
    @Test
    void testDetectUnknownProject() {
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.UNKNOWN, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.UNKNOWN, projectType.getFramework());
        assertFalse(projectType.isMultiModule());
    }
    
    @Test
    void testNullDirectory() {
        ProjectType projectType = service.detectProjectType(null);
        
        assertEquals(ProjectType.BuildTool.UNKNOWN, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.UNKNOWN, projectType.getFramework());
        assertFalse(projectType.isMultiModule());
    }
    
    @Test
    void testNonExistentDirectory() {
        File nonExistent = new File(tempDir, "does-not-exist");
        ProjectType projectType = service.detectProjectType(nonExistent);
        
        assertEquals(ProjectType.BuildTool.UNKNOWN, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.UNKNOWN, projectType.getFramework());
        assertFalse(projectType.isMultiModule());
    }
    
    @Test
    void testDetectFrameworkFromJavaSource() throws IOException {
        File buildFile = new File(tempDir, "build.gradle");
        FileUtils.writeStringToFile(buildFile, 
            "plugins {\n" +
            "    id 'java'\n" +
            "    id 'org.openjfx.javafxplugin' version '0.0.13'\n" +
            "}\n" +
            "dependencies {\n" +
            "    implementation 'org.openjfx:javafx-controls:17.0.2'\n" +
            "}", StandardCharsets.UTF_8);
        
        File srcDir = new File(tempDir, "src/main/java/com/example");
        srcDir.mkdirs();
        
        File javaFile = new File(srcDir, "Main.java");
        FileUtils.writeStringToFile(javaFile, 
            "package com.example;\n" +
            "import javafx.application.Application;\n" +
            "import javafx.stage.Stage;\n" +
            "\n" +
            "public class Main extends Application {\n" +
            "    @Override\n" +
            "    public void start(Stage primaryStage) {\n" +
            "        // JavaFX app\n" +
            "    }\n" +
            "}", StandardCharsets.UTF_8);
        
        ProjectType projectType = service.detectProjectType(tempDir);
        
        assertEquals(ProjectType.BuildTool.GRADLE, projectType.getBuildTool());
        assertEquals(ProjectType.Framework.JAVAFX, projectType.getFramework());
    }
}