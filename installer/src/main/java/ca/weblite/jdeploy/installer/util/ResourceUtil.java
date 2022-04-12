package ca.weblite.jdeploy.installer.util;

import ca.weblite.tools.io.ArchiveUtil;
import ca.weblite.tools.io.FileUtil;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class ResourceUtil {
    public static File extractResourceToTempDirectory(Class cls, String resource) throws IOException {
        File tmp = File.createTempFile("tmp", "");
        FileUtils.copyToFile(cls.getResourceAsStream(resource), tmp);
        tmp.deleteOnExit();
        return tmp;
    }
    public static File extractZipResourceToTempDirectory(Class cls, String resource) throws IOException {
        File tmp = extractResourceToTempDirectory(cls, resource);
        File tmpDir = FileUtil.createTempDirectory("tmp", "", tmp.getParentFile());

        ArchiveUtil.unzip(tmp, tmpDir, null);
        tmp.delete();
        return tmpDir;
    }

    public static File extractZipResourceWithExecutableJarToTempDirectory(Class cls, String resource) throws IOException {
        File tmpDir = extractZipResourceToTempDirectory(cls, resource);
        return findExecutableJar(tmpDir);
    }

    private static File findExecutableJar(File root) throws IOException {
        if (root.isDirectory()) {
            for (File child : root.listFiles()) {
                String name = child.getName();
                if (name.endsWith(".jar")) {
                    JarFile jarFile = new JarFile(child);
                    Manifest manifest = jarFile.getManifest();
                    if (manifest == null) {
                        continue;
                    }
                    if (manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS) != null) {
                        // We have our jar
                        return child;
                    }
                }
            }

            for (File child : root.listFiles()) {
                if (child.isDirectory()) {
                    File found = findExecutableJar(child);
                    if (found != null) return found;
                }
            }
        }
        return null;
    }
}
