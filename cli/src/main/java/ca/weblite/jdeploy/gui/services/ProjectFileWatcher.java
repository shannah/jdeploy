package ca.weblite.jdeploy.gui.services;

import ca.weblite.tools.io.MD5;
import java.io.File;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.sun.nio.file.SensitivityWatchEventModifier;

/**
 * Monitors a project directory for changes to package.json and .jdpignore files.
 * Tracks MD5 checksums and emits FileChangeEvent records via a Consumer callback.
 * 
 * This class encapsulates file system watching logic that was previously scattered
 * in JDeployProjectEditor, making it reusable and independently testable.
 */
public class ProjectFileWatcher {
    private final File projectDirectory;
    private final File packageJsonFile;
    private final Consumer<FileChangeEvent> onChange;
    
    private WatchService watchService;
    private volatile boolean watching = false;
    private Thread watchThread;
    
    // MD5 checksum tracking
    private String packageJsonMD5;
    private Map<String, String> jdpignoreFileMD5s = new HashMap<>();
    
    /**
     * Creates a ProjectFileWatcher for the given project directory.
     * 
     * @param projectDirectory the root directory of the project
     * @param packageJsonFile the package.json file to watch
     * @param onChange callback invoked when a file changes
     * @throws java.io.IOException if unable to read initial checksums
     */
    public ProjectFileWatcher(
            File projectDirectory,
            File packageJsonFile,
            Consumer<FileChangeEvent> onChange
    ) throws java.io.IOException {
        this.projectDirectory = Objects.requireNonNull(projectDirectory, "projectDirectory");
        this.packageJsonFile = Objects.requireNonNull(packageJsonFile, "packageJsonFile");
        this.onChange = Objects.requireNonNull(onChange, "onChange callback");
        
        // Initialize MD5 checksums
        updatePackageJsonMD5();
        updateJdpignoreFileMD5s();
    }
    
    /**
     * Immutable event emitted when a watched file changes.
     * 
     * @param filename the name of the changed file (e.g., "package.json" or ".jdpignore.mac-x64")
     * @param oldHash the MD5 hash before the change (empty string if file didn't exist)
     * @param newHash the MD5 hash after the change (empty string if file was deleted)
     */
    public static final class FileChangeEvent {
        private final String filename;
        private final String oldHash;
        private final String newHash;
        
        public FileChangeEvent(String filename, String oldHash, String newHash) {
            this.filename = Objects.requireNonNull(filename, "filename");
            this.oldHash = Objects.requireNonNull(oldHash, "oldHash");
            this.newHash = Objects.requireNonNull(newHash, "newHash");
        }
        
        public String filename() {
            return filename;
        }
        
        public String oldHash() {
            return oldHash;
        }
        
        public String newHash() {
            return newHash;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof FileChangeEvent)) return false;
            FileChangeEvent other = (FileChangeEvent) obj;
            return filename.equals(other.filename) &&
                   oldHash.equals(other.oldHash) &&
                   newHash.equals(other.newHash);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(filename, oldHash, newHash);
        }
        
        @Override
        public String toString() {
            return "FileChangeEvent{" +
                    "filename='" + filename + '\'' +
                    ", oldHash='" + oldHash + '\'' +
                    ", newHash='" + newHash + '\'' +
                    '}';
        }
    }
    
    /**
     * Starts watching the project directory for file changes.
     * Spawns a background thread that monitors for changes and invokes the onChange callback.
     * 
     * @throws java.io.IOException if unable to create WatchService
     * @throws InterruptedException if the watch thread is interrupted
     */
    public void startWatching() throws java.io.IOException, InterruptedException {
        if (watching) {
            return; // Already watching
        }
        
        watching = true;
        watchThread = new Thread(this::watchDirectoryForChanges);
        watchThread.setName("ProjectFileWatcher-" + projectDirectory.getName());
        watchThread.setDaemon(true);
        watchThread.start();
    }
    
    /**
     * Stops watching the project directory.
     */
    public void stopWatching() {
        watching = false;
        if (watchService != null) {
            try {
                watchService.close();
            } catch (java.io.IOException e) {
                // Suppress on close
            }
            watchService = null;
        }
        if (watchThread != null && watchThread.isAlive()) {
            try {
                watchThread.join(5000); // Wait up to 5 seconds
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Returns the current MD5 checksum of package.json.
     */
    public String getPackageJsonMD5() {
        return packageJsonMD5;
    }
    
    /**
     * Returns the current MD5 checksum for a specific .jdpignore file.
     */
    public String getJdpignoreMD5(String filename) {
        return jdpignoreFileMD5s.get(filename);
    }
    
    /**
     * Internal method: watches the directory for file changes and emits events.
     */
    private void watchDirectoryForChanges() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path path = projectDirectory.toPath();
            path.register(
                    watchService,
                    new WatchEvent.Kind[]{StandardWatchEventKinds.ENTRY_MODIFY},
                    SensitivityWatchEventModifier.HIGH
            );
            
            while (watching) {
                try {
                    WatchKey key = watchService.take();
                    
                    for (WatchEvent<?> event : key.pollEvents()) {
                        String filename = event.context().toString();
                        handleFileChanged(filename);
                    }
                    
                    watching = key.reset();
                } catch (ClosedWatchServiceException e) {
                    watching = false;
                }
            }
        } catch (java.io.IOException e) {
            System.err.println("Error initializing watch service: " + e.getMessage());
            e.printStackTrace(System.err);
            watching = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            watching = false;
        } finally {
            if (watchService != null) {
                try {
                    watchService.close();
                } catch (java.io.IOException e) {
                    // Suppress on close
                }
            }
        }
    }
    
    /**
     * Internal method: processes a single file change event.
     */
    private void handleFileChanged(String filename) {
        if ("package.json".equals(filename)) {
            handlePackageJsonChanged();
        } else if (filename.startsWith(".jdpignore")) {
            handleJdpignoreChanged(filename);
        }
    }
    
    /**
     * Internal method: handles package.json changes.
     */
    private void handlePackageJsonChanged() {
        try {
            String oldHash = packageJsonMD5;
            String newHash = "";
            
            if (packageJsonFile.exists()) {
                newHash = MD5.getMD5Checksum(packageJsonFile);
            }
            
            // Only emit event if hash actually changed
            if (!oldHash.equals(newHash)) {
                packageJsonMD5 = newHash;
                onChange.accept(new FileChangeEvent("package.json", oldHash, newHash));
            }
        } catch (Exception e) {
            System.err.println("Error handling package.json change: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
    
    /**
     * Internal method: handles .jdpignore file changes.
     */
    private void handleJdpignoreChanged(String filename) {
        try {
            File jdpignoreFile = new File(projectDirectory, filename);
            
            String oldHash = jdpignoreFileMD5s.getOrDefault(filename, "");
            String newHash = "";
            
            if (jdpignoreFile.exists()) {
                newHash = MD5.getMD5Checksum(jdpignoreFile);
            }
            
            // Only emit event if hash actually changed
            if (!oldHash.equals(newHash)) {
                jdpignoreFileMD5s.put(filename, newHash);
                onChange.accept(new FileChangeEvent(filename, oldHash, newHash));
            }
        } catch (Exception e) {
            System.err.println("Error handling " + filename + " change: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
    
    /**
     * Internal method: updates the stored MD5 for package.json.
     */
    private void updatePackageJsonMD5() throws java.io.IOException {
        if (packageJsonFile.exists()) {
            packageJsonMD5 = MD5.getMD5Checksum(packageJsonFile);
        } else {
            packageJsonMD5 = "";
        }
    }
    
    /**
     * Internal method: updates stored MD5s for all .jdpignore files.
     */
    private void updateJdpignoreFileMD5s() {
        try {
            jdpignoreFileMD5s.clear();
            
            // Track global .jdpignore file
            File globalIgnoreFile = new File(projectDirectory, ".jdpignore");
            if (globalIgnoreFile.exists()) {
                jdpignoreFileMD5s.put(".jdpignore", MD5.getMD5Checksum(globalIgnoreFile));
            }
            
            // Track platform-specific .jdpignore files
            ca.weblite.jdeploy.models.Platform[] platforms = ca.weblite.jdeploy.models.Platform.values();
            for (ca.weblite.jdeploy.models.Platform platform : platforms) {
                if (platform == ca.weblite.jdeploy.models.Platform.DEFAULT) {
                    continue;
                }
                String filename = ".jdpignore." + platform.getIdentifier();
                File platformIgnoreFile = new File(projectDirectory, filename);
                if (platformIgnoreFile.exists()) {
                    jdpignoreFileMD5s.put(filename, MD5.getMD5Checksum(platformIgnoreFile));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to update .jdpignore file MD5s: " + e.getMessage());
        }
    }
    
    /**
     * Refreshes all stored MD5 checksums. Call this after external file modifications
     * to ensure subsequent change detection works correctly.
     */
    public void refreshChecksums() throws java.io.IOException {
        updatePackageJsonMD5();
        updateJdpignoreFileMD5s();
    }
}
