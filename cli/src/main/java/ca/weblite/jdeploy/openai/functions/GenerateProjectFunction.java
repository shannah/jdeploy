package ca.weblite.jdeploy.openai.functions;

import ca.weblite.jdeploy.builders.ProjectGeneratorRequestBuilder;
import ca.weblite.jdeploy.openai.config.OpenAiChatConfig;
import ca.weblite.jdeploy.openai.dto.GenerateProjectRequest;
import ca.weblite.jdeploy.openai.dto.GenerateProjectResponse;
import ca.weblite.jdeploy.services.ProjectGenerator;
import com.theokanning.openai.completion.chat.ChatFunction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

@Singleton
public class GenerateProjectFunction {

    private final ProjectGenerator projectGenerator;

    private final OpenAiChatConfig config;

    @Inject()
    public GenerateProjectFunction(ProjectGenerator projectGenerator, OpenAiChatConfig config) {
        this.projectGenerator = projectGenerator;
        this.config = config;
    }

    public GenerateProjectResponse generateProject(GenerateProjectRequest request) {
        ProjectGeneratorRequestBuilder domainRequestBuilder = new ProjectGeneratorRequestBuilder();
        File baseProjectsPath = new File(config.getBaseProjectsPath());
        if (!baseProjectsPath.exists()) {
            baseProjectsPath.mkdirs();
        }
        domainRequestBuilder
                .setParentDirectory(baseProjectsPath)
                .setMagicArg(request.githubRepository)
                .setTemplateName(request.template);

        GenerateProjectResponse response = new GenerateProjectResponse();

        try {
            File path = projectGenerator.generate(domainRequestBuilder.build());
            response.path = path.getAbsolutePath();
            response.appName = domainRequestBuilder.getProjectName();
            response.mainClassName = domainRequestBuilder.getMainClassName();
            response.packageName = domainRequestBuilder.getPackageName();
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
