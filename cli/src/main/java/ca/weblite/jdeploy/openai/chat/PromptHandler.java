package ca.weblite.jdeploy.openai.chat;

public interface PromptHandler {
    public void onPrompt(ChatPromptRequest request, ChatPromptResponse response);
}
