package ca.weblite.jdeploy.installer.uninstall;

import ca.weblite.jdeploy.installer.ai.services.AiIntegrationUninstaller;
import ca.weblite.jdeploy.installer.cli.UnixPathManager;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.CleanupStrategy;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledDirectory;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.InstalledFile;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.ModifiedRegistryValue;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.PathModifications;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.RegistryKey;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.ShellProfileEntry;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.GitBashProfileEntry;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest.WindowsPathEntry;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.installer.util.PackagePathResolver;
import ca.weblite.jdeploy.installer.win.InstallWindowsRegistry;
import ca.weblite.jdeploy.installer.win.RegistryOperations;
import org.apache.commons.io.FileUtils;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service for orchestrating the uninstallation of jDeploy packages.
 *
 * Loads uninstall manifests (if available) and executes cleanup phases in the correct order:
 * 1. Files - delete in listed order (from manifest)
 * 2. Directories - process by cleanup strategy (from manifest)
 * 3. Registry (Windows only) - delete created keys, restore modified values (from manifest)
 * 4. PATH modifications - Windows registry PATH and Unix shell profiles (from manifest)
 * 5. Package directories - delete package and commands directories
 * 6. Self-cleanup - delete manifest file and empty parent directories
 *
 * If no manifest exists, logs a warning and continues with package directory cleanup.
 *
 * Idempotent and fault-tolerant: skips already-deleted items with logging,
 * continues on individual failures, and aggregates all errors for reporting.
 */
@Singleton
public class UninstallService {
    private static final Logger LOGGER = Logger.getLogger(UninstallService.class.getName());
    
    private final FileUninstallManifestRepository manifestRepository;
    private final RegistryOperations registryOperations;

    @Inject
    public UninstallService(
            FileUninstallManifestRepository manifestRepository,
            RegistryOperations registryOperations
    ) {
        this.manifestRepository = manifestRepository;
        this.registryOperations = registryOperations;
    }

    /**
     * Uninstalls a package by loading its manifest (if available) and executing cleanup phases.
     *
     * @param packageName the package name (e.g., "my-package")
     * @param source the source identifier (e.g., "npm", "github", or null for default)
     * @return UninstallResult containing success/failure counts and error messages
     */
    public UninstallResult uninstall(String packageName, String source) {
        UninstallResult result = new UninstallResult();

        // Load manifest from repository
        Optional<UninstallManifest> manifestOpt = Optional.empty();
        try {
            manifestOpt = manifestRepository.load(packageName, source);
        } catch (Exception e) {
            LOGGER.warning("Failed to load uninstall manifest for package: " + packageName +
                         (source != null ? " (source: " + source + ")" : "") + " - " + e.getMessage());
        }

        if (!manifestOpt.isPresent()) {
            LOGGER.info("No uninstall manifest found for package: " + packageName +
                         (source != null ? " (source: " + source + ")" : "") +
                         " - will attempt cleanup of package directories");
        }

        try {
            // Phase 1-4: Process manifest-based cleanup if manifest exists
            if (manifestOpt.isPresent()) {
                UninstallManifest manifest = manifestOpt.get();

                // Phase 1: Delete files
                deleteInstalledFiles(manifest.getFiles(), result);

                // Phase 2: Clean up directories
                cleanupDirectories(manifest.getDirectories(), result);

                // Phase 3: Clean up registry (Windows only)
                cleanupRegistry(manifest.getRegistry(), result);

                // Phase 4: Clean up PATH modifications
                cleanupPathModifications(manifest.getPathModifications(), result);

                // Phase 4.5: Clean up AI integrations
                cleanupAiIntegrations(manifest.getAiIntegrations(), packageName, source, result);
            }

            // Phase 5: Clean up package directories (always execute, even without manifest)
            cleanupPackageDirectories(packageName, source, result);

            // Phase 6: Self-cleanup (delete manifest and empty parent dirs)
            // ONLY delete manifest if all prior phases succeeded - preserves manifest for retry if errors occurred
            if (manifestOpt.isPresent() && result.isSuccess()) {
                selfCleanup(packageName, source, result);
            } else if (manifestOpt.isPresent() && !result.isSuccess()) {
                LOGGER.info("Skipping manifest deletion due to prior errors - manifest preserved for retry");
            }

        } catch (Exception e) {
            LOGGER.severe("Unexpected error during uninstall: " + e.getMessage());
            result.addError("Unexpected error during uninstall: " + e.getMessage());
        }

        // Log overall uninstall completion status
        if (result.isSuccess()) {
            LOGGER.info("Uninstall completed successfully for package: " + packageName +
                       (source != null ? " (source: " + source + ")" : ""));
        } else {
            LOGGER.info("Uninstall completed with " + result.getFailureCount() + " error(s) for package: " +
                       packageName + (source != null ? " (source: " + source + ")" : ""));
        }

        return result;
    }

    /**
     * Phase 1: Delete installed files in the order they are listed.
     * Skips non-existent files with logging (idempotent).
     */
    private void deleteInstalledFiles(List<InstalledFile> files, UninstallResult result) {
        deleteFiles(files, result);
    }

    /**
     * Delete files using Files.deleteIfExists(), handling both regular files and symbolic links.
     * Iterates through files in order, logs successes, and records failures without aborting.
     * 
     * @param files list of InstalledFile objects to delete
     * @param result UninstallResult to accumulate success/failure counts and error messages
     */
    private void deleteFiles(List<InstalledFile> files, UninstallResult result) {
        if (files == null || files.isEmpty()) {
            return;
        }
        
        for (InstalledFile file : files) {
            try {
                boolean deleted = Files.deleteIfExists(Paths.get(file.getPath()));
                
                if (deleted) {
                    result.incrementSuccessCount();
                    LOGGER.fine("Deleted file: " + file.getPath());
                } else {
                    LOGGER.fine("File already deleted or does not exist: " + file.getPath());
                }
                
            } catch (IOException e) {
                String errorMsg = "Failed to delete file: " + file.getPath() + " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Phase 2: Clean up directories according to their cleanup strategy.
     * 
     * Strategies:
     * - ALWAYS: delete directory and all contents recursively
     * - IF_EMPTY: delete only if empty after file removal
     * - CONTENTS_ONLY: delete contents but keep the directory itself
     */
    private void cleanupDirectories(List<InstalledDirectory> directories, UninstallResult result) {
        if (directories == null || directories.isEmpty()) {
            return;
        }
        
        for (InstalledDirectory dir : directories) {
            try {
                File dirFile = new File(dir.getPath());
                
                if (!dirFile.exists()) {
                    LOGGER.fine("Directory already deleted or does not exist: " + dir.getPath());
                    continue;
                }
                
                CleanupStrategy strategy = dir.getCleanup();
                
                switch (strategy) {
                    case ALWAYS:
                        FileUtils.deleteDirectory(dirFile);
                        result.incrementSuccessCount();
                        LOGGER.fine("Deleted directory (ALWAYS): " + dir.getPath());
                        break;
                        
                    case IF_EMPTY:
                        if (isDirEmpty(dirFile)) {
                            FileUtils.forceDelete(dirFile);
                            result.incrementSuccessCount();
                            LOGGER.fine("Deleted empty directory (IF_EMPTY): " + dir.getPath());
                        } else {
                            LOGGER.fine("Directory not empty, skipping (IF_EMPTY): " + dir.getPath());
                        }
                        break;
                        
                    case CONTENTS_ONLY:
                        FileUtils.cleanDirectory(dirFile);
                        result.incrementSuccessCount();
                        LOGGER.fine("Deleted directory contents (CONTENTS_ONLY): " + dir.getPath());
                        break;
                }
                
            } catch (IOException e) {
                String errorMsg = "Failed to clean directory: " + dir.getPath() + " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Phase 3: Clean up Windows registry entries.
     * - Delete created keys (in reverse order of creation for safety)
     * - Restore modified values (or delete if no previous value exists)
     * Only runs on Windows; skipped on other operating systems.
     */
    private void cleanupRegistry(UninstallManifest.RegistryInfo registryInfo, UninstallResult result) {
        if (registryInfo == null) {
            return;
        }

        // Guard: Only process registry on Windows
        String osName = System.getProperty("os.name");
        if (osName == null || !osName.toLowerCase().contains("windows")) {
            LOGGER.fine("Skipping registry cleanup on non-Windows OS: " + osName);
            return;
        }

        // Delete created keys in reverse order
        List<RegistryKey> createdKeys = registryInfo.getCreatedKeys();
        if (createdKeys != null && !createdKeys.isEmpty()) {
            // Process in reverse to delete leaf keys first
            for (int i = createdKeys.size() - 1; i >= 0; i--) {
                RegistryKey key = createdKeys.get(i);
                try {
                    if (registryOperations.keyExists(key.getPath())) {
                        // Use recursive deletion to handle keys with subkeys/values
                        deleteKeyRecursive(key.getPath());
                        result.incrementSuccessCount();
                        LOGGER.fine("Deleted registry key: " + key.getPath());
                    } else {
                        LOGGER.fine("Registry key already deleted or does not exist: " + key.getPath());
                    }
                } catch (Exception e) {
                    String errorMsg = "Failed to delete registry key: " + key.getPath() +
                                    " - " + e.getMessage();
                    LOGGER.warning(errorMsg);
                    result.addError(errorMsg);
                    result.incrementFailureCount();
                }
            }
        }
        
        // Restore modified values
        List<ModifiedRegistryValue> modifiedValues = registryInfo.getModifiedValues();
        if (modifiedValues != null && !modifiedValues.isEmpty()) {
            for (ModifiedRegistryValue modValue : modifiedValues) {
                try {
                    if (modValue.getPreviousValue() != null) {
                        // Restore the previous value
                        registryOperations.setStringValue(
                            modValue.getPath(),
                            modValue.getName(),
                            modValue.getPreviousValue()
                        );
                        result.incrementSuccessCount();
                        LOGGER.fine("Restored registry value: " + modValue.getPath() + 
                                  " \\ " + modValue.getName());
                    } else {
                        // No previous value, so delete the value
                        if (registryOperations.valueExists(modValue.getPath(), modValue.getName())) {
                            registryOperations.deleteValue(modValue.getPath(), modValue.getName());
                            result.incrementSuccessCount();
                            LOGGER.fine("Deleted registry value: " + modValue.getPath() + 
                                      " \\ " + modValue.getName());
                        }
                    }
                } catch (Exception e) {
                    String errorMsg = "Failed to restore/delete registry value: " + 
                                    modValue.getPath() + " \\ " + modValue.getName() + 
                                    " - " + e.getMessage();
                    LOGGER.warning(errorMsg);
                    result.addError(errorMsg);
                    result.incrementFailureCount();
                }
            }
        }
    }

    /**
     * Phase 4: Clean up PATH modifications.
     * - Windows: remove entries from HKCU\Environment\Path
     * - Unix/macOS: remove export lines from shell configuration files
     * - Git Bash: remove entries from Git Bash profiles (.bashrc, .bash_profile)
     */
    private void cleanupPathModifications(PathModifications pathMods, UninstallResult result) {
        if (pathMods == null) {
            return;
        }
        
        // Windows PATH cleanup
        cleanupWindowsPaths(pathMods.getWindowsPaths(), result);
        
        // Unix shell profile cleanup
        cleanupUnixShellProfiles(pathMods.getShellProfiles(), result);
        
        // Git Bash profile cleanup
        cleanupGitBashProfiles(pathMods.getGitBashProfiles(), result);
    }

    /**
     * Clean up Windows PATH entries from HKCU\Environment\Path registry.
     */
    private void cleanupWindowsPaths(List<WindowsPathEntry> windowsPaths, UninstallResult result) {
        if (windowsPaths == null || windowsPaths.isEmpty()) {
            return;
        }
        
        for (WindowsPathEntry entry : windowsPaths) {
            try {
                File binDir = new File(entry.getAddedEntry());
                InstallWindowsRegistry winRegistry = new InstallWindowsRegistry(
                    null, null, null, null, registryOperations
                );
                if (winRegistry.removeFromUserPath(binDir)) {
                    result.incrementSuccessCount();
                    LOGGER.fine("Removed from Windows PATH: " + entry.getAddedEntry());
                } else {
                    LOGGER.fine("Windows PATH entry not found or already removed: " + 
                              entry.getAddedEntry());
                }
            } catch (Exception e) {
                String errorMsg = "Failed to remove from Windows PATH: " + 
                                entry.getAddedEntry() + " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Clean up Unix/macOS shell profile entries from configuration files.
     */
    private void cleanupUnixShellProfiles(List<ShellProfileEntry> shellProfiles, UninstallResult result) {
        if (shellProfiles == null || shellProfiles.isEmpty()) {
            return;
        }
        
        String homeDir = System.getProperty("user.home");
        File homeDirFile = new File(homeDir);
        
        for (ShellProfileEntry entry : shellProfiles) {
            try {
                File configFile = new File(entry.getFile());
                File binDir = extractBinDirFromExportLine(entry.getExportLine());
                
                boolean removed = false;
                if (binDir != null) {
                    removed = UnixPathManager.removePathFromConfigFile(configFile, binDir, homeDirFile);
                }
                if (removed) {
                    result.incrementSuccessCount();
                    LOGGER.fine("Removed from shell profile: " + entry.getFile());
                } else {
                    LOGGER.fine("Shell profile entry not found or already removed: " + 
                              entry.getFile());
                }
            } catch (Exception e) {
                String errorMsg = "Failed to remove from shell profile: " + 
                                entry.getFile() + " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Clean up Git Bash profile entries from configuration files (.bashrc, .bash_profile).
     */
    private void cleanupGitBashProfiles(List<GitBashProfileEntry> gitBashProfiles, UninstallResult result) {
        if (gitBashProfiles == null || gitBashProfiles.isEmpty()) {
            return;
        }
        
        String homeDir = System.getProperty("user.home");
        File homeDirFile = new File(homeDir);
        
        for (GitBashProfileEntry entry : gitBashProfiles) {
            try {
                File configFile = new File(entry.getFile());
                File binDir = extractBinDirFromExportLine(entry.getExportLine());
                
                boolean removed = false;
                if (binDir != null) {
                    removed = UnixPathManager.removePathFromConfigFile(configFile, binDir, homeDirFile);
                }
                if (removed) {
                    result.incrementSuccessCount();
                    LOGGER.fine("Removed from Git Bash profile: " + entry.getFile());
                } else {
                    LOGGER.fine("Git Bash profile entry not found or already removed: " + 
                              entry.getFile());
                }
            } catch (Exception e) {
                String errorMsg = "Failed to remove from Git Bash profile: " + 
                                entry.getFile() + " - " + e.getMessage();
                LOGGER.warning(errorMsg);
                result.addError(errorMsg);
                result.incrementFailureCount();
            }
        }
    }

    /**
     * Phase 4.5: Clean up AI integrations (MCP servers, skills, agents).
     */
    private void cleanupAiIntegrations(
            UninstallManifest.AiIntegrations aiIntegrations,
            String packageName,
            String source,
            UninstallResult result) {

        if (aiIntegrations == null) {
            LOGGER.fine("No AI integrations to clean up");
            return;
        }

        try {
            AiIntegrationUninstaller uninstaller = new AiIntegrationUninstaller();
            AiIntegrationUninstaller.UninstallResult aiResult = uninstaller.uninstall(
                aiIntegrations, packageName, source
            );

            // Transfer results to main result
            for (int i = 0; i < aiResult.getSuccessCount(); i++) {
                result.incrementSuccessCount();
            }
            for (int i = 0; i < aiResult.getFailureCount(); i++) {
                result.incrementFailureCount();
            }
            for (String error : aiResult.getErrors()) {
                result.addError(error);
            }

            if (aiResult.isSuccess()) {
                LOGGER.info("AI integrations cleanup completed successfully");
            } else {
                LOGGER.warning("AI integrations cleanup completed with " +
                             aiResult.getFailureCount() + " error(s)");
            }
        } catch (Exception e) {
            String errorMsg = "Failed to clean up AI integrations: " + e.getMessage();
            LOGGER.warning(errorMsg);
            result.addError(errorMsg);
            result.incrementFailureCount();
        }
    }

    /**
     * Phase 5: Clean up package directories - delete package, commands, apps, and uninstaller directories.
     * This runs even if no manifest exists, ensuring directories are cleaned up.
     *
     * Deletes:
     * - Package directory (from PackagePathResolver - both arch-specific and legacy paths)
     * - Commands directory (from CliCommandBinDirResolver - per-app bin directory)
     * - Apps directory (~/.jdeploy/apps/{fullyQualifiedPackageName})
     * - Uninstallers directory (~/.jdeploy/uninstallers/{fullyQualifiedPackageName})
     */
    private void cleanupPackageDirectories(String packageName, String source, UninstallResult result) {
        // Clean up package directories (both architecture-specific and legacy)
        File[] packagePaths = PackagePathResolver.getAllPossiblePackagePaths(packageName, null, source);
        for (File packagePath : packagePaths) {
            if (packagePath.exists()) {
                try {
                    FileUtils.deleteDirectory(packagePath);
                    result.incrementSuccessCount();
                    LOGGER.info("Deleted package directory: " + packagePath.getAbsolutePath());
                } catch (IOException e) {
                    String errorMsg = "Failed to delete package directory: " + packagePath.getAbsolutePath() +
                                    " - " + e.getMessage();
                    LOGGER.warning(errorMsg);
                    result.addError(errorMsg);
                    result.incrementFailureCount();
                }
            } else {
                LOGGER.fine("Package directory does not exist: " + packagePath.getAbsolutePath());
            }
        }

        // Clean up commands directory (per-app bin directory)
        try {
            File commandsDir = CliCommandBinDirResolver.getPerAppBinDir(packageName, source);
            if (commandsDir.exists()) {
                FileUtils.deleteDirectory(commandsDir);
                result.incrementSuccessCount();
                LOGGER.info("Deleted commands directory: " + commandsDir.getAbsolutePath());
            } else {
                LOGGER.fine("Commands directory does not exist: " + commandsDir.getAbsolutePath());
            }
        } catch (Exception e) {
            String errorMsg = "Failed to delete commands directory for package: " + packageName +
                            " - " + e.getMessage();
            LOGGER.warning(errorMsg);
            result.addError(errorMsg);
            result.incrementFailureCount();
        }

        // Clean up apps directory (~/.jdeploy/apps/{fullyQualifiedPackageName})
        try {
            String fullyQualifiedName = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);
            File jdeployHome = new File(System.getProperty("user.home"), ".jdeploy");
            File appsDir = new File(jdeployHome, "apps");
            File appDir = new File(appsDir, fullyQualifiedName);

            if (appDir.exists()) {
                FileUtils.deleteDirectory(appDir);
                result.incrementSuccessCount();
                LOGGER.info("Deleted apps directory: " + appDir.getAbsolutePath());
            } else {
                LOGGER.fine("Apps directory does not exist: " + appDir.getAbsolutePath());
            }
        } catch (Exception e) {
            String errorMsg = "Failed to delete apps directory for package: " + packageName +
                            " - " + e.getMessage();
            LOGGER.warning(errorMsg);
            result.addError(errorMsg);
            result.incrementFailureCount();
        }

        // Clean up uninstallers directory (~/.jdeploy/uninstallers/{fullyQualifiedPackageName})
        try {
            String fullyQualifiedName = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);
            File jdeployHome = new File(System.getProperty("user.home"), ".jdeploy");
            File uninstallersDir = new File(jdeployHome, "uninstallers");
            File uninstallerDir = new File(uninstallersDir, fullyQualifiedName);

            if (uninstallerDir.exists()) {
                FileUtils.deleteDirectory(uninstallerDir);
                result.incrementSuccessCount();
                LOGGER.info("Deleted uninstallers directory: " + uninstallerDir.getAbsolutePath());
            } else {
                LOGGER.fine("Uninstallers directory does not exist: " + uninstallerDir.getAbsolutePath());
            }
        } catch (Exception e) {
            String errorMsg = "Failed to delete uninstallers directory for package: " + packageName +
                            " - " + e.getMessage();
            LOGGER.warning(errorMsg);
            result.addError(errorMsg);
            result.incrementFailureCount();
        }
    }

    /**
     * Phase 6: Self-cleanup - delete the manifest file and empty parent directories.
     */
    private void selfCleanup(String packageName, String source, UninstallResult result) {
        try {
            // Delete the manifest file via repository
            manifestRepository.delete(packageName, source);
            result.incrementSuccessCount();
            LOGGER.info("Deleted uninstall manifest for: " + packageName);
            
            // Attempt to clean up empty parent directories
            cleanupEmptyParentDirs(packageName, source, result);
            
            // Log completion of manifest self-cleanup phase
            LOGGER.info("Manifest self-cleanup phase completed for package: " + packageName + 
                       (source != null ? " (source: " + source + ")" : ""));
            
        } catch (Exception e) {
            String errorMsg = "Failed to delete manifest: " + e.getMessage();
            LOGGER.warning(errorMsg);
            result.addError(errorMsg);
            result.incrementFailureCount();
        }
    }

    /**
     * Helper to clean up empty parent directories of the manifest location.
     */
    private void cleanupEmptyParentDirs(String packageName, String source, UninstallResult result) {
        try {
            String userHome = System.getProperty("user.home");
            File jdeployDir = new File(userHome, ".jdeploy");
            File manifestsDir = new File(jdeployDir, "manifests");
            File packageDir = new File(manifestsDir, packageName);
            
            // Try to delete packageDir if empty
            if (packageDir.exists() && packageDir.isDirectory() && isDirEmpty(packageDir)) {
                FileUtils.forceDelete(packageDir);
                LOGGER.fine("Deleted empty package directory: " + packageDir.getAbsolutePath());
            }
            
            // Try to delete manifestsDir if empty
            if (manifestsDir.exists() && manifestsDir.isDirectory() && isDirEmpty(manifestsDir)) {
                FileUtils.forceDelete(manifestsDir);
                LOGGER.fine("Deleted empty manifests directory");
            }
            
            // Try to delete jdeployDir if empty
            if (jdeployDir.exists() && jdeployDir.isDirectory() && isDirEmpty(jdeployDir)) {
                FileUtils.forceDelete(jdeployDir);
                LOGGER.fine("Deleted empty .jdeploy directory");
            }
            
        } catch (IOException e) {
            LOGGER.fine("Could not clean up empty parent directories: " + e.getMessage());
            // Don't add to errors - this is non-critical
        }
    }

    /**
     * Helper to extract bin directory path from an export line.
     * Parses lines like: export PATH="/path/to/bin:$PATH" or export PATH="$HOME/bin:$PATH"
     * Handles both absolute paths and $HOME-relative paths.
     */
    private File extractBinDirFromExportLine(String exportLine) {
        if (exportLine == null || exportLine.isEmpty()) {
            return null;
        }
        
        // Try to extract path from export PATH="..." pattern
        int pathIndex = exportLine.indexOf("\"");
        if (pathIndex >= 0) {
            int endIndex = exportLine.indexOf("\"", pathIndex + 1);
            if (endIndex > pathIndex) {
                String pathValue = exportLine.substring(pathIndex + 1, endIndex);
                
                // Remove :$PATH suffix if present
                if (pathValue.contains(":")) {
                    pathValue = pathValue.substring(0, pathValue.indexOf(":"));
                }
                
                // Expand $HOME to actual home directory path
                if (pathValue.contains("$HOME")) {
                    String homeDir = System.getProperty("user.home");
                    pathValue = pathValue.replace("$HOME", homeDir);
                }
                
                if (!pathValue.isEmpty()) {
                    return new File(pathValue);
                }
            }
        }
        
        return null;
    }

    /**
     * Helper to check if a directory is empty.
     */
    private boolean isDirEmpty(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return true;
        }
        File[] files = dir.listFiles();
        return files == null || files.length == 0;
    }

    /**
     * Recursively delete a registry key and all its subkeys and values.
     *
     * @param key the registry key path to delete
     */
    private void deleteKeyRecursive(String key) {
        if (!registryOperations.keyExists(key)) {
            return;
        }
        // First delete all subkeys recursively
        for (String subkey : registryOperations.getKeys(key)) {
            deleteKeyRecursive(key + "\\" + subkey);
        }
        // Then delete all values
        for (String valueKey : registryOperations.getValues(key).keySet()) {
            registryOperations.deleteValue(key, valueKey);
        }
        // Finally delete the key itself
        registryOperations.deleteKey(key);
    }

    /**
     * Result object tracking uninstall operation success/failure counts and errors.
     */
    public static final class UninstallResult {
        private int successCount = 0;
        private int failureCount = 0;
        private final List<String> errors = new ArrayList<>();

        public void incrementSuccessCount() {
            this.successCount++;
        }

        public void incrementFailureCount() {
            this.failureCount++;
        }

        public void addError(String error) {
            this.errors.add(error);
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailureCount() {
            return failureCount;
        }

        public List<String> getErrors() {
            return Collections.unmodifiableList(errors);
        }

        public boolean isSuccess() {
            return failureCount == 0;
        }

        @Override
        public String toString() {
            return "UninstallResult{" +
                   "successCount=" + successCount +
                   ", failureCount=" + failureCount +
                   ", errors=" + errors +
                   '}';
        }
    }
}
