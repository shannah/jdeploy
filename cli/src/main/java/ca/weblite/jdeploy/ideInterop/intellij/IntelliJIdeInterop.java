package ca.weblite.jdeploy.ideInterop.intellij;

import ca.weblite.jdeploy.ideInterop.AbstractIdeInterop;
import ca.weblite.tools.platform.Platform;

import java.io.File;


public class IntelliJIdeInterop extends AbstractIdeInterop {

    public IntelliJIdeInterop(File path) {
        super(path);
    }

    @Override
    public String getName() {
        if (Platform.getSystemPlatform().isWindows()) {
            return getPath().getParentFile().getParentFile().getName();
        }
        return super.getName();
    }
}
