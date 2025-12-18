package ca.weblite.jdeploy.ideInterop.netbeans;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.ideInterop.AbstractIdeInterop;
import ca.weblite.jdeploy.services.SystemJdkFinder;
import ca.weblite.tools.platform.Platform;

import java.io.File;
import java.util.Map;

public class NetBeansIdeInterop extends AbstractIdeInterop {
    public NetBeansIdeInterop(File path) {
        super(path);
    }

    @Override
    public void openProject(String projectPath) {
        try {

            File javaHome = DIContext.get(SystemJdkFinder.class).findJavaHome("30");
            if (javaHome == null) {
                for (int i = 30; i >= 8; i--) {
                    javaHome = DIContext.get(SystemJdkFinder.class).findJavaHome(""+i);
                    if (javaHome != null) {
                        break;
                    }
                }
            }

            // Assuming the IDE can be opened with the project path as an argument
            ProcessBuilder processBuilder = new ProcessBuilder(getPath().getAbsolutePath(), "--open", projectPath);
            if (javaHome != null) {
                processBuilder.environment().put("JAVA_HOME", javaHome.getAbsolutePath());
                processBuilder.command().add("--jdkhome");
                processBuilder.command().add(javaHome.getAbsolutePath());
            }
            decorateProcessEnvironment(processBuilder.environment());
            processBuilder.inheritIO();
            processBuilder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        if (Platform.getSystemPlatform().isWindows()) {
            return getPath().getParentFile().getParentFile().getName();
        }
        return super.getName();
    }
}
