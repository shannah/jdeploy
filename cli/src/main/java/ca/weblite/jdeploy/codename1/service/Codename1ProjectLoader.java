package ca.weblite.jdeploy.codename1.service;

import ca.weblite.jdeploy.codename1.factory.Codename1ProjectFactory;
import ca.weblite.jdeploy.codename1.model.Codename1Project;
import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.project.service.ProjectLoader;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;

@Singleton
public class Codename1ProjectLoader implements ProjectLoader {

    private final FileSystemInterface fileSystemInterface;

    private final Codename1ProjectFactory codename1ProjectFactory;

    @Inject
    public Codename1ProjectLoader(
            FileSystemInterface fileSystemInterface,
            Codename1ProjectFactory codename1ProjectFactory
    ) {
        this.fileSystemInterface = fileSystemInterface;
        this.codename1ProjectFactory = codename1ProjectFactory;
    }

    public Codename1Project loadProject(String projectPath) throws IOException {
        Path rootPath = fileSystemInterface.getPath(projectPath);
        if (fileSystemInterface.isDirectory(rootPath)) {
            Path codenameonePropertiesPath = getCodenameOneSettingsPath(rootPath);
            if (fileSystemInterface.exists(codenameonePropertiesPath)) {
                Properties codenameoneProperties = new Properties();
                try (InputStream inputStream = fileSystemInterface.getInputStream(codenameonePropertiesPath)) {
                    codenameoneProperties.load(inputStream);
                }
                return codename1ProjectFactory.createProject(rootPath, codenameoneProperties);
            }
        }

        throw new IOException("Could not find Codename One project at " + projectPath);
    }

    public boolean canLoadProject(String projectPath) {
        Path rootPath = fileSystemInterface.getPath(projectPath);
        return fileSystemInterface.exists(getCodenameOneSettingsPath(rootPath));
    }

    private Path getCodenameOneSettingsPath(Path projectPath) {
        return projectPath.resolve("common").resolve("codenameone_settings.properties");
    }
}
