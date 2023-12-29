package ca.weblite.jdeploy.project.service;

import ca.weblite.jdeploy.codename1.service.Codename1ProjectLoader;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class DefaultProjectLoader implements ca.weblite.jdeploy.project.service.ProjectLoader {

    private final ProjectLoader[] projectLoaders;

    @Inject
    public DefaultProjectLoader(Codename1ProjectLoader codename1ProjectLoader) {
        this.projectLoaders = new ProjectLoader[]{codename1ProjectLoader};
    }

    public ca.weblite.jdeploy.project.model.ProjectDescriptor loadProject(String projectPath) throws IOException {
        for (ProjectLoader projectLoader : projectLoaders) {
            if (projectLoader.canLoadProject(projectPath)) {
                return projectLoader.loadProject(projectPath);
            }
        }

        throw new IOException("No project loader found for project path: " + projectPath);
    }

    public boolean canLoadProject(String projectPath) {
        for (ProjectLoader projectLoader : projectLoaders) {
            if (projectLoader.canLoadProject(projectPath)) {
                return true;
            }
        }

        return false;
    }
}

