package ca.weblite.jdeploy.openai.functions;

import ca.weblite.jdeploy.openai.dto.PutClassSourceRequest;
import ca.weblite.jdeploy.openai.dto.PutClassSourceResponse;
import ca.weblite.jdeploy.openai.services.ProjectPathResolver;
import ca.weblite.jdeploy.project.service.ClassWriter;
import ca.weblite.jdeploy.project.service.ProjectLoader;
import com.theokanning.openai.completion.chat.ChatFunction;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PutClassSourceFunction {
    private final ProjectPathResolver projectPathResolver;

    private final ProjectLoader projectLoader;

    private final ClassWriter classWriter;

    @Inject
    public PutClassSourceFunction(
            ProjectPathResolver projectPathResolver,
            ProjectLoader projectLoader,
            ClassWriter classWriter
    ) {
        this.projectPathResolver = projectPathResolver;
        this.projectLoader = projectLoader;
        this.classWriter = classWriter;
    }

    private PutClassSourceResponse putClassSource(PutClassSourceRequest request) {
        PutClassSourceResponse response = new PutClassSourceResponse();
        try {
            classWriter.writeClass(
                    projectLoader.loadProject(
                            projectPathResolver.getProjectPathByName(request.projectName).toString()
                    ),
                    request.className,
                    request.packageName,
                    request.source
            );
            response.success = true;
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            response.error = "Failed to write class source: " + e.getMessage();
        }
        return response;
    }

    public ChatFunction toChatFunction() {
        return ChatFunction
                .builder()
                .name("put_class_source")
                .description("Writes the Java source code for a class")
                .executor(PutClassSourceRequest.class, this::putClassSource)
                .build();
    }
}
