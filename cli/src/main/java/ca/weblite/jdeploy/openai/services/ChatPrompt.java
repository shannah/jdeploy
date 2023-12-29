package ca.weblite.jdeploy.openai.services;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.openai.chat.PromptHandler;
import ca.weblite.jdeploy.openai.functions.GenerateProjectFunction;
import ca.weblite.jdeploy.openai.functions.GetMainClassSourceFunction;
import ca.weblite.jdeploy.openai.interop.UiThreadDispatcher;
import ca.weblite.jdeploy.openai.model.ChatPromptRequest;
import ca.weblite.jdeploy.openai.model.ChatPromptResponse;
import ca.weblite.jdeploy.openai.model.ChatThread;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatFunctionCall;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.FunctionExecutor;
import com.theokanning.openai.service.OpenAiService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Duration;
import java.util.*;

@Singleton
public class ChatPrompt implements PromptHandler{

    private final GenerateProjectFunction generateProjectFunction;

    private final GetMainClassSourceFunction getMainClassSourceFunction;

    private final UiThreadDispatcher dispatchThread;

    @Inject
    public ChatPrompt(
            GenerateProjectFunction generateProjectFunction,
            GetMainClassSourceFunction getMainClassSourceFunction,
            UiThreadDispatcher dispatchThread
    ) {
        this.generateProjectFunction = generateProjectFunction;
        this.getMainClassSourceFunction = getMainClassSourceFunction;
        this.dispatchThread = dispatchThread;
    }

    public void run() {
        String token = System.getenv("OPENAI_TOKEN");
        OpenAiService service = new OpenAiService(token, Duration.ZERO);

        FunctionExecutor functionExecutor = new FunctionExecutor(Arrays.asList(
                generateProjectFunction.asChatFunction(),
                getMainClassSourceFunction.asChatFunction()
        ));

        List<ChatMessage> messages = new ArrayList<>();

        System.out.print("First Query: ");

        Scanner scanner = new Scanner(System.in);
        ChatMessage firstMsg = new ChatMessage(ChatMessageRole.USER.value(), scanner.nextLine());
        messages.add(firstMsg);

        while (true) {

            ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest
                    .builder()
                    .model("gpt-3.5-turbo-16k")
                    .messages(messages)
                    .functions(functionExecutor.getFunctions())
                    .functionCall(ChatCompletionRequest.ChatCompletionRequestFunctionCall.of("auto"))
                    .n(1)
                    .maxTokens(7000)
                    .logitBias(new HashMap<>())
                    .build();
            ChatMessage responseMessage = service.createChatCompletion(chatCompletionRequest).getChoices().get(0).getMessage();
            messages.add(responseMessage); // don't forget to update the conversation with the latest response

            ChatFunctionCall functionCall = responseMessage.getFunctionCall();
            if (functionCall != null) {
                System.out.println("Trying to execute " + functionCall.getName() + "...");
                Optional<ChatMessage> message = Optional.empty();
                try {
                    message = Optional.of(functionExecutor.executeAndConvertToMessage(functionCall));
                } catch (Exception ex) {
                    ex.printStackTrace();
                    messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), "Something went wrong with the execution of " + functionCall.getName() + ".  Try a different function"));
                    continue;
                }
                /* You can also try 'executeAndConvertToMessage' inside a try-catch block, and add the following line inside the catch:
                "message = executor.handleException(exception);"
                The content of the message will be the exception itself, so the flow of the conversation will not be interrupted, and you will still be able to log the issue. */

                if (message.isPresent()) {
                    /* At this point:
                    1. The function requested was found
                    2. The request was converted to its specified object for execution (Weather.class in this case)
                    3. It was executed
                    4. The response was finally converted to a ChatMessage object. */

                    System.out.println("Executed " + functionCall.getName() + ".");
                    messages.add(message.get());
                    continue;
                } else {
                    System.out.println("Something went wrong with the execution of " + functionCall.getName() + "...");
                    break;
                }
            }

            System.out.println("Response: " + responseMessage.getContent());
            System.out.print("Next Query: ");
            String nextLine = scanner.nextLine();
            if (nextLine.equalsIgnoreCase("exit")) {
                System.exit(0);
            }
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), nextLine));
        }
    }

    public static void main(String[] args) {

        ChatPrompt chatPrompt = DIContext.getInstance().getInstance(ChatPrompt.class);
        chatPrompt.run();
    }

    @Override
    public void onPrompt(ChatThread chatThread, ChatPromptRequest request, ChatPromptResponse response) {

    }
}
