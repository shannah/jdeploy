package ca.weblite.jdeploy;

import ca.weblite.tools.platform.Platform;

public class PathUtil {
    public static String fromNativePath(String path) {
        if (path == null) return null;
        return path.replace("\\", "/");
    }

    public static String toNativePath(String path) {
        if (path == null) return null;
        if (Platform.getSystemPlatform().isWindows()) {
            path = path.replace("/", "\\");
        } else {
            path = path.replace("\\", "/");
        }
        //path = stripLeading(path);
        return path;
    }

    /*
    private static String stripLeading(String path) {
        if (path == null) return null;
        while (path.startsWith("./")) {
            path =  path.substring(2);
        }
        while (path.startsWith(".\\")) {
            path = path.substring(2);
        }
        return path;
    }

     */
}
