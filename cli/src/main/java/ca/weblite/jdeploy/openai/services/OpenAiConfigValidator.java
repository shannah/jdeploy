package ca.weblite.jdeploy.openai.services;

import ca.weblite.jdeploy.openai.config.OpenAiChatConfig;
import ca.weblite.jdeploy.openai.exception.OpenAiConfigException;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class OpenAiConfigValidator {
    private final OpenAiChatConfig config;

    @Inject
    public OpenAiConfigValidator(OpenAiChatConfig config) {
        this.config = config;
    }

    public void validate() throws OpenAiConfigException {
        if (config.getOpenAiApiKey() == null || config.getOpenAiApiKey().isEmpty()) {
            throw new OpenAiConfigException("OpenAI API key is not set. Please please define the openai.token property in your config file.");
        }

        if (config.getDefaultGithubUser() == null || config.getDefaultGithubUser().isEmpty()) {
            throw new OpenAiConfigException("Github user is not set. Please please define the github.user property in your config file.");
        }
    }
}
