package ca.weblite.jdeploy.github.config;

import ca.weblite.jdeploy.config.ConfigValidator;
import ca.weblite.jdeploy.exception.ConfigValidationException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GithubConfigValidator implements ConfigValidator {
    private final GithubConfig config;

    @Inject
    public GithubConfigValidator(GithubConfig config) {
        this.config = config;
    }

    public void validate() throws ConfigValidationException {
        if (config.getToken() == null || config.getToken().isEmpty()) {
            throw new ConfigValidationException("Github token is not set. Please set the jdeploy.github.token property in your config file.");
        }
    }
}
