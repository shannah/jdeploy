package ca.weblite.jdeploy.openai.functions;

import ca.weblite.jdeploy.builders.ProjectGeneratorRequestBuilder;
import ca.weblite.jdeploy.openai.dtos.GenerateProjectRequest;
import ca.weblite.jdeploy.openai.dtos.GenerateProjectResponse;
import ca.weblite.jdeploy.services.ProjectGenerator;
import com.theokanning.openai.completion.chat.ChatFunction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

@Singleton
public class GenerateProjectFunction {

    private final ProjectGenerator projectGenerator;

    @Inject()
    public GenerateProjectFunction(ProjectGenerator projectGenerator) {
        this.projectGenerator = projectGenerator;
    }

    public GenerateProjectResponse generateProject(GenerateProjectRequest request) {
        ProjectGeneratorRequestBuilder domainRequestBuilder = new ProjectGeneratorRequestBuilder();
        domainRequestBuilder
                .setMagicArg(request.githubRepository)
                .setTemplateName(request.template);

        GenerateProjectResponse response = new GenerateProjectResponse();

        try {
            File path = projectGenerator.generate(domainRequestBuilder.build());
            response.path = path.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            response.error = "Failed to generate project: " + e.getMessage();
        }

        return response;
    }

    public ChatFunction asChatFunction() {
        return ChatFunction
                .builder()
                .name("generate_project")
                .description("Generates a project from a template")
                .executor(GenerateProjectRequest.class, this::generateProject)
                .build();

    }
}
