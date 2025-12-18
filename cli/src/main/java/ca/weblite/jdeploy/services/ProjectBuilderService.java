package ca.weblite.jdeploy.services;

import ca.weblite.jdeploy.jvmdownloader.JVMKit;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.tools.platform.Platform;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class ProjectBuilderService {
    private final SystemJdkFinder systemJdkFinder;

    @Inject
    public ProjectBuilderService(SystemJdkFinder systemJdkFinder) {
        this.systemJdkFinder = systemJdkFinder;
    }

    public void buildProject(PackagingContext context, File buildLog) throws IOException {
        if (!isBuildSupported(context)) {
            throw new UnsupportedOperationException("Build command not supported for this project.");
        }

        // Execute the build command
        List<String> buildCommand = context.getProjectBuildCommand();

        if (Platform.getSystemPlatform().isWindows()) {
            buildCommand = appendWindowsSuffixes(context, buildCommand);
        }
        if (
                buildCommand.size() > 0
                        && (
                                buildCommand.get(0).contains("gradlew")
                                        || buildCommand.get(0).contains("mvnw")
                )
                && new File(context.directory, buildCommand.get(0)).exists()
        ) {
            // If the first command is "gradle", we need to add "build" to the command
            new File(context.directory, buildCommand.get(0)).setExecutable(true, false);
        }

        // Assuming the build command is a list of strings
        // representing the command and its arguments
        ProcessBuilder processBuilder = new ProcessBuilder(buildCommand);
        processBuilder.directory(context.directory);

        File javaHome = findJavaHome(""+context.getJavaVersion(17));
        if (javaHome == null) {
            for (int i = 30; i >= 8; i--) {
                javaHome = findJavaHome(""+i);
                if (javaHome != null) {
                    break;
                }
            }
        }

        if (javaHome != null) {
            System.out.println("Using JAVA_HOME=" + javaHome.getAbsolutePath());
            processBuilder.environment().put("JAVA_HOME", javaHome.getAbsolutePath());
        } else {
            System.out.println("JAVA_HOME not found");
        }


        PrintStream outputStream = context.out;
        PrintStream errorStream = context.err;

        PrintStream fileOutputStream = buildLog != null
            ? new PrintStream(new FileOutputStream(buildLog))
            : null;

        // set output to the outputStream
        processBuilder.redirectOutput(ProcessBuilder.Redirect.PIPE);
        processBuilder.redirectError(ProcessBuilder.Redirect.PIPE);



        try {
            Process process = processBuilder.start();

            // Read the output and error streams
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        outputStream.println(line);
                        if (fileOutputStream != null) {
                            fileOutputStream.println(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorStream.println(line);
                        if (fileOutputStream != null) {
                            fileOutputStream.println(line);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Build failed with exit code: " + exitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error executing build command", e);
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
    }

    public boolean isBuildSupported(PackagingContext context) {
        return context.getProjectBuildCommand() != null;
    }

    private List<String> appendWindowsSuffixes(PackagingContext context, List<String> buildCommand) {
        List<String> out = new ArrayList<>();
        for (String command : buildCommand) {
            out.add(applyWindowsSuffix(context, command));
        }

        return out;
    }

    private String applyWindowsSuffix(PackagingContext context, String command) {
        if (command.endsWith("/gradlew") || command.endsWith("mvnw")) {
            command = command.replace("./gradlew", ".\\gradlew");
            command = command.replace("./mvnw", ".\\mvnw");
            command = command.replace("/", "\\");
            String batCommand = command + ".bat";
            String cmdCommand = command + ".cmd";
            if (new File(context.directory, cmdCommand).exists()) {
                return new File(context.directory, cmdCommand).getAbsolutePath();
            } else if (new File(context.directory, batCommand).exists()) {
                return new File(context.directory, batCommand).getAbsolutePath();
            } else {
                return command;
            }
        } else if (command.endsWith(".sh")) {
            String batCommand = command.replace(".sh", ".bat");
            String cmdCommand = command.replace(".sh", ".cmd");
            if (new File(context.directory, batCommand).exists()) {
                return new File(context.directory, batCommand).getAbsolutePath();
            } else if (new File(context.directory, cmdCommand).exists()) {
                return new File(context.directory, cmdCommand).getAbsolutePath();
            } else {
                return command;
            }
        }

        return command;
    }

    private File findJavaHome(String version) {
        return systemJdkFinder.findJavaHome(version);
    }

}
