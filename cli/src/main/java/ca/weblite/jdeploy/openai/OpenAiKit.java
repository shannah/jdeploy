package ca.weblite.jdeploy.openai;

import ca.weblite.jdeploy.openai.model.ChatMessage;
import ca.weblite.jdeploy.openai.model.ChatMessageRole;
import ca.weblite.jdeploy.openai.model.ChatPromptRequest;
import ca.weblite.jdeploy.openai.model.ChatThread;

import javax.inject.Singleton;

@Singleton
public interface OpenAiKit {

    ChatThread createChatThread();
    ChatMessage createChatMessage(ChatMessageRole role, String message, String name);
    ChatPromptRequest createChatPromptRequest(ChatMessage userInput);
}
