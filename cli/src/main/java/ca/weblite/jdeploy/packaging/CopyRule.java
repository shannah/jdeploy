package ca.weblite.jdeploy.packaging;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CopyRule {

    private final PackagingContext context;
    String dir;
    List<String> includes;
    List<String> excludes;

    private final String sanitizeDirPath(String dir) {
        if (new File(dir).isAbsolute()) {
            try {
                //System.out.println("dir="+new File(dir).getCanonicalPath()+"; directory="+directory.getCanonicalPath());
                dir = new File(dir).getCanonicalPath().substring(context.directory.getCanonicalPath().length());
            } catch (Exception ex) {
                dir = dir.substring(context.directory.getAbsolutePath().length());
            }
        }
        if (dir.startsWith("/") || dir.startsWith("\\")) {
            dir = dir.substring(1);
        }
        if (dir.isEmpty()) dir = ".";

        return dir.replace("\\", "/");
    }

    public CopyRule(PackagingContext context, String dir, List<String> includes, List<String> excludes) {
        this.context = context;
        this.dir = sanitizeDirPath(dir);

        this.includes = includes;
        this.excludes = excludes;
    }

    public CopyRule(PackagingContext context, String dir, String includes, String excludes) {
        this.context = context;
        this.dir = sanitizeDirPath(dir);

        this.includes = includes == null ? null : Arrays.asList(includes.split(","));
        this.excludes = excludes == null ? null : Arrays.asList(excludes.split(","));
    }

    public String toString() {
        return "CopyRule{dir="+dir+", includes="+includes+", excludes="+excludes+"}";
    }

    public void copyTo(File destDirectory) throws IOException {
        final File srcDir = new File(dir);
        if (!srcDir.exists()) {
            throw new IOException("Source directory of copy rule does not exist: "+srcDir);
        }

        if (!destDirectory.exists()) {
            throw new IOException("Destination directory of copy rule does not exist: "+destDirectory);
        }

        if (srcDir.equals(destDirectory)) {
            return;
        }
        final Set<String> includedDirectories = new HashSet<String>();

        FileUtils.copyDirectory(srcDir, destDirectory, new FileFilter() {

            @Override
            public boolean accept(File pathname) {
                if (pathname.isDirectory()) {
                    for (File child : pathname.listFiles()) {
                        if (this.accept(child)) {
                            return true;
                        }
                    }
                }
                File parent = pathname.getParentFile();
                if (parent != null && includedDirectories.contains(parent.getPath())) {
                    if (pathname.isDirectory()) {
                        includedDirectories.add(pathname.getPath());
                    }
                    return true;

                }
                if (excludes != null) {
                    for (String pattern : excludes) {
                        PathMatcher matcher = srcDir.toPath().getFileSystem().getPathMatcher("glob:"+dir+"/"+pattern.replace("\\", "/"));
                        if (matcher.matches(pathname.toPath())) {
                            return false;
                        }
                    }
                }

                if (includes != null) {
                    for (String pattern : includes) {

                        PathMatcher matcher = srcDir.toPath().getFileSystem().getPathMatcher("glob:"+dir+"/"+pattern.replace("\\", "/"));
                        if (matcher.matches(pathname.toPath())) {
                            if (pathname.isDirectory()) {
                                includedDirectories.add(pathname.getPath());
                            }
                            return true;
                        }
                    }
                    //System.out.println(pathname+" does not match any patterns.");
                    return false;
                } else {
                    if (pathname.isDirectory()) {
                        includedDirectories.add(pathname.getPath());
                    }
                    return true;
                }
            }

        });
    }
}
