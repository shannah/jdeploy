package ca.weblite.intellij.jdeployintellijplugin;

import ca.weblite.jdeploy.gui.JDeployProjectEditor;
import ca.weblite.tools.io.FileUtil;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class JDeploySettingsAction extends AnAction {

    private Map<Project, JDeployProjectEditor> editors = new HashMap<>();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (e.getProject() == null) return;
        JSONObject packageJSON = getPackageJSON(e);
        if (packageJSON == null) return;
        EventQueue.invokeLater(() -> {
            JDeployProjectEditor editor = getEditor(e);
            if (!editor.focus()) {
                editor.show();
            }
        });

    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && getPackageJSON(e) != null);
    }

    private File getPackageJSONFile(AnActionEvent e) {
        if (e.getProject() == null) return null;
        File packageJSONFile = new File(e.getProject().getBasePath() + File.separator + "package.json");
        if (!packageJSONFile.exists()) {
            return null;
        }
        return packageJSONFile;
    }

    private JSONObject getPackageJSON(AnActionEvent e) {
        File packageJSONFile = getPackageJSONFile(e);
        if (packageJSONFile == null) {
            return null;
        }
        try {
            JSONObject packageJSON = new JSONObject(FileUtil.readFileToString(packageJSONFile));
            if (!packageJSON.has("jdeploy")) {
                return null;
            }
            return packageJSON;
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private JDeployProjectEditor getEditor(AnActionEvent e) {
        if (e.getProject() == null) return null;
        JDeployProjectEditor editor = editors.get(e.getProject());
        if (editor != null && editor.isShowing()) {
            return editor;
        }

        editors.put(e.getProject(), new JDeployProjectEditor(
                getPackageJSONFile(e),
                getPackageJSON(e),
                new IntellijJdeployProjectEditorContext(e.getProject())
        ));

        return editors.get(e.getProject());
    }
}
