package ca.weblite.jdeploy.packaging;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Path;
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

    public CopyRule(PackagingContext context, String dir, List<String> includes, List<String> excludes, boolean escape) {
        this.context = context;
        this.dir = dir;

        this.includes = includes;
        this.excludes = excludes;

        if (escape) {
            if (this.includes != null) {
                for (int i=0; i<this.includes.size(); i++) {
                    this.includes.set(i, escapeGlob(this.includes.get(i)));
                }
            }
            if (this.excludes != null) {
                for (int i=0; i<this.excludes.size(); i++) {
                    this.excludes.set(i, escapeGlob(this.excludes.get(i)));
                }
            }
        }
    }

    public CopyRule(PackagingContext context, String dir, String includes, String excludes, boolean escape) {
        this.context = context;
        this.dir = dir;

        this.includes = includes == null
                ? null
                : Arrays.asList(includes.split(","));
        this.excludes = excludes == null
                ? null
                : Arrays.asList(excludes.split(","));

        if (escape) {
            if (this.includes != null) {
                for (int i=0; i<this.includes.size(); i++) {
                    this.includes.set(i, escapeGlob(this.includes.get(i)));
                }
            }
            if (this.excludes != null) {
                for (int i=0; i<this.excludes.size(); i++) {
                    this.excludes.set(i, escapeGlob(this.excludes.get(i)));
                }
            }
        }


    }

    public String toString() {
        return "CopyRule{dir="+dir+", includes="+includes+", excludes="+excludes+"}";
    }

    public void copyTo(File destDirectory) throws IOException {
        final File srcDir = new File(dir).isAbsolute()
                ? new File(dir)
                : new File(context.directory, dir);

        if (!srcDir.exists()) {
            throw new IOException("Source directory of copy rule does not exist: " + srcDir);
        }

        if (!destDirectory.exists()) {
            throw new IOException("Destination directory of copy rule does not exist: " + destDirectory);
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
                    return false;
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
                        PathMatcher matcher = srcDir
                                .toPath()
                                .getFileSystem()
                                .getPathMatcher(
                                        "glob:"
                                                + escapeGlob(srcDir.getPath())
                                                + escapeGlob("/")
                                                + pattern
                                );
                        if (matcher.matches(pathname.toPath())) {
                            return false;
                        }
                    }
                }

                if (includes != null) {
                    for (String pattern : includes) {
                        PathMatcher matcher = srcDir
                                .toPath()
                                .getFileSystem()
                                .getPathMatcher(
                                        "glob:"
                                                + escapeGlob(srcDir.getPath())
                                                + "/"
                                                + pattern
                                );
                        if (matcher.matches(pathname.toPath())) {
                            if (pathname.isDirectory()) {
                                includedDirectories.add(pathname.getPath());
                            }
                            return true;
                        }
                    }
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

    public static String escapeGlob(String path) {
        StringBuilder escaped = new StringBuilder();
        for (char c : path.toCharArray()) {
            switch (c) {
                case '*': case '?': case '[': case ']': case '{': case '}':
                case '(': case ')': case ',': case '\\':
                    escaped.append('\\'); // Add the escape character
                    // Fallthrough to append the actual character
                default:
                    escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
