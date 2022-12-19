package ca.weblite.jdeploy.maven;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class JDeployClient {
    private final String version;

    public JDeployClient(String version) {
        this.version = version;
    }

    public void execJdeploy(String... args) throws IOException {
        wait(jdeployProcessBuilder(args).start());
    }

    public void execNpm(String... args) throws IOException {
        wait(npmProcessBuilder(args).start());
    }

    private void wait(Process process) throws IOException {
        try {
            if (process.waitFor() == 0) {
                return;
            }
            throw new IOException("Process returned non-zero exit code");
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private ProcessBuilder jdeployProcessBuilder(String... args) {
        final ProcessBuilder processBuilder = new ProcessBuilder(getNpxPath(), "--yes", "jdeploy@"+getJdeployVersion());
        processBuilder.command().addAll(Arrays.asList(args));
        processBuilder.inheritIO();

        return processBuilder;
    }

    private ProcessBuilder npmProcessBuilder(String... args) {
        final ProcessBuilder processBuilder = new ProcessBuilder(getNpmPath());
        processBuilder.command().addAll(Arrays.asList(args));
        processBuilder.inheritIO();

        return processBuilder;
    }

    private String getJdeployVersion() {
        return version == null ? "latest" : version;
    }

    private String getNpmPath() {
        return isWindows() ? "npm.cmd" : "npm";
    }

    private String getNpxPath() {
        return isWindows() ? "npx.cmd" : "npx";
    }

    private boolean isWindows() {
        return "\\".equals(File.separator);
    }
}
