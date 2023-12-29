package ca.weblite.jdeploy.openai.chat;

import ca.weblite.jdeploy.openai.model.ChatPromptRequest;
import ca.weblite.jdeploy.openai.model.ChatPromptResponse;
import ca.weblite.jdeploy.openai.model.ChatThread;

public interface PromptHandler {
    public void onPrompt(ChatThread chatThread, ChatPromptRequest request, ChatPromptResponse response);
}
