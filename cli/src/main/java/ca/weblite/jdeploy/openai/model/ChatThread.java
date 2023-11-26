package ca.weblite.jdeploy.openai.model;

import java.util.List;

public interface ChatThread {
    List<ChatMessage> getMessages();
    void addMessage(ChatMessage message);

    void withLock(Runnable runnable);
}
