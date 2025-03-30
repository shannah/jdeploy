package ca.weblite.jdeploy.ideInterop;

import ca.weblite.tools.platform.Platform;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.io.File;

public class AbstractIdeInterop implements IdeInteropInterface{
    private final File path;

    public AbstractIdeInterop(File path) {
        this.path = path;
    }

    @Override
    public void openProject(String projectPath) {
        if (Platform.getSystemPlatform().isMac()) {
            // On macOS, we can use the "open" command to open the IDE
            try {
                ProcessBuilder processBuilder = new ProcessBuilder("open", "-a", path.getAbsolutePath(), projectPath);
                processBuilder.inheritIO();
                processBuilder.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        // This can be overridden in subclasses for specific IDE behavior.
        try {
            // Assuming the IDE can be opened with the project path as an argument
            ProcessBuilder processBuilder = new ProcessBuilder(path.getAbsolutePath(), projectPath);
            processBuilder.inheritIO();
            processBuilder.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public Icon getIcon() {
        // Default implementation returns null
        return FileSystemView.getFileSystemView().getSystemIcon(path);
    }

    @Override
    public String getName() {
        return path.getName();
    }

    @Override
    public File getPath() {
        return path;
    }
}
