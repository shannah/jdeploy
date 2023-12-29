package ca.weblite.intellij.jdeployintellijplugin;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;

import javax.swing.*;

public class JdeployModuleWizardStep extends ModuleWizardStep {

    private final JdeployModuleBuilder builder;

    private final JdeployProjectWizardForm form = new JdeployProjectWizardForm();

    JdeployModuleWizardStep(JdeployModuleBuilder builder) {
        this.builder = builder;

    }

    @Override
    public JComponent getComponent() {
        return form.getRoot();
    }



    @Override
    public void updateDataModel() {
        builder.setJdeployProjectType(form.getProjectTemplate().getSelectedItem().toString());
        builder.setGithubToken(form.getGithubToken().getText());
        builder.setGithubUser(form.getGithubUser().getText());
        builder.setPrivateRepository(form.getPrivateRepository().isSelected());

    }

    @Override
    public void updateStep() {
        super.updateStep();
        form.getGithubToken().setText(builder.getGithubToken());
        form.getGithubUser().setText(builder.getGithubUser());
    }
}
