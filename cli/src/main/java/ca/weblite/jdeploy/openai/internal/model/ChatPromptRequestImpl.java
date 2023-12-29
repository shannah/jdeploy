package ca.weblite.jdeploy.openai.internal.model;

import ca.weblite.jdeploy.openai.model.ChatMessage;
import ca.weblite.jdeploy.openai.model.ChatPromptRequest;

public class ChatPromptRequestImpl implements ChatPromptRequest {

    private final ChatMessageImpl userInput;

    public ChatPromptRequestImpl(ChatMessageImpl userInput) {
        this.userInput = userInput;
    }

    @Override
    public ChatMessage getUserInput() {
        return userInput;
    }
}
