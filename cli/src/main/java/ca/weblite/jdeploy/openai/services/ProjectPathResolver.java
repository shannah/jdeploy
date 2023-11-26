package ca.weblite.jdeploy.openai.services;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.openai.config.OpenAiChatConfig;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;

@Singleton
public class ProjectPathResolver {
    private final OpenAiChatConfig config;

    private final FileSystemInterface fileSystemInterface;

    @Inject
    public ProjectPathResolver(OpenAiChatConfig config, FileSystemInterface fileSystemInterface) {
        this.config = config;
        this.fileSystemInterface = fileSystemInterface;
    }

    public Path getProjectPathByName(String projectName) {
        return fileSystemInterface.getPath(config.getBaseProjectsPath(), projectName);
    }
}
