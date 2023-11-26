package ca.weblite.jdeploy.config;

import java.io.IOException;

public interface ConfigLoader {
    void loadConfig(Class source) throws IOException;
}
