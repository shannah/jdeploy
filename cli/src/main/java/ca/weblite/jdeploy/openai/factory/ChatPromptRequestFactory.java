package ca.weblite.jdeploy.openai.factory;

import ca.weblite.jdeploy.openai.model.ChatMessage;
import ca.weblite.jdeploy.openai.model.ChatPromptRequest;

public interface ChatPromptRequestFactory {
    ChatPromptRequest create(ChatMessage userInput);
}
