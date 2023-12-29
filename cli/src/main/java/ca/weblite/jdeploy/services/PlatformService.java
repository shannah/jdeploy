package ca.weblite.jdeploy.services;

import javax.inject.Singleton;

@Singleton
public class PlatformService {
    public boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
