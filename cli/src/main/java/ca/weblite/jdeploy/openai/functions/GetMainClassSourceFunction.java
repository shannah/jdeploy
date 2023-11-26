package ca.weblite.jdeploy.openai.functions;

import ca.weblite.jdeploy.openai.config.OpenAiChatConfig;
import ca.weblite.jdeploy.openai.dto.GetMainClassSourceRequest;
import ca.weblite.jdeploy.openai.dto.GetMainClassSourceResponse;
import com.theokanning.openai.completion.chat.ChatFunction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

@Singleton
public class GetMainClassSourceFunction {

    private final OpenAiChatConfig config;

    @Inject
    public GetMainClassSourceFunction(OpenAiChatConfig config) {
        this.config = config;
    }

    public GetMainClassSourceResponse getMainClassSource(GetMainClassSourceRequest request) {
        GetMainClassSourceResponse response = new GetMainClassSourceResponse();
        File mainClass = getMainClassFile(new File(config.getBaseProjectsPath(), request.appName), request);
        if (!mainClass.exists()) {
            mainClass = getMainClassFile(new File(config.getBaseProjectsPath() + File.separator + request.appName, "common"), request);
        }

        if (!mainClass.exists()) {
            response.error = "Main class file could not be found";
        }

        try {
            response.source = new String(java.nio.file.Files.readAllBytes(mainClass.toPath()));
        } catch (Exception e) {
            e.printStackTrace();
            response.error = "Failed to read main class file: " + e.getMessage();
        }

        return response;

    }

    public ChatFunction asChatFunction() {
        return ChatFunction
                .builder()
                .name("get_main_class_source")
                .description("Gets the Java source code of the main class for the project")
                .executor(GetMainClassSourceRequest.class, this::getMainClassSource)
                .build();

    }

    private File getMainClassFile(File projectBase, GetMainClassSourceRequest request) {
        return new File(projectBase, "src" + File.separator + "main" + File.separator + "java" + File.separator + request.packageName.replace(".", File.separator) + File.separator + request.mainClassName + ".java");
    }
}
