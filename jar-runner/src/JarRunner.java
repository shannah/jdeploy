import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.Arrays;

/**
 * This class is compiled and bundled with JDeploy and is used by the go launcher
 * to be able to run jar files.
 */
public class JarRunner {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("JarRunner expects first argument to be path to jar file to run.  Received no args");
            System.exit(1);
        }
        try {
            String[] newArgs = new String[args.length-1];
            if (newArgs.length > 0) {
                System.arraycopy(args, 1, newArgs, 0, newArgs.length);
            }
            run(new File(args[0]), newArgs);
        } catch (Exception ex) {
            System.err.println("Exception thrown while running jar "+args[0]);
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }

    public static void run(File jarFile, String[] args) throws Exception {
        System.out.println("Running "+jarFile+" with args "+Arrays.toString(args));
        JarFile jarArchive = new JarFile(jarFile);
        String mainClass = jarArchive.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        String classPath = jarArchive.getManifest().getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
        List<URL> classURLs = new ArrayList<URL>();
        classURLs.add(jarFile.toURL());
        for (String url : classPath.split(" ")) {
            if (url.startsWith("http:") || url.startsWith("file:") || url.startsWith("https:")) {
                classURLs.add(new URL(url));
            } else {
                File f = new File(jarFile.getParentFile(), url);
                if (f.exists()) {
                    classURLs.add(f.toURL());
                }
            }
        }
        System.out.println("ClassPath="+classURLs);
        //URLClassLoader classPathLoader = new URLClassLoader(classURLs.toArray(new URL[classURLs.size()]));
        //Thread.currentThread().setContextClassLoader(classPathLoader);

        Class clsMain = JarRunner.class.getClassLoader().loadClass(mainClass);
        Method mainMethod = clsMain.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object)args);
    }
}
