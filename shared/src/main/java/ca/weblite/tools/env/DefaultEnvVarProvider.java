package ca.weblite.tools.env;

public class DefaultEnvVarProvider implements EnvVarProvider {

    @Override
    public String getEnv(String key) {
        return System.getenv(key);
    }
}
