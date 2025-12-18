package ca.weblite.jdeploy.services;

import javax.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class SystemJdkFinder {
    public File findJavaHome(String version) {
        List<String> paths = new ArrayList<>();

        String osName = System.getProperty("os.name").toLowerCase();
        String userHome = System.getProperty("user.home");

        if (osName.contains("win")) {
            paths.add("C:\\Program Files\\Java");
            paths.add("C:\\Program Files (x86)\\Java");
            paths.add(userHome + "\\.jdks");
            paths.add(userHome + "\\.jbang\\cache\\jdks");
            paths.add("C:\\Program Files\\Zulu");
            paths.add("C:\\Program Files\\Azul");
            paths.add("C:\\Program Files\\Eclipse Adoptium");
            paths.add("C:\\Program Files\\Adoptium");
        } else if (osName.contains("mac")) {
            paths.add(userHome + "/Library/Java/JavaVirtualMachines");
            paths.add("/Library/Java/JavaVirtualMachines");
            paths.add(userHome + "/.jdks");
            paths.add(userHome + "/.jbang/cache/jdks");
            paths.add("/Applications/NetBeans");
        } else { // Assume Linux or Unix
            paths.add("/usr/lib/jvm");
            paths.add("/usr/java");
            paths.add("/opt/java");
            paths.add(userHome + "/.jdks");
            paths.add(userHome + "/.jbang/cache/jdks");
            paths.add("/opt/zulu");
            paths.add("/usr/lib/jvm/zulu");
            paths.add("/usr/lib/jvm/temurin");
            paths.add("/usr/lib/jvm/adoptium");
        }

        for (String basePath : paths) {
            File baseDir = new File(basePath);
            if (!baseDir.exists() || !baseDir.isDirectory()) continue;

            File[] candidates = baseDir.listFiles();
            if (candidates == null) continue;

            for (File candidate : candidates) {
                String name = candidate.getName().toLowerCase();

                // Check that it's explicitly a JDK, not a JRE
                if (!name.contains("jre") && name.matches(".*-" + version + "(?:[._].*|$)")) {
                    File home = candidate;
                    // Special handling for macOS bundles
                    if (osName.contains("mac")) {
                        home = new File(candidate, "Contents/Home");
                    }

                    File javacExecutable = osName.contains("win") ?
                            new File(home, "bin\\javac.exe") : new File(home, "bin/javac");

                    if (javacExecutable.exists() && javacExecutable.canExecute()) {
                        return home;
                    }
                }
            }
        }

        return null; // Not found
    }
}
