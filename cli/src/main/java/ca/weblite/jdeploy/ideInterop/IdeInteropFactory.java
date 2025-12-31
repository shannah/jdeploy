package ca.weblite.jdeploy.ideInterop;

import ca.weblite.jdeploy.ideInterop.brokk.BrokkInterop;
import ca.weblite.jdeploy.ideInterop.eclipse.EclipseIdeInterop;
import ca.weblite.jdeploy.ideInterop.intellij.IntelliJIdeInterop;
import ca.weblite.jdeploy.ideInterop.netbeans.NetBeansIdeInterop;
import ca.weblite.jdeploy.ideInterop.vscode.VscodeInterop;

import javax.inject.Singleton;
import java.io.File;

@Singleton
public class IdeInteropFactory {
    public IdeInteropInterface createIdeInterop(File path) {
        if (path.getAbsolutePath().contains("IntelliJ IDEA")) {
            return new IntelliJIdeInterop(path);
        } else if (path.getAbsolutePath().contains("Eclipse")) {
            return new EclipseIdeInterop(path);
        } else if (path.getAbsolutePath().contains("NetBeans")) {
            return new NetBeansIdeInterop(path);
        } else if (path.getName().contains("Code") && (path.getAbsolutePath().contains("VS") || path.getAbsolutePath().contains("Visual Studio"))) {
            return new VscodeInterop(path);
        } else if (path.getAbsolutePath().contains("Brokk") || path.getAbsolutePath().contains("brokk")) {
            return new BrokkInterop(path);
        } else {
            return null;
        }
    }
}
