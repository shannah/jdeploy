package ca.weblite.jdeploy.appbundler.mac;

import ca.weblite.jdeploy.appbundler.Bundler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DmgCreator {

    public static void createDmg(String appPath, String dmgPath) throws IOException, InterruptedException {
        File appFile = new File(appPath);
        if (!appFile.exists() || !appFile.isDirectory()) {
            throw new IllegalArgumentException("The provided app path is not valid.");
        }

        String appName = appFile.getName();
        String volumeName = appName.replaceAll("\\.app$", "") + " Installer";
        String dmgName = appName.replaceAll("\\.app$", "") + ".dmg";
        File dmgFile;
        if (dmgPath != null) {
            dmgFile = new File(dmgPath);
            dmgName = dmgFile.getName();
        } else {
            dmgFile = new File(appFile.getParentFile(), dmgName);
        }
        String tempDir = Files.createTempDirectory("dmg").toString();
        String backgroundImg = "background.tiff";

        // Create temporary directory structure
        Files.createDirectories(Paths.get(tempDir, ".background"));

        // Copy the app to the temporary directory
        runCommand("cp", "-R", appPath, tempDir);

        // Create alias for /Applications
        runCommand("ln", "-s", "/Applications", Paths.get(tempDir, "Applications").toString());

        copyResourceToDirectory(
                "/com/joshondesign/appbundler/mac/dmg/background.tiff",
                Paths.get(tempDir, ".background").toString()
        );

        // Create the DMG file
        runCommand("hdiutil", "create", "-volname", volumeName, "-srcfolder", tempDir, "-ov", "-format", "UDRW", dmgFile.getPath());

        // Mount the DMG file
        String mountOutput = runCommand("hdiutil", "attach", dmgFile.getPath(), "-readwrite", "-noverify", "-noautoopen", "-verbose");
        // Extract mount point
        String mountDir = null;
        try (BufferedReader reader = new BufferedReader(new StringReader(mountOutput))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Reading: " + line);
                if (line.contains("/Volumes")) {
                    String[] tokens = line.trim().split("\\s+");
                    if (tokens.length >= 3) {
                        // Combine all tokens from the third one onward to account for spaces in the path
                        mountDir = String.join(" ", Arrays.copyOfRange(tokens, 2, tokens.length));
                        break;
                    }
                }
            }
        }

        if (mountDir == null) {
            throw new IOException("Failed to mount DMG");
        }
        try {
            System.out.println("Mount dir: " + mountDir);
            // Set up the DMG window with AppleScript
            String appleScript = String.format(
                    "tell application \"Finder\"\n" +
                            "    tell disk \"%s\"\n" +
                            "        log \"Opening disk\"\n" +
                            "        open\n" +
                            "        log \"Setting view to icon view\"\n" +
                            "        set current view of container window to icon view\n" +
                            "        log \"Hiding toolbar\"\n" +
                            "        set toolbar visible of container window to false\n" +
                            "        log \"Hiding statusbar\"\n" +
                            "        set statusbar visible of container window to false\n" +
                            "        log \"Setting window bounds\"\n" +
                            "        set the bounds of container window to {400, 100, 900, 450}\n" +
                            "        log \"Setting view options\"\n" +
                            "        set viewOptions to the icon view options of container window\n" +
                            "        log \"Setting arrangement to not arranged\"\n" +
                            "        set arrangement of viewOptions to not arranged\n" +
                            "        log \"Setting icon size\"\n" +
                            "        set icon size of viewOptions to 100\n" +
                            "        log \"Setting background picture\"\n" +
                            "        set background picture of viewOptions to file \".background:%s\"\n" +
                            "        log \"Setting position of the app icon\"\n" +
                            "        set position of item \"%s\" of container window to {100, 150}\n" +
                            "        log \"Setting position of Applications icon\"\n" +
                            "        set position of item \"Applications\" of container window to {400, 150}\n" +
                            "        log \"Updating window\"\n" +
                            "        update without registering applications\n" +
                            "        log \"Delaying for 5 seconds\"\n" +
                            "        delay 5\n" +
                            "        log \"AppleScript execution completed\"\n" +
                            "    end tell\n" +
                            "end tell\n",
                    volumeName, backgroundImg, appName
            );
            runCommand("osascript", "-e", appleScript);
        } finally {
            // Unmount the DMG
            runCommand("hdiutil", "detach", mountDir);
        }

        runCommand("hdiutil", "convert", dmgFile.getPath(), "-format", "UDZO", "-imagekey", "zlib-level=9", "-o", new File(dmgFile.getParentFile(), "compressed.dmg").getPath());

        runCommand("rm", dmgFile.getPath());
        runCommand("mv", new File(dmgFile.getParentFile(), "compressed.dmg").getPath(), dmgFile.getPath());

        // Clean up
        Files.walk(Paths.get(tempDir))
                .map(Path::toFile)
                .forEach(File::delete);

    }

    private static String runCommand(String... command) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(">" + line);
                output.append(line).append(System.lineSeparator());
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Command failed with exit code " + exitCode + ": " +
                    String.join(" ", command) + "\n" + output.toString());
        }
        return output.toString();
    }

    private static void copyResourceToDirectory(String resourcePath, String destDir) throws IOException {
        Files.copy(DmgCreator.class.getResourceAsStream(resourcePath), Paths.get(destDir).resolve(resourcePath.substring(resourcePath.lastIndexOf('/') + 1)));
    }
}
