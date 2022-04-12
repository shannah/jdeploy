package ca.weblite.jdeploy.installer.util;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

public class JarClassLoader extends URLClassLoader {

    private static URL[] extractURLs(File jarFile) throws IOException {
        JarFile jar = new JarFile(jarFile);
        ArrayList<URL> urls = new ArrayList<URL>();


        urls.add(jarFile.toURL());
        String classPath = jar.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        if (classPath != null) {
            for (String url : classPath.split(" ")) {
                if (url.startsWith("file:")) {
                    urls.add(new URL(url));
                } else {
                    File f = new File(jarFile.getParentFile(), url);
                    if (f.exists()) {
                        urls.add(f.toURL());
                    }
                }
            }
        }

        return urls.toArray(new URL[urls.size()]);
    }

    public JarClassLoader(File jarFile) throws IOException {
        super(extractURLs(jarFile));

    }
}
