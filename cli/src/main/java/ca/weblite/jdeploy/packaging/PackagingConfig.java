package ca.weblite.jdeploy.packaging;

import ca.weblite.jdeploy.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PackagingConfig {
    private static String DEFAULT_JDEPLOY_REGISTRY = "https://www.jdeploy.com/";

    private final Config config;
    @Inject
    public PackagingConfig(Config config) {
        this.config = config;
    }

    public String getJdeployRegistry() {
        return config.getProperties().getProperty("registry.url", DEFAULT_JDEPLOY_REGISTRY);
    }
}
