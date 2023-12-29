package ca.weblite.jdeploy.openai.model;

public interface ChatPromptResponse {
    void responseReceived(ChatMessage responseMessage);
}
