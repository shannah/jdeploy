package ca.weblite.jdeploy.tests.mocks;

import ca.weblite.jdeploy.io.DefaultFileSystemInterface;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

import java.nio.file.Path;


public class MockFileSystem extends DefaultFileSystemInterface {

    public MockFileSystem() {
        super(Jimfs.newFileSystem(Configuration.unix()));
    }

    @Override
    public boolean makeExecutable(Path path) {
        return true;
    }
}
