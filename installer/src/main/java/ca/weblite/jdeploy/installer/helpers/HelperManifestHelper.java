package ca.weblite.jdeploy.installer.helpers;

import ca.weblite.jdeploy.installer.uninstall.UninstallManifestBuilder;
import ca.weblite.jdeploy.installer.uninstall.model.UninstallManifest;
import ca.weblite.tools.platform.Platform;

import java.io.File;

/**
 * Utility class for adding Helper installation entries to the uninstall manifest.
 *
 * This class handles the platform-specific differences in how Helper files
 * should be tracked for uninstallation:
 * <ul>
 *   <li>macOS: The .app bundle is a directory that should be deleted entirely</li>
 *   <li>Windows/Linux: The helper executable is a single file in a helpers directory</li>
 * </ul>
 *
 * @author jDeploy Team
 */
public class HelperManifestHelper {

    /**
     * Adds Helper installation entries to the uninstall manifest.
     *
     * This method adds appropriate entries based on the current platform:
     *
     * <p><b>macOS:</b></p>
     * <ul>
     *   <li>Helper executable (.app bundle) - ALWAYS strategy (delete entire bundle)</li>
     *   <li>Helper context directory (.jdeploy-files) - ALWAYS strategy (delete entirely)</li>
     *   <li>Parent helper directory - IF_EMPTY strategy (delete only if empty)</li>
     * </ul>
     *
     * <p><b>Windows/Linux:</b></p>
     * <ul>
     *   <li>Helper executable - added as a file entry</li>
     *   <li>Helper context directory (.jdeploy-files) - ALWAYS strategy (delete entirely)</li>
     *   <li>Parent helpers directory - IF_EMPTY strategy (delete only if empty)</li>
     * </ul>
     *
     * @param builder The UninstallManifestBuilder to add entries to
     * @param result The HelperInstallationResult containing paths to installed files
     * @throws IllegalArgumentException if builder or result is null, or if result indicates failure
     */
    public static void addHelperToManifest(UninstallManifestBuilder builder, HelperInstallationResult result) {
        if (builder == null) {
            throw new IllegalArgumentException("builder cannot be null");
        }
        if (result == null) {
            throw new IllegalArgumentException("result cannot be null");
        }
        if (!result.isSuccess()) {
            throw new IllegalArgumentException("Cannot add failed installation to manifest: " + result.getErrorMessage());
        }

        File helperExecutable = result.getHelperExecutable();
        File helperContextDirectory = result.getHelperContextDirectory();

        if (helperExecutable == null || helperContextDirectory == null) {
            throw new IllegalArgumentException("Helper paths cannot be null in successful result");
        }

        Platform platform = Platform.getSystemPlatform();

        if (platform.isMac()) {
            addMacOSEntries(builder, helperExecutable, helperContextDirectory);
        } else {
            addWindowsLinuxEntries(builder, helperExecutable, helperContextDirectory);
        }
    }

    /**
     * Adds macOS-specific manifest entries.
     *
     * On macOS:
     * - The helper executable is a .app bundle (directory)
     * - The context directory is a sibling to the .app bundle
     * - Both are inside ~/Applications/{AppName} Helper/
     */
    private static void addMacOSEntries(UninstallManifestBuilder builder,
                                        File helperExecutable,
                                        File helperContextDirectory) {
        // Add the .app bundle directory - delete entirely
        builder.addDirectory(
            helperExecutable.getAbsolutePath(),
            UninstallManifest.CleanupStrategy.ALWAYS,
            "Helper application bundle"
        );

        // Add the context directory - delete entirely
        builder.addDirectory(
            helperContextDirectory.getAbsolutePath(),
            UninstallManifest.CleanupStrategy.ALWAYS,
            "Helper context directory"
        );

        // Add parent helper directory - delete only if empty
        // On macOS this is ~/Applications/{AppName} Helper/
        File parentHelperDir = helperExecutable.getParentFile();
        if (parentHelperDir != null) {
            builder.addDirectory(
                parentHelperDir.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.IF_EMPTY,
                "Helper installation directory"
            );
        }
    }

    /**
     * Adds Windows/Linux-specific manifest entries.
     *
     * On Windows/Linux:
     * - The helper executable is a single file (.exe on Windows, no extension on Linux)
     * - The context directory is inside the helpers directory
     * - Both are inside {appDirectory}/helpers/
     */
    private static void addWindowsLinuxEntries(UninstallManifestBuilder builder,
                                               File helperExecutable,
                                               File helperContextDirectory) {
        // Add the helper executable as a file entry
        builder.addFile(
            helperExecutable.getAbsolutePath(),
            UninstallManifest.FileType.BINARY,
            "Helper executable"
        );

        // Add the context directory - delete entirely
        builder.addDirectory(
            helperContextDirectory.getAbsolutePath(),
            UninstallManifest.CleanupStrategy.ALWAYS,
            "Helper context directory"
        );

        // Add parent helpers directory - delete only if empty
        // On Windows/Linux this is {appDirectory}/helpers/
        File parentHelpersDir = helperExecutable.getParentFile();
        if (parentHelpersDir != null) {
            builder.addDirectory(
                parentHelpersDir.getAbsolutePath(),
                UninstallManifest.CleanupStrategy.IF_EMPTY,
                "Helpers directory"
            );
        }
    }
}
