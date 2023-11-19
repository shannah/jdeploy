package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.tools.io.IOUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

@Singleton
public class MavenWrapperInjector {
    private FileSystemInterface fileSystem;

    private static final String RESOURCES_BASE = "/ca/weblite/jdeploy/mavenWrapper/";

    @Inject
    public MavenWrapperInjector(FileSystemInterface fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void installIntoProject(String projectDirectoryPath) throws IOException {
        Path projectDirectory = fileSystem.getPath(projectDirectoryPath);
        if (!isMavenProject(projectDirectoryPath)) {
            throw new IOException("Not a maven project: " + projectDirectory);
        }

        Path mavenWrapperDirectory = projectDirectory.resolve("mvnw");
        if (fileSystem.exists(mavenWrapperDirectory)) {
            return;
        }
        copyResourceTo(RESOURCES_BASE + "mvnw", projectDirectory.resolve("mvnw"));
        fileSystem.makeExecutable(projectDirectory.resolve("mvnw"));
        copyResourceTo(RESOURCES_BASE + "mvnw.cmd", projectDirectory.resolve("mvnw.cmd"));
        fileSystem.makeExecutable(projectDirectory.resolve("mvnw.cmd"));

        Path mvnWrapperDirectory = projectDirectory.resolve(".mvn").resolve("wrapper");
        fileSystem.mkdirs(mvnWrapperDirectory);

        if (!fileSystem.isDirectory(mvnWrapperDirectory)) {
            throw new IOException("Could not create directory: " + mvnWrapperDirectory);
        }

        copyResourceTo(
                RESOURCES_BASE + ".mvn/wrapper/MavenWrapperDownloader.java",
                mvnWrapperDirectory.resolve("MavenWrapperDownloader.java")
        );
        copyResourceTo(
                RESOURCES_BASE + ".mvn/wrapper/maven-wrapper.properties",
                mvnWrapperDirectory.resolve("maven-wrapper.properties")
        );
    }

    public boolean isMavenProject(String projectDirectoryPath) {
        Path projectDirectory = fileSystem.getPath(projectDirectoryPath);
        if (!fileSystem.exists(projectDirectory)) {
            return false;
        }

        if (!fileSystem.isDirectory(projectDirectory)) {
            return false;
        }

        Path pomFile = projectDirectory.resolve("pom.xml");
        if (!fileSystem.exists(pomFile)) {
            return false;
        }

        return true;
    }

    private void copyResourceTo(String resource, Path target) throws IOException {
        fileSystem.copyResourceTo(getClass(), resource, target);
    }
}
