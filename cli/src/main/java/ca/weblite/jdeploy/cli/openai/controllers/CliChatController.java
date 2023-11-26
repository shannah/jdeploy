package ca.weblite.jdeploy.cli.openai.controllers;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.cli.config.CliConfigLoader;
import ca.weblite.jdeploy.config.ConfigLoader;
import ca.weblite.jdeploy.exception.ConfigValidationException;
import ca.weblite.jdeploy.github.config.GithubConfigValidator;
import ca.weblite.jdeploy.openai.OpenAiKit;
import ca.weblite.jdeploy.openai.chat.PromptHandler;
import ca.weblite.jdeploy.openai.model.*;
import ca.weblite.jdeploy.openai.services.OpenAiConfigValidator;


import javax.inject.Inject;
import java.util.Scanner;

public class CliChatController {

    private final PromptHandler promptHandler;

    private final OpenAiKit openAiKit;

    private ChatThread chatThread;

    private final OpenAiConfigValidator openAiConfigValidator;

    private final GithubConfigValidator githubConfigValidator;

    @Inject
    public CliChatController(
            PromptHandler promptHandler,
            OpenAiKit openAiKit,
            OpenAiConfigValidator openAiConfigValidator,
            GithubConfigValidator githubConfigValidator
    ) {
        this.promptHandler = promptHandler;
        this.openAiKit = openAiKit;
        this.openAiConfigValidator = openAiConfigValidator;
        this.githubConfigValidator = githubConfigValidator;
    }

    public void run() {
        try {
            openAiConfigValidator.validate();
            githubConfigValidator.validate();
        } catch (ConfigValidationException e) {
            System.out.println(e.getMessage());
            System.exit(1);
            return;
        }
        chatThread = openAiKit.createChatThread();
        Scanner scanner = new Scanner(System.in);
        System.out.println("Hello, I am the JDeploy chat bot. I can help you deploy your Java code to the cloud.");
        System.out.println("Type 'exit' to exit.");
        while (true) {
            System.out.print("> ");
            String nextLine = scanner.nextLine();
            if (nextLine.equals("exit")) {
                break;
            }
            ChatMessage chatMessage = openAiKit.createChatMessage(ChatMessageRole.USER, nextLine, null);
            ChatPromptRequest request = openAiKit.createChatPromptRequest(chatMessage);
            promptHandler.onPrompt(chatThread, request, new ChatPromptResponse() {
                @Override
                public void responseReceived(ChatMessage responseMessage) {
                    System.out.println("Response: " + responseMessage.getContent());
                }
            });

        }
    }

    public static void main(String[] args) throws Exception {
        ConfigLoader configLoader = DIContext.getInstance().getInstance(CliConfigLoader.class);
        configLoader.loadConfig(CliChatController.class);
        DIContext.getInstance().getInstance(CliChatController.class).run();
    }
}
