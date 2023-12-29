package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.github.config.GithubConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GithubTokenService {
    private GithubConfig config;

    @Inject
    public GithubTokenService(GithubConfig config) {
        this.config = config;
    }
    public String getToken() {
        return config.getToken();
    }
}
