package ca.weblite.jdeploy.config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

@Singleton
public class ClassPathConfigLoader implements ConfigLoader {

    private static final String DEFAULT_CONFIG_FILE = "/application.properties";

    private final PropertiesFileConfigLoader propertiesFileConfigLoader;

    @Inject
    public ClassPathConfigLoader(PropertiesFileConfigLoader propertiesFileConfigLoader) {
        this.propertiesFileConfigLoader = propertiesFileConfigLoader;
    }
    @Override
    public void loadConfig(Class source) throws IOException {
        for (String path: getPaths()) {
            loadConfig(path, source);
        }
    }

    private List<String> getPaths() {
        List<String> paths = new ArrayList<>();
        paths.add(DEFAULT_CONFIG_FILE);
        paths.addAll(getProfiles().stream().map(this::getProfilePath).collect(toList()));

        return paths;
    }

    private List<String> getProfiles() {
        List<String> profiles = new ArrayList<>();
        if (System.getProperty("jdeploy.profile") != null) {
            for (String profile : System.getProperty("jdeploy.profile").split(",")) {
                profiles.add(profile);
            }
        }
        return profiles;
    }

    private String getProfilePath(String profile) {
        return "/application-" + profile + ".properties";
    }

    private void loadConfig(String path, Class source) throws IOException {
        propertiesFileConfigLoader.loadConfig(source.getResourceAsStream(path));
    }
}
