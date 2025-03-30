package ca.weblite.jdeploy.ideInterop;

import javax.swing.*;
import java.io.File;

public interface IdeInteropInterface {
    void openProject(String projectPath);
    Icon getIcon();
    String getName();
    File getPath();
}
