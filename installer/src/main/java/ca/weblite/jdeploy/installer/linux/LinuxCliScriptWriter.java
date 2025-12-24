package ca.weblite.jdeploy.installer.linux;

import ca.weblite.jdeploy.installer.CliInstallerConstants;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Helper to generate simple POSIX shell wrapper scripts that execute the bundled launcher
 * with a --jdeploy:command=<name> argument and forward user arguments.
 */
public class LinuxCliScriptWriter {

    /**
     * Generate the content of a POSIX shell script that exec's the given launcher with
     * the configured command name and forwards user-supplied args.
     *
     * @param launcherPath Absolute path to the CLI-capable launcher binary.
     * @param commandName  The command name to pass as --jdeploy:command=<name>.
     * @return Script content (including shebang and trailing newline).
     */
    public static String generateContent(String launcherPath, String commandName) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("exec \"").append(escapeDoubleQuotes(launcherPath)).append("\" ").append(CliInstallerConstants.JDEPLOY_COMMAND_ARG_PREFIX).append(commandName).append(" -- \"$@\"\n");
        return sb.toString();
    }

    /**
     * Write the script to disk and make it executable.
     *
     * @param dest         Destination file path for the script.
     * @param launcherPath Absolute path to the launcher to invoke.
     * @param commandName  The configured command name to pass through.
     * @throws IOException if writing fails.
     */
    public static void writeExecutableScript(File dest, String launcherPath, String commandName) throws IOException {
        String content = generateContent(launcherPath, commandName);
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        }
        // make executable for owner/group/other (non-strict)
        dest.setExecutable(true, false);
    }

    private static String escapeDoubleQuotes(String s) {
        if (s == null) return "";
        // Order matters: escape backslashes first to avoid double-escaping
        // Then escape other special chars that have meaning inside double quotes
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("`", "\\`")
                .replace("$", "\\$");
    }
}
