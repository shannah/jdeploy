package ca.weblite.jdeploy.cli.config;

import ca.weblite.jdeploy.config.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;

@Singleton
public class CliConfigLoader implements ConfigLoader {
    private final ClassPathConfigLoader classPathConfigLoader;

    private final UserDirConfigLoader userDirConfigLoader;

    private final UserHomeConfigLoader userHomeConfigLoader;

    private final SystemPropertiesConfigLoader systemPropertiesConfigLoader;

    private final EnvironmentConfigLoader environmentConfigLoader;

    @Inject
    public CliConfigLoader(
            ClassPathConfigLoader classPathConfigLoader,
            UserDirConfigLoader userDirConfigLoader,
            UserHomeConfigLoader userHomeConfigLoader,
            SystemPropertiesConfigLoader systemPropertiesConfigLoader,
            EnvironmentConfigLoader environmentConfigLoader
    ) {
        this.classPathConfigLoader = classPathConfigLoader;
        this.userDirConfigLoader = userDirConfigLoader;
        this.userHomeConfigLoader = userHomeConfigLoader;
        this.systemPropertiesConfigLoader = systemPropertiesConfigLoader;
        this.environmentConfigLoader = environmentConfigLoader;
    }

    @Override
    public void loadConfig(Class source) throws IOException {
        classPathConfigLoader.loadConfig(source);
        userHomeConfigLoader.loadConfig(source);
        userDirConfigLoader.loadConfig(source);
        environmentConfigLoader.loadConfig(source);
        systemPropertiesConfigLoader.loadConfig(source);
    }
}
