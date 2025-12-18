package ca.weblite.jdeploy.installer.mac;

import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

public class MacAdminLauncherGenerator {
    public static final String ADMIN_LAUNCHER_SUFFIX = " (Run as admin)";

    public File getAdminLauncherFile(File sourceApp) {
        if (!sourceApp.getName().endsWith(".app")) {
            throw new IllegalArgumentException("Source must be a .app bundle: " + sourceApp);
        }
        String sourceAppName = sourceApp.getName().substring(0, sourceApp.getName().length() - 4);
        return new File(sourceApp.getParentFile(), sourceAppName + ADMIN_LAUNCHER_SUFFIX + ".app");
    }

    public File generateAdminLauncher(File sourceApp) throws IOException {
        if (!System.getProperty("os.name").toLowerCase().contains("mac")) {
            throw new UnsupportedOperationException("Admin launcher generation is only supported on macOS");
        }

        if (!sourceApp.exists() || !sourceApp.isDirectory()) {
            throw new IllegalArgumentException("Source app does not exist or is not a directory: " + sourceApp);
        }

        if (!sourceApp.getName().endsWith(".app")) {
            throw new IllegalArgumentException("Source must be a .app bundle: " + sourceApp);
        }

        String sourceAppName = sourceApp.getName().substring(0, sourceApp.getName().length() - 4);
        File adminApp = getAdminLauncherFile(sourceApp);

        System.out.println("Creating admin launcher for: " + sourceApp.getAbsolutePath());

        String executablePath = findExecutablePath(sourceApp);
        System.out.println("Found executable at: " + executablePath);

        createShellScriptApp(sourceApp, adminApp, executablePath);
        System.out.println("Created shell script app at: " + adminApp.getAbsolutePath());

        copyIcon(sourceApp, adminApp);
        System.out.println("Copied icon from source app");

        makeAppExecutable(adminApp);
        System.out.println("Made admin app executable");

        return adminApp;
    }

    private String findExecutablePath(File appBundle) throws IOException {
        File infoPlist = new File(appBundle, "Contents/Info.plist");
        if (!infoPlist.exists()) {
            throw new IOException("Info.plist not found in app bundle: " + infoPlist);
        }

        String executableName = parseExecutableName(infoPlist);
        if (executableName == null || executableName.isEmpty()) {
            executableName = appBundle.getName().substring(0, appBundle.getName().length() - 4);
        }

        File executableFile = new File(appBundle, "Contents/MacOS/" + executableName);
        if (!executableFile.exists()) {
            executableFile = new File(appBundle, "Contents/MacOS/Client4JLauncher");
            if (!executableFile.exists()) {
                throw new IOException("Executable not found in app bundle");
            }
        }

        return executableFile.getAbsolutePath();
    }

    private String parseExecutableName(File infoPlist) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "/usr/libexec/PlistBuddy",
                "-c", "Print :CFBundleExecutable",
                infoPlist.getAbsolutePath()
            );

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                if (process.exitValue() == 0 && line != null) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Could not parse executable name from Info.plist: " + e.getMessage());
        }
        return null;
    }

    private void createShellScriptApp(File sourceApp, File adminApp, String executablePath) throws IOException {
        // Extract the app bundle path from the executable path
        String appBundlePath = executablePath;
        int contentsIndex = appBundlePath.indexOf("/Contents/MacOS/");
        if (contentsIndex > 0) {
            appBundlePath = appBundlePath.substring(0, contentsIndex);
        }

        // Create the app bundle structure
        File contentsDir = new File(adminApp, "Contents");
        File macosDir = new File(contentsDir, "MacOS");
        File resourcesDir = new File(contentsDir, "Resources");

        macosDir.mkdirs();
        resourcesDir.mkdirs();

        // Create the shell script launcher
        File launcherScript = new File(macosDir, sourceApp.getName().replace(".app", "") + "_Admin");
        String shellScript = "#!/bin/bash\n" +
                           "\n" +
                           "# Admin launcher for " + sourceApp.getName() + "\n" +
                           "\n" +
                           "APP_PATH=\"" + appBundlePath + "\"\n" +
                           "EXEC_PATH=\"" + executablePath + "\"\n" +
                           "\n" +
                           "if [ $# -eq 0 ]; then\n" +
                           "    # No arguments - use open command for better app launching\n" +
                           "    osascript -e \"do shell script \\\"open -a '$APP_PATH'\\\" with administrator privileges\"\n" +
                           "else\n" +
                           "    # With arguments - use executable directly\n" +
                           "    osascript -e \"do shell script \\\"'$EXEC_PATH' $*\\\" with administrator privileges\"\n" +
                           "fi\n";

        Files.write(launcherScript.toPath(), shellScript.getBytes(StandardCharsets.UTF_8));
        launcherScript.setExecutable(true);

        // Create Info.plist
        String sourceAppName = sourceApp.getName().replace(".app", "");
        String infoPlistContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                "<!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">\n" +
                                "<plist version=\"1.0\">\n" +
                                "<dict>\n" +
                                "    <key>CFBundleExecutable</key>\n" +
                                "    <string>" + sourceAppName + "_Admin</string>\n" +
                                "    <key>CFBundleIconFile</key>\n" +
                                "    <string>icon</string>\n" +
                                "    <key>CFBundleIdentifier</key>\n" +
                                "    <string>com.jdeploy.admin." + sourceAppName.toLowerCase() + "</string>\n" +
                                "    <key>CFBundleName</key>\n" +
                                "    <string>" + sourceAppName + ADMIN_LAUNCHER_SUFFIX +"</string>\n" +
                                "    <key>CFBundlePackageType</key>\n" +
                                "    <string>APPL</string>\n" +
                                "    <key>CFBundleShortVersionString</key>\n" +
                                "    <string>1.0</string>\n" +
                                "    <key>CFBundleVersion</key>\n" +
                                "    <string>1</string>\n" +
                                "</dict>\n" +
                                "</plist>\n";

        File infoPlist = new File(contentsDir, "Info.plist");
        Files.write(infoPlist.toPath(), infoPlistContent.getBytes(StandardCharsets.UTF_8));
    }

    private void copyIcon(File sourceApp, File adminApp) throws IOException {
        try {
            File sourceInfoPlist = new File(sourceApp, "Contents/Info.plist");
            if (!sourceInfoPlist.exists()) {
                return;
            }

            String iconFileName = parseIconFileName(sourceInfoPlist);
            if (iconFileName == null) {
                iconFileName = "AppIcon";
            }

            if (!iconFileName.endsWith(".icns")) {
                iconFileName += ".icns";
            }

            File sourceIcon = new File(sourceApp, "Contents/Resources/" + iconFileName);
            if (!sourceIcon.exists()) {
                sourceIcon = new File(sourceApp, "Contents/Resources/AppIcon.icns");
                if (!sourceIcon.exists()) {
                    System.err.println("Icon file not found in source app: " + sourceIcon);
                    return;
                }
            }

            File destResourcesDir = new File(adminApp, "Contents/Resources");
            if (!destResourcesDir.exists()) {
                destResourcesDir.mkdirs();
            }

            File destIcon = new File(destResourcesDir, iconFileName);
            Files.copy(sourceIcon.toPath(), destIcon.toPath(),
                      StandardCopyOption.REPLACE_EXISTING);

            updateAdminAppIcon(adminApp, iconFileName);

        } catch (Exception e) {
            System.err.println("Could not copy icon from source app: " + e.getMessage());
        }
    }

    private String parseIconFileName(File infoPlist) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "/usr/libexec/PlistBuddy",
                "-c", "Print :CFBundleIconFile",
                infoPlist.getAbsolutePath()
            );

            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                process.waitFor();
                if (process.exitValue() == 0 && line != null) {
                    return line.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Could not parse icon file name from Info.plist: " + e.getMessage());
        }
        return null;
    }

    private void updateAdminAppIcon(File adminApp, String iconFileName) {
        try {
            File adminInfoPlist = new File(adminApp, "Contents/Info.plist");
            if (!adminInfoPlist.exists()) {
                return;
            }

            String iconName = iconFileName;
            if (iconName.endsWith(".icns")) {
                iconName = iconName.substring(0, iconName.length() - 5);
            }

            ProcessBuilder pb = new ProcessBuilder(
                "/usr/libexec/PlistBuddy",
                "-c", "Set :CFBundleIconFile " + iconName,
                adminInfoPlist.getAbsolutePath()
            );

            Process process = pb.start();
            process.waitFor();

            if (process.exitValue() != 0) {
                pb = new ProcessBuilder(
                    "/usr/libexec/PlistBuddy",
                    "-c", "Add :CFBundleIconFile string " + iconName,
                    adminInfoPlist.getAbsolutePath()
                );
                process = pb.start();
                process.waitFor();
            }

        } catch (Exception e) {
            System.err.println("Could not update admin app icon in Info.plist: " + e.getMessage());
        }
    }

    private void makeAppExecutable(File adminApp) {
        try {
            // Make sure all shell scripts in MacOS directory are executable
            File macosDir = new File(adminApp, "Contents/MacOS");
            if (macosDir.exists()) {
                File[] scripts = macosDir.listFiles();
                if (scripts != null) {
                    for (File script : scripts) {
                        if (script.isFile()) {
                            ProcessBuilder pb = new ProcessBuilder("chmod", "+x", script.getAbsolutePath());
                            Process process = pb.start();
                            process.waitFor();
                        }
                    }
                }
            }

            // Remove quarantine attributes that might prevent execution
            ProcessBuilder pb = new ProcessBuilder("xattr", "-d", "com.apple.quarantine", adminApp.getAbsolutePath());
            Process process = pb.start();
            process.waitFor(); // Ignore exit code - attribute might not exist

        } catch (Exception e) {
            System.err.println("Could not make admin app executable: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: AdminLauncherGenerator <path-to-app>");
            System.exit(1);
        }

        try {
            File sourceApp = new File(args[0]);
            MacAdminLauncherGenerator generator = new MacAdminLauncherGenerator();
            File adminApp = generator.generateAdminLauncher(sourceApp);
            System.out.println("Admin launcher created: " + adminApp.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}