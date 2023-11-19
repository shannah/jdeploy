package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.tests.mocks.MockFileSystem;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class MavenWrapperInjectorTest {

    @Test
    void installIntoProject() throws Exception {

        String projectDirectory = "/tmp/test/project";
        FileSystemInterface mockFileSystem = Mockito.spy(MockFileSystem.class);
        mockFileSystem.mkdirs(mockFileSystem.getPath(projectDirectory));
        mockFileSystem.writeStringToFile(
                mockFileSystem.getPath(projectDirectory).resolve("pom.xml"),
                "<project></project>", StandardCharsets.UTF_8
        );
        MavenWrapperInjector mavenWrapperInjector = new MavenWrapperInjector(mockFileSystem);

        try {
            mavenWrapperInjector.installIntoProject(projectDirectory);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertTrue(mockFileSystem.exists(mockFileSystem.getPath(projectDirectory).resolve("mvnw")));
        assertTrue(mockFileSystem.exists(mockFileSystem.getPath(projectDirectory).resolve("mvnw.cmd")));
        assertTrue(mockFileSystem.exists(
                mockFileSystem.getPath(projectDirectory)
                        .resolve(".mvn")
                        .resolve("wrapper")
                        .resolve("MavenWrapperDownloader.java")
        ));

    }

    @Test
    void installIntoProjectShouldThrowExceptionIfNotMavenProject() {
        String projectDirectory = "/tmp/test/project";
        FileSystemInterface mockFileSystem = Mockito.spy(MockFileSystem.class);
        mockFileSystem.mkdirs(mockFileSystem.getPath(projectDirectory));

        MavenWrapperInjector mavenWrapperInjector = new MavenWrapperInjector(mockFileSystem);
        assertThrows(IOException.class, () -> {
            mavenWrapperInjector.installIntoProject(projectDirectory);
        });
    }

    @Test
    void installIntoProjectShouldThrowExceptionIfProjectDirectoryDoesntExist() {
        String projectDirectory = "/tmp/test/project";
        FileSystemInterface mockFileSystem = Mockito.spy(MockFileSystem.class);
        MavenWrapperInjector mavenWrapperInjector = new MavenWrapperInjector(mockFileSystem);
        assertThrows(IOException.class, () -> {
            mavenWrapperInjector.installIntoProject(projectDirectory);
        });
    }

    @Test
    void isMavenProjectShouldReturnFalseIfProjectDoesntExist() {
        String projectDirectory = "/tmp/test/project";
        FileSystemInterface mockFileSystem = Mockito.spy(MockFileSystem.class);
        MavenWrapperInjector mavenWrapperInjector = new MavenWrapperInjector(mockFileSystem);
        assertFalse(mavenWrapperInjector.isMavenProject(projectDirectory));
    }

    @Test
    void isMavenProjectShouldReturnFalseIfProjectMissingPom() throws IOException {
        String projectDirectory = "/tmp/test/project";
        FileSystemInterface mockFileSystem = Mockito.spy(MockFileSystem.class);
        mockFileSystem.mkdirs(mockFileSystem.getPath(projectDirectory));
        MavenWrapperInjector mavenWrapperInjector = new MavenWrapperInjector(mockFileSystem);
        assertFalse(mavenWrapperInjector.isMavenProject(projectDirectory));
    }

    @Test
    void isMavenProject() throws IOException {
        String projectDirectory = "/tmp/test/project";
        FileSystemInterface mockFileSystem = Mockito.spy(MockFileSystem.class);
        mockFileSystem.mkdirs(mockFileSystem.getPath(projectDirectory));
        mockFileSystem.writeStringToFile(
                mockFileSystem.getPath(projectDirectory).resolve("pom.xml"),
                "<project></project>", StandardCharsets.UTF_8
        );
        MavenWrapperInjector mavenWrapperInjector = new MavenWrapperInjector(mockFileSystem);
        assertTrue(mavenWrapperInjector.isMavenProject(projectDirectory));
    }
}