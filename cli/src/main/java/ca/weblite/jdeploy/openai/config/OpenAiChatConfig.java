package ca.weblite.jdeploy.openai.config;

import ca.weblite.jdeploy.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

@Singleton
public class OpenAiChatConfig {

    private final Config config;

    @Inject
    public OpenAiChatConfig(Config config) {
        this.config = config;
    }
    public String getBaseProjectsPath() {
        return new File(System.getProperty("user.home"), "jdeploy-openai-projects").getAbsolutePath();
    }

    public String getDefaultGithubUser() {
        return config.getProperties().getProperty("github.user");
    }

    public String getOpenAiApiKey() {
        return config.getProperties().getProperty("openai.token");
    }
}
