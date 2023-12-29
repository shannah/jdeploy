package ca.weblite.jdeploy.repositories;

import ca.weblite.jdeploy.factories.JDeployProjectFactory;
import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.models.JDeployProject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;


@Singleton
public class JDeployProjectRepository {

    private JDeployProjectFactory factory;

    private FileSystemInterface fileSystemInterface;

    @Inject
    public JDeployProjectRepository(
            JDeployProjectFactory factory,
            FileSystemInterface fileSystemInterface
    ) {
        this.factory = factory;
        this.fileSystemInterface = fileSystemInterface;
    }

    public JDeployProject findByPath(String path) throws IOException {
        if (!fileSystemInterface.isDirectory(Paths.get(path))) {
            throw new FileNotFoundException(path);
        }

        Path packageJSON = Paths.get(path, "package.json");
        if (!fileSystemInterface.exists(packageJSON)) {
            throw new FileNotFoundException(path);
        }

        JDeployProject project = factory.createProject(packageJSON);

        return project;
    }
}
