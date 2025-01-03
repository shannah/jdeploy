package ca.weblite.jdeploy.packaging;

import java.io.File;
import java.io.IOException;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

import static ca.weblite.jdeploy.PathUtil.toNativePath;

public class JarFinder {

    public File findBestCandidate(PackagingContext context) throws IOException{
        File[] jars = findJarCandidates(context);
        File[] wars = findWarCandidates(context);
        File[] webApps = findWebAppCandidates(context);
        List<File> combined = new ArrayList<File>();
        combined.addAll(Arrays.asList(jars));
        combined.addAll(Arrays.asList(wars));
        combined.addAll(Arrays.asList(webApps));
        return shallowest(combined.toArray(new File[combined.size()]));
    }

    public File findJarFile(PackagingContext context) {
        if (context.getJar(null) != null) {
            File jarFile = new File(context.directory, toNativePath(context.getJar(null)));

            if (!jarFile.exists() && jarFile.getParentFile() == null) {
                return null;
            }
            if (!jarFile.exists() && jarFile.getParentFile().exists()) {
                // Jar file might be a glob
                try {
                    PathMatcher matcher = jarFile.getParentFile().toPath().getFileSystem().getPathMatcher(jarFile.getName());
                    for (File f : jarFile.getParentFile().listFiles()) {
                        if (matcher.matches(f.toPath())) {
                            jarFile = f;
                            break;
                        }
                    }
                } catch (IllegalArgumentException ex) {
                    // just eat this
                }

                if (!jarFile.exists()) {
                    return null;
                }
            }
            return jarFile;
        }
        return null;
    }

    public File findWarFile(PackagingContext context) {
        if (context.getWar(null) != null) {
            File warFile = new File(context.getWar(null));
            if (!warFile.exists() || warFile.getParentFile() == null) {
                return null;
            }
            if (!warFile.exists() && warFile.getParentFile().exists()) {
                // Jar file might be a glob
                PathMatcher matcher = warFile.getParentFile().toPath().getFileSystem().getPathMatcher(warFile.getName());
                for (File f : warFile.getParentFile().listFiles()) {
                    if (matcher.matches(f.toPath())) {
                        warFile = f;
                        break;
                    }
                }

                if (!warFile.exists()) {
                    return null;
                }


            }
            return warFile;
        }
        return null;
    }

    public File[] findJarCandidates(PackagingContext context) throws IOException {
        File[] jars = findCandidates(context, context.directory.toPath().getFileSystem().getPathMatcher("glob:**/*.jar"));
        List<File> out = new ArrayList<File>();
        // We only want executable jars
        for (File f : jars) {
            Manifest m = new JarFile(f).getManifest();
            //System.out.println(m.getEntries());
            if (m != null) {
                Attributes atts = m.getMainAttributes();
                if (atts.containsKey(Attributes.Name.MAIN_CLASS)) {
                    //executable jar
                    out.add(f);
                }
            }
        }
        return out.toArray(new File[out.size()]);
    }



    public File[] findWarCandidates(PackagingContext context) {
        return findCandidates(context, context.directory.toPath().getFileSystem().getPathMatcher("glob:**.war"));
    }

    public File[] findWebAppCandidates(PackagingContext context) {
        List<File> out = new ArrayList<File>();
        findWebAppCandidates(context.directory, out);
        return out.toArray(new File[out.size()]);
    }

    private void findWebAppCandidates(File root, List<File> matches) {
        if (".".equals(root.getName()) && root.getParentFile() != null) {
            root = root.getParentFile();
        }
        if ("WEB-INF".equals(root.getName()) && root.isDirectory() && root.getParentFile() != null) {
            matches.add(root.getParentFile());
        } else if (root.isDirectory()) {
            if (root.getName().startsWith(".") || excludedDirectoriesForJarAndWarSearches.contains(root.getName())) {
                return;
            }
            for (File f : root.listFiles()) {
                findWebAppCandidates(f, matches);
            }
        }
    }

    private File[] findCandidates(PackagingContext context, PathMatcher matcher) {
        List<File> out = new ArrayList<File>();
        //PathMatcher matcher = directory.toPath().getFileSystem().getPathMatcher("glob:**.jar");
        findCandidates(context, context.directory, matcher, out);
        return out.toArray(new File[out.size()]);
    }

    private static final List<String> excludedDirectoriesForJarAndWarSearches = new ArrayList<String>();
    static {
        final String[] l = new String[]{
                "src",
                "jdeploy-bundle",
                "node_modules"
        };
        excludedDirectoriesForJarAndWarSearches.addAll(Arrays.asList(l));
    }

    private File shallowest(File[] files) {
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

    private void findCandidates(PackagingContext context, File root, PathMatcher matcher, List<File> matches) {
        if (".".equals(root.getName()) && root.getParentFile() != null) {
            root = root.getParentFile();
        }
        //System.out.println("Checking "+root+" for "+matcher);
        if (matcher.matches(root.toPath())) {
            matches.add(root);
        }
        if (root.isDirectory()) {
            if (root.getName().startsWith(".") || excludedDirectoriesForJarAndWarSearches.contains(root.getName())) {
                return;
            }
            for (File f : root.listFiles()) {
                findCandidates(context, f, matcher, matches);
            }
        }
    }
}
