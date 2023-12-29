package ca.weblite.jdeploy.openai.functions;

import ca.weblite.jdeploy.openai.dto.GetProjectRequest;
import ca.weblite.jdeploy.openai.dto.GetProjectResponse;
import ca.weblite.jdeploy.openai.services.ProjectPathResolver;
import ca.weblite.jdeploy.project.model.ProjectDescriptor;
import ca.weblite.jdeploy.project.service.ProjectLoader;
import com.theokanning.openai.completion.chat.ChatFunction;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetProjectFunction {
    private final ProjectLoader projectLoader;

    private final ProjectPathResolver projectPathResolver;

    @Inject
    public GetProjectFunction(ProjectLoader projectLoader, ProjectPathResolver projectPathResolver) {
        this.projectLoader = projectLoader;
        this.projectPathResolver = projectPathResolver;
    }

    public ChatFunction toChatFunction() {
        return ChatFunction
                .builder()
                .name("get_project")
                .description("Gets a project by name")
                .executor(GetProjectRequest.class, this::getProject)
                .build();
    }

    private GetProjectResponse getProject(GetProjectRequest request) {
        GetProjectResponse response = new GetProjectResponse();
        try {
            ProjectDescriptor projectDescriptor = projectLoader.loadProject(
                    projectPathResolver.getProjectPathByName(request.projectName).toString()
            );
            response.mainClassName = projectDescriptor.getMainClassName();
            response.packageName = projectDescriptor.getMainPackage();
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            response.error = "Failed to load project: " + e.getMessage();
        }
        return response;
    }
}
