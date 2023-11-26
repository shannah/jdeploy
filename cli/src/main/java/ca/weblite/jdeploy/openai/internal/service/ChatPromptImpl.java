package ca.weblite.jdeploy.openai.internal.service;

import ca.weblite.jdeploy.openai.chat.PromptHandler;
import ca.weblite.jdeploy.openai.config.OpenAiChatConfig;
import ca.weblite.jdeploy.openai.internal.model.ChatMessageImpl;
import ca.weblite.jdeploy.openai.internal.model.ChatThreadImpl;
import ca.weblite.jdeploy.openai.interop.ChatThreadDispatcher;
import ca.weblite.jdeploy.openai.interop.UiThreadDispatcher;
import ca.weblite.jdeploy.openai.model.ChatPromptRequest;
import ca.weblite.jdeploy.openai.model.ChatPromptResponse;
import ca.weblite.jdeploy.openai.model.ChatThread;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;

@Singleton
public class ChatPromptImpl implements PromptHandler {

    private final UiThreadDispatcher uiThreadDispatcher;

    private final ChatThreadDispatcher chatThreadDispatcher;

    private final OpenAiChatConfig config;

    private final JDeployFunctionExecutor  functionExecutor;

    @Inject
    public ChatPromptImpl(
            JDeployFunctionExecutor functionExecutor,
            UiThreadDispatcher dispatchThread,
            ChatThreadDispatcher chatThreadDispatcher,
            OpenAiChatConfig config
    ) {
        this.functionExecutor = functionExecutor;
        this.uiThreadDispatcher = dispatchThread;
        this.chatThreadDispatcher = chatThreadDispatcher;
        this.config = config;
    }

    @Override
    public void onPrompt(
            ChatThread chatThread,
            ChatPromptRequest request,
            ChatPromptResponse response
    ) {
        chatThreadDispatcher.run(() -> onPromptImpl(chatThread, request, response));
    }

    private void onPromptImpl(
            ChatThread chatThread,
            ChatPromptRequest chatPromptRequest,
            ChatPromptResponse chatPromptResponse
    ) {
        final ChatThreadImpl chatThreadImpl = (ChatThreadImpl) chatThread;
        System.out.println(">>>>>> Received user input <<<<<<<<<<");
        System.out.println(chatPromptRequest.getUserInput().getContent());
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        chatThreadImpl.addMessage(chatPromptRequest.getUserInput());
        String token = config.getOpenAiApiKey();
        OpenAiService service = new OpenAiService(token, Duration.ZERO);

        while (true) {
            final List<ChatMessage> messages = new ArrayList<>(((ChatThreadImpl) chatThread).getInternalMessages());
            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                    .builder()
                    .model("gpt-4-1106-preview")
                    //.model("gpt-3.5-turbo-16k")
                    .messages(messages)
                    .functions(functionExecutor.getFunctions())
                    .functionCall(ChatCompletionRequest.ChatCompletionRequestFunctionCall.of("auto"))
                    .n(1)
                    .maxTokens(4096)
                    .logitBias(new HashMap<>())
                    .build();
            ChatMessage responseMessage = service.createChatCompletion(chatCompletionRequest)
                    .getChoices()
                    .get(0)
                    .getMessage();
            chatThreadImpl.addMessage(new ChatMessageImpl(responseMessage));

            ChatFunctionCall functionCall = responseMessage.getFunctionCall();
            if (functionCall != null) {
                System.out.println("Trying to execute " + functionCall.getName() + "...");
                Optional<ChatMessage> message;
                try {
                    message = Optional.of(functionExecutor.executeAndConvertToMessage(functionCall));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    ChatMessageImpl errorMessage = new ChatMessageImpl(
                            new ChatMessage(ChatMessageRole.SYSTEM.value(), "Something went wrong with the execution of " + functionCall.getName() + ".  Try a different function")
                    );
                    chatThreadImpl.addMessage(errorMessage);
                    uiThreadDispatcher.run(() -> chatPromptResponse.responseReceived(errorMessage));
                    return;
                }

                System.out.println("Executed " + functionCall.getName() + ".");
                ChatMessageImpl functionResultMessage = new ChatMessageImpl(message.get());
                chatThreadImpl.addMessage(functionResultMessage);

                //uiThreadDispatcher.run(() -> chatPromptResponse.responseReceived(functionResultMessage));
            } else {
                uiThreadDispatcher.run(() -> chatPromptResponse.responseReceived(new ChatMessageImpl(responseMessage)));
                return;
            }
        }
    }
}
