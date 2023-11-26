package ca.weblite.jdeploy.config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class EnvironmentConfigLoader implements ConfigLoader {

    private final Config config;

    private static final String CONFIG_PREFIX = "JDEPLOY_";

    @Inject
    public EnvironmentConfigLoader(Config config) {
        this.config = config;
    }

    @Override
    public void loadConfig(Class source) throws IOException {
        for (String key : System.getenv().keySet()) {
            if (key.startsWith(CONFIG_PREFIX)) {
                config.getProperties().setProperty(convertEnvironmentVariableToPropertyKey(key.substring(CONFIG_PREFIX.length())), System.getenv(key));
            }
        }
    }

    private String convertEnvironmentVariableToPropertyKey(String envVar) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<envVar.length(); i++) {
            char c = envVar.charAt(i);
            if (c == '_') {
                sb.append('.');
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
