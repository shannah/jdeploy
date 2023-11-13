package ca.weblite.jdeploy.factories;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.models.JDeployProject;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

@Singleton
public class JDeployProjectFactory {

    private FileSystemInterface fileSystemInterface;

    @Inject
    public JDeployProjectFactory(FileSystemInterface fileSystemInterface) {
        this.fileSystemInterface = fileSystemInterface;
    }

    public JDeployProject createProject(Path packageJSONFile) throws IOException {
        return new JDeployProject(
                packageJSONFile,
                new JSONObject(fileSystemInterface.readToString(packageJSONFile, StandardCharsets.UTF_8))
        );
    }
}
