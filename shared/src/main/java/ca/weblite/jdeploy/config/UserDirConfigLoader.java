package ca.weblite.jdeploy.config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Singleton
public class UserDirConfigLoader implements ConfigLoader {

    private static final String DEFAULT_CONFIG_FILE = "jdeploy.properties";

    private final PropertiesFileConfigLoader propertiesFileConfigLoader;

    @Inject
    public UserDirConfigLoader(PropertiesFileConfigLoader propertiesFileConfigLoader) {
        this.propertiesFileConfigLoader = propertiesFileConfigLoader;
    }

    @Override
    public void loadConfig(Class source) throws IOException {
        File configFile = new File(System.getProperty("user.dir"), DEFAULT_CONFIG_FILE);
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                propertiesFileConfigLoader.loadConfig(fis);
            }
        }
    }
}
