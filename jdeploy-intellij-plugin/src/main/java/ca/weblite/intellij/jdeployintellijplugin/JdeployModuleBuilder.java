package ca.weblite.intellij.jdeployintellijplugin;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.builders.ProjectGeneratorRequestBuilder;
import ca.weblite.jdeploy.config.Config;
import ca.weblite.jdeploy.services.GithubTokenService;
import ca.weblite.jdeploy.services.ProjectGenerator;
import com.intellij.ide.util.projectWizard.ModuleBuilder;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class JdeployModuleBuilder extends ModuleBuilder {

    private String projectType;
    private String githubToken;

    private String githubUser;

    private boolean privateRepository;

    public JdeployModuleBuilder() {
        super();
        githubToken = DIContext.getInstance().getInstance(GithubPreferences.class).getToken();
        githubUser = DIContext.getInstance().getInstance(GithubPreferences.class).getGithubUser();
    }

    @Override
    public ModuleType<?> getModuleType() {
        return JdeployModuleType.getInstance();
    }

    @Override
    public ModuleWizardStep[] createWizardSteps(@NotNull WizardContext wizardContext, @NotNull ModulesProvider modulesProvider) {
        return super.createWizardSteps(wizardContext, modulesProvider);
    }

    @Override
    public @Nullable ModuleWizardStep getCustomOptionsStep(WizardContext context, Disposable parentDisposable) {
        return new JdeployModuleWizardStep(this);
    }

    @Override
    public void setupRootModel(@NotNull ModifiableRootModel modifiableRootModel) throws ConfigurationException {
        super.setupRootModel(modifiableRootModel);
        ProjectGeneratorRequestBuilder requestBuilder = new ProjectGeneratorRequestBuilder();
        new File(modifiableRootModel.getProject().getBasePath()).delete();
        requestBuilder.setParentDirectory(
                new File(modifiableRootModel.getProject().getBasePath()).getParentFile()
        );
        requestBuilder.setTemplateName(projectType);
        requestBuilder.setGithubRepository(githubUser + "/" + modifiableRootModel.getProject().getName());
        requestBuilder.setProjectName(modifiableRootModel.getProject().getName());
        requestBuilder.setPrivateRepository(privateRepository);

        GithubPreferences githubPreferences = DIContext.getInstance().getInstance(GithubPreferences.class);
        githubPreferences.setToken(githubToken);
        githubPreferences.setGithubUser(githubUser);

        DIContext context = DIContext.getInstance();
        Config config = context.getInstance(Config.class);
        config.getProperties().setProperty("github.token", githubToken);

        ProjectGenerator generator = context.getInstance(ProjectGenerator.class);
        try {
            generator.generate(requestBuilder.build());
        } catch (Exception e) {
            throw new ConfigurationException(e.getMessage());
        }

    }

    public String getJdeployProjectType() {
        return projectType;
    }

    public void setJdeployProjectType(String projectType) {
        this.projectType = projectType;
    }

    public String getGithubToken() {
        return githubToken;
    }

    public void setGithubToken(String githubToken) {
        this.githubToken = githubToken;
    }

    public String getGithubUser() {
        return githubUser;
    }

    public void setGithubUser(String githubUser) {
        this.githubUser = githubUser;
    }

    public void setPrivateRepository(boolean privateRepository) {
        this.privateRepository = privateRepository;
    }
}
