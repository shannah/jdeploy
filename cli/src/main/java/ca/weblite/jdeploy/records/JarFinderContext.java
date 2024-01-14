package ca.weblite.jdeploy.records;

import javax.inject.Singleton;
import java.io.File;

public class JarFinderContext {
    private File directory;

    public JarFinderContext(File directory) {
        this.directory = directory;
    }

    public File getDirectory() {
        return directory;
    }


    @Singleton
    public static class Factory {
        public JarFinderContext fromPackageJsonInitializerContext(PackageJsonInitializerContext packageJsonInitializerContext) {
            return new JarFinderContext(packageJsonInitializerContext.getDirectory());
        }
    }
}
