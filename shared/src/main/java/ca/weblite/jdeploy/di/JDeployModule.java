package ca.weblite.jdeploy.di;

import ca.weblite.jdeploy.io.DefaultFileSystemInterface;
import ca.weblite.jdeploy.io.FileSystemInterface;
import org.codejargon.feather.Provides;

public class JDeployModule {
    private FileSystemInterface fileSystemInterface = new DefaultFileSystemInterface();

    @Provides
    public FileSystemInterface fileSystemInterface() {
        return fileSystemInterface;
    }
}
