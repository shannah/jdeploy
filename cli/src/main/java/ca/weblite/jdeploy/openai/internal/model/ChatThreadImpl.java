package ca.weblite.jdeploy.openai.internal.model;

import ca.weblite.jdeploy.openai.model.ChatMessage;

import java.util.ArrayList;
import java.util.List;

public class ChatThreadImpl implements ca.weblite.jdeploy.openai.model.ChatThread {
    private java.util.List<ChatMessage> messages = new ArrayList<>();
    public synchronized java.util.List<ChatMessage> getMessages() {
        return this.messages;
    }
    public synchronized void addMessage(ChatMessage message) {
        this.messages.add(message);
    }

    @Override
    public void withLock(Runnable runnable) {
        synchronized(this) {
            runnable.run();
        }
    }

    public synchronized List<com.theokanning.openai.completion.chat.ChatMessage> getInternalMessages() {
        return this.messages.stream()
                .map(msg -> (ChatMessageImpl) msg)
                .map(ChatMessageImpl::getInternalChatMessage)
                .collect(java.util.stream.Collectors.toList());
    }
}
