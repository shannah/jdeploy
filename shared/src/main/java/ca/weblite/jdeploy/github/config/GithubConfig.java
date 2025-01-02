package ca.weblite.jdeploy.github.config;

import ca.weblite.jdeploy.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GithubConfig {
    private final Config config;

    @Inject
    public GithubConfig(Config config) {
        this.config = config;
    }

    public String getToken() {
        return config.getProperties().getProperty("github.token");
    }

    public String setToken(String token) {
        return (String) config.getProperties().setProperty("github.token", token);
    }
}
