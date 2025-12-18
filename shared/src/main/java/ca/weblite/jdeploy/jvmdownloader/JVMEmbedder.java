package ca.weblite.jdeploy.jvmdownloader;

import java.io.IOException;

public class JVMEmbedder {

    private JVMFinder finder;

    /**
     * Embed a JVM into a directory
     * @param sourcePath The source path of the JVM to install.  May be either a .zip or a .tar.gz file.
     * @param targetPath The target path where the JVM will be installed.  E.g. path/to/jre.  The JVM should be extracted to this directory.
     *                   Not as a subdirectory, but as the targetPath itself.
     * @throws IOException
     */
    public void embedJVM(
            String sourcePath,
            String targetPath
    ) throws IOException {

    }
}
