package ca.weblite.jdeploy.config;

import javax.inject.Singleton;
import java.util.Properties;

@Singleton
public class Config {
    private final Properties properties;

    public Config() {
        this.properties = new Properties();
    }

    public Properties getProperties() {
        return properties;
    }
}
