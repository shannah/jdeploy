package ca.weblite.jdeploy.helpers;

import javax.inject.Singleton;
import java.io.File;
import java.util.regex.Pattern;

@Singleton
public class FileHelper {
    public File shallowest(File[] files) {
        File out = null;
        int depth = -1;
        for (File f : files) {
            int fDepth = f.getPath().split(Pattern.quote("\\") + "|" + Pattern.quote("/")).length;
            if (out == null || fDepth < depth) {
                depth = fDepth;
                out = f;
            }
        }
        return out;
    }

    public String getRelativePath(File currentDirectory, File f) {

        return currentDirectory.toURI().relativize(f.toURI()).getPath();
    }
}
