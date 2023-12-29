package ca.weblite.jdeploy.openai.functions;

import ca.weblite.jdeploy.openai.config.OpenAiChatConfig;
import ca.weblite.jdeploy.openai.dto.GetMainClassSourceRequest;
import ca.weblite.jdeploy.openai.dto.GetMainClassSourceResponse;
import ca.weblite.jdeploy.openai.dto.PutMainClassSourceRequest;
import ca.weblite.jdeploy.openai.dto.PutMainClassSourceResponse;
import com.theokanning.openai.completion.chat.ChatFunction;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.File;

@Singleton
public class PutMainClassSourceFunction {

    private final OpenAiChatConfig config;

    @Inject
    public PutMainClassSourceFunction(OpenAiChatConfig config) {
        this.config = config;
    }

    public PutMainClassSourceResponse putMainClassSource(PutMainClassSourceRequest request) {
        PutMainClassSourceResponse response = new PutMainClassSourceResponse();
        File mainClass = getMainClassFile(new File(config.getBaseProjectsPath(), request.appName), request);
        if (!mainClass.exists()) {
            mainClass = getMainClassFile(new File(config.getBaseProjectsPath() + File.separator + request.appName, "common"), request);
        }

        if (!mainClass.exists()) {
            response.error = "Main class file could not be found";
        }

        try {
            // Write request source to mainClass
            java.nio.file.Files.write(mainClass.toPath(), request.sourceCode.getBytes());
            response.source = request.sourceCode;
        } catch (Exception e) {
            e.printStackTrace();
            response.error = "Failed to read main class file: " + e.getMessage();
        }

        return response;

    }

    public ChatFunction asChatFunction() {
        return ChatFunction
                .builder()
                .name("put_main_class_source")
                .description("Updates the Java source code of the main class for the project")
                .executor(PutMainClassSourceRequest.class, this::putMainClassSource)
                .build();

    }

    private File getMainClassFile(File projectBase, PutMainClassSourceRequest request) {
        return new File(projectBase, "src" + File.separator + "main" + File.separator + "java" + File.separator + request.packageName.replace(".", File.separator) + File.separator + request.mainClassName + ".java");
    }
}
