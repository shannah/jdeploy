package ca.weblite.jdeploy.environment;

public class Environment {
    public String get(String key) {
        return System.getenv(key);
    }
}
