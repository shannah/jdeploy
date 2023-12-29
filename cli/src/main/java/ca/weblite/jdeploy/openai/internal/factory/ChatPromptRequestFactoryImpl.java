package ca.weblite.jdeploy.openai.internal.factory;

import ca.weblite.jdeploy.openai.factory.ChatPromptRequestFactory;
import ca.weblite.jdeploy.openai.internal.model.ChatMessageImpl;
import ca.weblite.jdeploy.openai.internal.model.ChatPromptRequestImpl;
import ca.weblite.jdeploy.openai.model.ChatMessage;
import ca.weblite.jdeploy.openai.model.ChatPromptRequest;

import javax.inject.Singleton;

@Singleton
public class ChatPromptRequestFactoryImpl implements ChatPromptRequestFactory {
    @Override
    public ChatPromptRequest create(ChatMessage userInput) {
        return new ChatPromptRequestImpl((ChatMessageImpl) userInput);
    }
}
