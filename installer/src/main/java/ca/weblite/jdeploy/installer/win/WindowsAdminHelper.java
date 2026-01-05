package ca.weblite.jdeploy.installer.win;

import ca.weblite.tools.platform.Platform;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Helper class for Windows administrator privilege detection and elevation.
 *
 * @author Steve Hannah
 */
public class WindowsAdminHelper {

    /**
     * Checks if the current process is running with administrator privileges on Windows.
     * On non-Windows platforms, always returns true (no elevation needed).
     *
     * @return true if running as admin or not on Windows, false otherwise
     */
    public static boolean isRunningAsAdmin() {
        if (!Platform.getSystemPlatform().isWindows()) {
            return true;
        }

        try {
            // Use 'net session' command - it will fail if not running as admin
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "net", "session");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Consume output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            while (reader.readLine() != null) {
                // Discard
            }

            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            // If we can't determine, assume not admin
            return false;
        }
    }

    /**
     * Checks if the installer can be relaunched as administrator.
     * This requires the jdeploy.launcher.path system property to be set
     * and point to an existing executable file.
     *
     * @return true if relaunch is possible, false otherwise
     */
    public static boolean canRelaunchAsAdmin() {
        if (!Platform.getSystemPlatform().isWindows()) {
            return false;
        }

        String launcherPath = System.getProperty("jdeploy.launcher.path");
        if (launcherPath == null || launcherPath.isEmpty()) {
            return false;
        }

        File launcherFile = new File(launcherPath);
        return launcherFile.exists() && launcherFile.isFile();
    }

    /**
     * Relaunches the installer with administrator privileges.
     * Uses PowerShell Start-Process with -Verb RunAs to trigger UAC elevation.
     *
     * Requires the jdeploy.launcher.path system property to be set to the
     * path of the installer executable.
     *
     * @return true if relaunch was initiated successfully, false if relaunch is not possible
     */
    public static boolean relaunchAsAdmin() {
        if (!Platform.getSystemPlatform().isWindows()) {
            return false;
        }

        String launcherPath = System.getProperty("jdeploy.launcher.path");
        if (launcherPath == null || launcherPath.isEmpty()) {
            return false;
        }

        File launcherFile = new File(launcherPath);
        if (!launcherFile.exists() || !launcherFile.isFile()) {
            return false;
        }

        try {
            // Build PowerShell command to relaunch with elevation
            String escapedPath = launcherPath.replace("'", "''");

            String psCommand = String.format(
                "Start-Process -FilePath '%s' -Verb RunAs",
                escapedPath
            );

            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-ExecutionPolicy", "Bypass", "-Command", psCommand
            );
            pb.start();

            return true;
        } catch (Exception e) {
            System.err.println("Failed to relaunch as admin: " + e.getMessage());
            e.printStackTrace(System.err);
            return false;
        }
    }
}
