package ca.weblite.jdeploy.openai.internal;

import ca.weblite.jdeploy.openai.OpenAiKit;
import ca.weblite.jdeploy.openai.internal.factory.ChatMessageFactoryImpl;
import ca.weblite.jdeploy.openai.internal.factory.ChatPromptRequestFactoryImpl;
import ca.weblite.jdeploy.openai.internal.factory.ChatThreadFactoryImpl;
import ca.weblite.jdeploy.openai.model.ChatMessage;
import ca.weblite.jdeploy.openai.model.ChatMessageRole;
import ca.weblite.jdeploy.openai.model.ChatPromptRequest;
import ca.weblite.jdeploy.openai.model.ChatThread;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DefaultOpenAiKit implements OpenAiKit {

    private final ChatThreadFactoryImpl chatThreadFactory;
    private final ChatPromptRequestFactoryImpl chatPromptRequestFactory;
    private final ChatMessageFactoryImpl chatMessageFactory;

    @Inject
    public DefaultOpenAiKit() {
        this.chatThreadFactory = new ChatThreadFactoryImpl();
        this.chatPromptRequestFactory = new ChatPromptRequestFactoryImpl();
        this.chatMessageFactory = new ChatMessageFactoryImpl();
    }
    @Override
    public ChatThread createChatThread() {
        return chatThreadFactory.createChatThread();
    }

    @Override
    public ChatMessage createChatMessage(ChatMessageRole role, String message, String name) {
        return chatMessageFactory.createChatMessage(role, message, name);
    }

    @Override
    public ChatPromptRequest createChatPromptRequest(ChatMessage userInput) {
        return chatPromptRequestFactory.create(userInput);
    }
}
