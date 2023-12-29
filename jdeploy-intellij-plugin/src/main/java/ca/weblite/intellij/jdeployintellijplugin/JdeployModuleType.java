package ca.weblite.intellij.jdeployintellijplugin;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.module.ModuleTypeManager;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class JdeployModuleType extends ModuleType<JdeployModuleBuilder> {

    public static final String ID = "JDEPLOY_MODULE_TYPE";
    JdeployModuleType() {
        super(ID);
    }

    public static JdeployModuleType getInstance() {
        return (JdeployModuleType) ModuleTypeManager.getInstance().findByID(ID);
    }

    @Override
    public @NotNull JdeployModuleBuilder createModuleBuilder() {
        return new JdeployModuleBuilder();
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getName() {
        return "jDeploy";
    }

    @Override
    public @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getDescription() {
        return "jDeploy project";
    }

    @Override
    public @NotNull Icon getNodeIcon(boolean isOpened) {
        return AllIcons.Nodes.Module;
    }
}
