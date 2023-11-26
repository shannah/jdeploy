package ca.weblite.jdeploy.openai.functions;

import ca.weblite.jdeploy.openai.dto.GetClassSourceRequest;
import ca.weblite.jdeploy.openai.dto.GetClassSourceResponse;
import ca.weblite.jdeploy.openai.services.ProjectPathResolver;
import ca.weblite.jdeploy.project.service.ClassReader;
import ca.weblite.jdeploy.project.service.ProjectLoader;
import com.theokanning.openai.completion.chat.ChatFunction;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetClassSourceFunction {

    private final ProjectLoader projectLoader;

    private final ProjectPathResolver projectPathResolver;

    private final ClassReader classReader;

    @Inject
    public GetClassSourceFunction(
            ProjectLoader projectLoader,
            ProjectPathResolver projectPathResolver,
            ClassReader classReader
    ) {
        this.projectLoader = projectLoader;
        this.projectPathResolver = projectPathResolver;
        this.classReader = classReader;
    }

    public ChatFunction toChatFunction() {
        return ChatFunction
                .builder()
                .name("get_class_source")
                .description("Gets the Java source code for a class")
                .executor(GetClassSourceRequest.class, this::getClassSource)
                .build();
    }

    private GetClassSourceResponse getClassSource(GetClassSourceRequest request) {
        GetClassSourceResponse response = new GetClassSourceResponse();
        try {
            response.source = classReader.readClass(
                    projectLoader.loadProject(
                            projectPathResolver.getProjectPathByName(request.projectName).toString()
                    ),
                    request.className,
                    request.packageName
            );

            return response;
        } catch (Exception e) {
            e.printStackTrace();
            response.error = "Failed to load class source: " + e.getMessage();
        }
        return response;
    }


}
