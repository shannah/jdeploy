package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.models.InstallationSettings;
import ca.weblite.jdeploy.models.CommandSpec;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * macOS implementation of CLI command installation.
 * 
 * Handles installation of CLI commands to ~/.local/bin, creation of symlinks
 * to the CLI launcher, PATH management, and persistence of command metadata.
 */
public class MacCliCommandInstaller extends AbstractUnixCliCommandInstaller {

    @Override
    public List<File> installCommands(File launcherPath, List<CommandSpec> commands, InstallationSettings settings) {
        List<File> createdFiles = new ArrayList<>();

        if (launcherPath == null || !launcherPath.exists()) {
            System.err.println("Warning: Launcher path does not exist: " + launcherPath);
            return createdFiles;
        }

        File localBinDir = getBinDir(settings);

        // Create ~/.local/bin if it doesn't exist
        if (!ensureBinDirExists(localBinDir)) {
            return createdFiles;
        }

        boolean anyCreated = false;

        // Create per-command scripts if requested and commands are present
        if (settings.isInstallCliCommands() && commands != null && !commands.isEmpty()) {
            for (CommandSpec cs : commands) {
                String cmdName = cs.getName();
                File scriptPath = new File(localBinDir, cmdName);

                if (scriptPath.exists()) {
                    scriptPath.delete();
                }

                try {
                    writeCommandScript(scriptPath, launcherPath.getAbsolutePath(), cmdName, cs.getArgs());
                    System.out.println("Created command-line script: " + scriptPath.getAbsolutePath());
                    createdFiles.add(scriptPath);
                    anyCreated = true;
                } catch (IOException ioe) {
                    System.err.println("Warning: Failed to create command script for " + cmdName + ": " + ioe.getMessage());
                }
            }

            // Persist command metadata
            try {
                boolean pathUpdated = false;
                String path = System.getenv("PATH");
                String localBinPath = localBinDir.getAbsolutePath();
                if (path == null || !path.contains(localBinPath)) {
                    pathUpdated = addToPath(localBinDir);
                } else {
                    pathUpdated = true;
                }
                saveMetadata(localBinDir, createdFiles, pathUpdated, localBinDir);
            } catch (Exception e) {
                System.err.println("Warning: Failed to persist command metadata: " + e.getMessage());
            }
        }

        // Create traditional single symlink for primary command if requested
        if (settings.isInstallCliLauncher()) {
            String commandName = deriveCommandName(settings);
            File symlinkPath = new File(localBinDir, commandName);

            if (symlinkPath.exists()) {
                symlinkPath.delete();
            }

            try {
                Files.createSymbolicLink(symlinkPath.toPath(), launcherPath.toPath());
                System.out.println("Created command-line symlink: " + symlinkPath.getAbsolutePath());
                settings.setCommandLineSymlinkCreated(true);
                createdFiles.add(symlinkPath);
                anyCreated = true;
            } catch (Exception e) {
                System.err.println("Warning: Failed to create command-line symlink: " + e.getMessage());
            }
        }

        // Add to PATH if any files were created
        if (anyCreated) {
            String path = System.getenv("PATH");
            String localBinPath = localBinDir.getAbsolutePath();
            if (path == null || !path.contains(localBinPath)) {
                boolean pathUpdated = addToPath(localBinDir);
                settings.setAddedToPath(pathUpdated);
            } else {
                settings.setAddedToPath(true);
            }
        }

        return createdFiles;
    }

    @Override
    protected void writeCommandScript(File scriptPath, String launcherPath, String commandName, List<String> args) throws IOException {
        StringBuilder script = new StringBuilder();
        script.append("#!/bin/bash\n");
        script.append("\"").append(escapeDoubleQuotes(launcherPath)).append("\" ");
        script.append(escapeDoubleQuotes(commandName));
        
        if (args != null && !args.isEmpty()) {
            for (String arg : args) {
                script.append(" \"").append(escapeDoubleQuotes(arg)).append("\"");
            }
        }
        
        script.append(" \"$@\"\n");

        try (FileOutputStream fos = new FileOutputStream(scriptPath)) {
            fos.write(script.toString().getBytes(StandardCharsets.UTF_8));
        }

        scriptPath.setExecutable(true, false);
    }

    /**
     * Escapes double quotes in a string for shell script usage.
     * 
     * @param s the string to escape
     * @return the escaped string
     */
    private static String escapeDoubleQuotes(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\"", "\\\"");
    }
}
