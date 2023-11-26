package ca.weblite.jdeploy.openai.model;

public interface ChatMessage {
    ChatMessageRole getRole();

    String getName();
    String getContent();
    ChatFunctionCall getFunctionCall();
}
