package ca.weblite.jdeploy.config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;

@Singleton
class PropertiesFileConfigLoader {
    private final Config config;

    @Inject
    public PropertiesFileConfigLoader(Config config) {
        this.config = config;
    }

    public void loadConfig(InputStream inputStream) throws IOException {
        if (inputStream != null) {
            try {
                config.getProperties().load(inputStream);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
