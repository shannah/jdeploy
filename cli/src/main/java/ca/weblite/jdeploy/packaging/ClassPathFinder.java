package ca.weblite.jdeploy.packaging;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class ClassPathFinder {
    public String[] findClassPath(File jarFile) throws IOException {
        Manifest m = new JarFile(jarFile).getManifest();
        //System.out.println(m.getEntries());
        String cp = m.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        //System.out.println("Class path is "+cp);
        if (cp != null) {
            return cp.split(" ");
        } else {
            return new String[0];
        }
    }
}
