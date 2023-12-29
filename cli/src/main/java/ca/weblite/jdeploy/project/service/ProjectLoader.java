package ca.weblite.jdeploy.project.service;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.project.model.ProjectDescriptor;

import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public interface ProjectLoader {

    ProjectDescriptor loadProject(String projectPath) throws IOException;
    boolean canLoadProject(String projectPath);
}
