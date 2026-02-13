package ca.weblite.jdeploy.cli.di;

import ca.weblite.jdeploy.cli.util.CliChatThreadDispatcher;
import ca.weblite.jdeploy.cli.util.CliUiThreadDispatcher;
import ca.weblite.jdeploy.openai.interop.ChatThreadDispatcher;
import ca.weblite.jdeploy.openai.interop.UiThreadDispatcher;
import ca.weblite.jdeploy.project.service.DefaultProjectLoader;
import ca.weblite.jdeploy.project.service.ProjectLoader;
import ca.weblite.jdeploy.publishTargets.PublishTargetService;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.services.PrebuiltAppRequirementService;
import ca.weblite.jdeploy.services.PrebuiltAppRequirementServiceImpl;
import org.codejargon.feather.Provides;

public class JDeployCliModule {

    @Provides
    public UiThreadDispatcher uiThreadDispatcher(CliUiThreadDispatcher impl) {
        return impl;
    }

    @Provides
    public ChatThreadDispatcher promptHandler(CliChatThreadDispatcher impl) {
        return impl;
    }

    @Provides
    public ProjectLoader projectLoader(DefaultProjectLoader impl) {
        return impl;
    }

    @Provides
    public PublishTargetServiceInterface publishTargetServiceInterface(PublishTargetService impl) {
        return impl;
    }

    @Provides
    public PrebuiltAppRequirementService prebuiltAppRequirementService(PrebuiltAppRequirementServiceImpl impl) {
        return impl;
    }

}
