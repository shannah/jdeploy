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
            return path.replace("/", "\\");
        } else {
            return path.replace("\\", "/");
        }
    }
}
