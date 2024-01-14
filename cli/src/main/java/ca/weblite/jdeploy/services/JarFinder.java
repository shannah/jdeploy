package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.helpers.FileHelper;
import ca.weblite.jdeploy.records.JarFinderContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

@Singleton
public class JarFinder {

    private final FileHelper fileHelper;

    @Inject
    public JarFinder(FileHelper fileHelper) {
        this.fileHelper = fileHelper;
    }

    private final List<String> excludedDirectoriesForJarAndWarSearches = new ArrayList<String>();
    {
        final String[] l = new String[]{
                "src",
                "jdeploy-bundle",
                "node_modules"
        };
        excludedDirectoriesForJarAndWarSearches.addAll(Arrays.asList(l));
    }
    public File findBestCandidate(JarFinderContext context) throws IOException {
        File[] jars = findJarCandidates(context);
        File[] wars = findWarCandidates(context);
        File[] webApps = findWebAppCandidates(context);
        List<File> combined = new ArrayList<File>();
        combined.addAll(Arrays.asList(jars));
        combined.addAll(Arrays.asList(wars));
        combined.addAll(Arrays.asList(webApps));
        return shallowest(combined.toArray(new File[combined.size()]));
    }

    public File[] findWarCandidates(JarFinderContext context) {
        return findCandidates(
                context,
                context.getDirectory().toPath().getFileSystem().getPathMatcher("glob:**.war")
        );
    }

    public File[] findWebAppCandidates(JarFinderContext context) {
        List<File> out = new ArrayList<File>();
        findWebAppCandidates(context.getDirectory(), out);
        return out.toArray(new File[out.size()]);
    }

    public File[] findJarCandidates(JarFinderContext context) throws IOException {
        File[] jars = findCandidates(
                context,
                context.getDirectory().toPath().getFileSystem().getPathMatcher("glob:**/*.jar")
        );
        List<File> out = new ArrayList<File>();
        // We only want executable jars
        for (File f : jars) {
            try (JarFile jarFile = new JarFile(f)) {
                Manifest m = jarFile.getManifest();
                //System.out.println(m.getEntries());
                if (m != null) {
                    Attributes atts = m.getMainAttributes();
                    if (atts.containsKey(Attributes.Name.MAIN_CLASS)) {
                        //executable jar
                        out.add(f);
                    }
                }
            }

        }
        return out.toArray(new File[out.size()]);
    }

    private File shallowest(File[] files) {
        return fileHelper.shallowest(files);
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

    private File[] findCandidates(JarFinderContext context, PathMatcher matcher) {
        List<File> out = new ArrayList<File>();
        findCandidates(context.getDirectory(), matcher, out);
        return out.toArray(new File[out.size()]);
    }

    private void findCandidates(File root, PathMatcher matcher, List<File> matches) {
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
                findCandidates(f, matcher, matches);
            }
        }
    }
}
