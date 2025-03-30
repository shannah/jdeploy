package ca.weblite.jdeploy.ideInterop;

import ca.weblite.jdeploy.ideInterop.eclipse.EclipseIdeInterop;
import ca.weblite.jdeploy.ideInterop.intellij.IntelliJIdeInterop;
import ca.weblite.jdeploy.ideInterop.netbeans.NetBeansIdeInterop;
import ca.weblite.jdeploy.ideInterop.vscode.VscodeInterop;

import javax.inject.Singleton;
import java.io.File;

@Singleton
public class IdeInteropFactory {
    public IdeInteropInterface createIdeInterop(File path) {
        if (path.getName().contains("IntelliJ IDEA")) {
            return new IntelliJIdeInterop(path);
        } else if (path.getName().contains("Eclipse")) {
            return new EclipseIdeInterop(path);
        } else if (path.getName().contains("NetBeans")) {
            return new NetBeansIdeInterop(path);
        } else if (path.getName().contains("Visual Studio Code")) {
            return new VscodeInterop(path);
        } else {
            return null;
        }
    }
}
