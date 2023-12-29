package ca.weblite.intellij.jdeployintellijplugin;

import ca.weblite.jdeploy.gui.JDeployProjectEditorContext;
import ca.weblite.jdeploy.interop.DesktopInterop;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URI;

public class IntellijJdeployProjectEditorContext extends JDeployProjectEditorContext {

    private final Project project;

    private final IntellijDesktopInterop desktopInterop = new IntellijDesktopInterop();

    public IntellijJdeployProjectEditorContext(Project project) {
        this.project = project;
    }
    @Override
    public boolean shouldDisplayExitMenu() {
        return false;
    }

    @Override
    public boolean shouldShowPublishButton() {
        return false;
    }

    @Override
    public boolean shouldDisplayApplyButton() {
        return true;
    }

    @Override
    public boolean shouldDisplayCancelButton() {
        return true;
    }

    @Override
    public DesktopInterop getDesktopInterop() {
        return desktopInterop;
    }

    private class IntellijDesktopInterop extends DesktopInterop {
        @Override
        public void edit(File file) throws Exception {
            ApplicationManager.getApplication().invokeLater(() -> {
                VirtualFile fileToOpen = LocalFileSystem.getInstance().findFileByPath(file.getAbsolutePath());
                if (fileToOpen != null) {
                    // Open the file in the editor
                    FileEditorManager.getInstance(project).openFile(fileToOpen, true);
                }
            });

        }

        @Override
        public void browse(URI url) throws Exception {
            BrowserUtil.browse(url);
        }
    }

    @Override
    public Component getParentFrame() {
        return WindowManager.getInstance().getIdeFrame(project).getComponent();
    }

    @Override
    public void onFileUpdated(File file) {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(file.getAbsolutePath());
        if (virtualFile != null) {
            ApplicationManager.getApplication().runWriteAction(() -> {
                virtualFile.refresh(false, false);
            });
        }
    }
}
