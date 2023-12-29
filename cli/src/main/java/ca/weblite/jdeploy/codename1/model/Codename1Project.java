package ca.weblite.jdeploy.codename1.model;

import ca.weblite.jdeploy.project.model.ProjectDescriptor;

import java.io.File;
import java.nio.file.Path;
import java.util.Properties;

public class Codename1Project implements ProjectDescriptor {
    private final Path projectPath;

    private final Properties codenameoneProperties;
    public Codename1Project(Path projectPath, Properties codenameoneProperties) {
        this.projectPath = projectPath;
        this.codenameoneProperties = codenameoneProperties;
    }


    public Path getProjectPath() {
        return projectPath;
    }

    @Override
    public String getMainPackage() {
        return codenameoneProperties.getProperty("codename1.packageName");
    }

    @Override
    public String getMainClassName() {
        return codenameoneProperties.getProperty("codename1.mainName");
    }

    @Override
    public Path getSourceRootPath() {
        return projectPath.resolve("common" + File.separator + "src" + File.separator + "main" + File.separator + "java");
    }

    @Override
    public Path getResourcesRootPath() {
        return projectPath.resolve("common" + File.separator + "src" + File.separator + "main" + File.separator + "resources");
    }

    public Properties getCodenameoneProperties() {
        return codenameoneProperties;
    }
}

