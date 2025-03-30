package ca.weblite.jdeploy.ideInterop;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class IdeInteropService {

    private final IdeInteropFactory ideInteropFactory;

    @Inject
    public IdeInteropService(IdeInteropFactory ideInteropFactory) {
        this.ideInteropFactory = ideInteropFactory;
    }

    /**
     * Finds all IDE installations
     */
    public List<IdeInteropInterface> findAll() {
        List<IdeInteropInterface> ideInterops = new ArrayList<>();
        List<File> ideFiles = findAllIdeApplicationFiles();
        for (File ideFile : ideFiles) {
            IdeInteropInterface ideInterop = ideInteropFactory.createIdeInterop(ideFile);
            if (ideInterop != null) {
                ideInterops.add(ideInterop);
            }
        }
        return ideInterops;
    }

    private List<File> findAllIdeApplicationFiles() {
        List<File> ideFiles = new ArrayList<>();

        // Define OS-specific base directories
        String os = System.getProperty("os.name").toLowerCase();
        File[] searchRoots;
        if (os.contains("win")) {
            searchRoots = new File[] {
                    new File("C:\\Program Files"),
                    new File("C:\\Program Files (x86)"),
                    new File(System.getProperty("user.home") + "\\AppData\\Local")
            };
        } else if (os.contains("mac")) {
            searchRoots = new File[] {
                    new File("/Applications"),
                    new File(System.getProperty("user.home") + "/Applications")
            };
        } else { // Linux/Unix
            searchRoots = new File[] {
                    new File("/usr/bin"),
                    new File("/usr/local/bin"),
                    new File("/opt"),
                    new File(System.getProperty("user.home"))
            };
        }

        // Search for each IDE using heuristics
        for (File root : searchRoots) {
            if (!root.exists() || !root.isDirectory()) continue;

            // NetBeans
            findIde(root, ideFiles, "netbeans", "bin/netbeans.exe", "bin/netbeans", "NetBeans.app");
            // Eclipse
            findIde(root, ideFiles, "eclipse", "eclipse.exe", "eclipse", "Eclipse.app");
            // IntelliJ IDEA
            findIde(root, ideFiles, "idea", "bin/idea.exe", "bin/idea.sh", "IntelliJ IDEA.app");
            // VSCode
            findIde(root, ideFiles, "code", "Code.exe", "code", "Visual Studio Code.app");
        }

        return ideFiles;
    }

    private void findIde(File root, List<File> ideFiles, String keyword, String winPath, String unixPath, String macPath) {
        File[] files = root.listFiles();
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (name.contains(keyword)) {
                // Check for executable or app bundle
                File candidate;
                if (System.getProperty("os.name").toLowerCase().contains("win")) {
                    candidate = new File(file, winPath);
                } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                    if (file.getName().endsWith(".app")) {
                        candidate = file; // macOS .app bundle
                    } else {
                        candidate = new File(file, unixPath);
                    }
                } else { // Linux/Unix
                    candidate = new File(file, unixPath);
                }

                if (candidate.exists() && (candidate.isFile() || candidate.getName().endsWith(".app"))) {
                    ideFiles.add(candidate);
                }
            }
        }
    }

}
