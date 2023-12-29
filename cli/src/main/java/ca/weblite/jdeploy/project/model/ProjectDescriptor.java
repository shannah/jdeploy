package ca.weblite.jdeploy.project.model;

import java.nio.file.Path;

public interface ProjectDescriptor {
    public Path getProjectPath();

    public String getMainPackage();

    public String getMainClassName();

    public Path getSourceRootPath();

    public Path getResourcesRootPath();
}
