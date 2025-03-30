package ca.weblite.jdeploy.ideInterop.vscode;

import ca.weblite.jdeploy.ideInterop.AbstractIdeInterop;
import ca.weblite.tools.platform.Platform;

import java.io.File;

public class VscodeInterop extends AbstractIdeInterop {
    public VscodeInterop(File path) {
        super(path);
    }

    @Override
    public String getName() {
        if (Platform.getSystemPlatform().isWindows()) {
            return getPath().getParentFile().getName();
        }
        return super.getName();
    }
}
