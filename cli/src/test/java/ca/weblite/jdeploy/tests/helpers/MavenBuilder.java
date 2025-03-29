package ca.weblite.jdeploy.tests.helpers;

import ca.weblite.jdeploy.services.PlatformService;
import org.apache.maven.shared.invoker.*;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;
import java.util.Collections;

@Singleton
public class MavenBuilder {

    private PlatformService platformService;

    @Inject
    public MavenBuilder(PlatformService platformService) {
        this.platformService = platformService;
    }

    public void buildMavenProject(String projectDirectory) {
        // Create Invoker instance
        Invoker invoker = new DefaultInvoker();

        // Check if Maven Wrapper is present
        File mvnwUnixWrapper = new File(projectDirectory, "mvnw");
        File mvnwWindowsWrapper = new File(projectDirectory, "mvnw.cmd");
        if (mvnwUnixWrapper.exists() || mvnwWindowsWrapper.exists()) {
            // Use the Maven Wrapper
            invoker.setMavenHome(new File(projectDirectory));
            if (platformService.isWindows() && mvnwWindowsWrapper.exists()) {
                mvnwWindowsWrapper.setExecutable(true, false);
                invoker.setMavenExecutable(mvnwWindowsWrapper);
            } else if (!platformService.isWindows() && mvnwUnixWrapper.exists()) {
                mvnwUnixWrapper.setExecutable(true, false);
                invoker.setMavenExecutable(mvnwUnixWrapper);
            } else {
                // If both wrappers exist, prefer the Unix one
                mvnwUnixWrapper.setExecutable(true, false);
                mvnwWindowsWrapper.setExecutable(true, false);
                invoker.setMavenExecutable(mvnwUnixWrapper.exists() ? mvnwUnixWrapper : mvnwWindowsWrapper);
            }
        } else {
            // Optional: Set path to system Maven, if different from the default
            // invoker.setMavenHome(new File("/path/to/maven"));
        }

        // Create a request to execute the 'install' goal (or any other goal as needed)
        InvocationRequest request = new DefaultInvocationRequest();
        request.setJavaHome(new File(System.getProperty("java.home")));
        request.setBatchMode(true);
        request.setPomFile(new File(projectDirectory, "pom.xml"));
        request.setGoals(Collections.singletonList("package"));

        try {
            // Execute the Maven invocation
            InvocationResult result = invoker.execute(request);

            // Check for build success
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Build failed!");
            }
        } catch (MavenInvocationException e) {
            e.printStackTrace();
            // Handle exceptions appropriately
        }
    }
}
