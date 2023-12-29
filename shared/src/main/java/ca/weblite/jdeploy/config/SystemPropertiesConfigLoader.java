package ca.weblite.jdeploy.config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class SystemPropertiesConfigLoader implements ConfigLoader {
    private final Config config;

    private static final String CONFIG_PREFIX = "jdeploy.";

    @Inject
    public SystemPropertiesConfigLoader(Config config) {
        this.config = config;
    }

    public void loadConfig(Class source) throws IOException {
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(CONFIG_PREFIX)) {
                config.getProperties().setProperty(key.substring(CONFIG_PREFIX.length()), System.getProperty(key));
            }
        }
    }
}
