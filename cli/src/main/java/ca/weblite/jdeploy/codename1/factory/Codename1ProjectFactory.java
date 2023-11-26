package ca.weblite.jdeploy.codename1.factory;

import ca.weblite.jdeploy.codename1.model.Codename1Project;

import java.nio.file.Path;
import java.util.Properties;

public class Codename1ProjectFactory {
    public Codename1Project createProject(Path projectPath, Properties properties) {
        return new Codename1Project(projectPath, properties);
    }
}
