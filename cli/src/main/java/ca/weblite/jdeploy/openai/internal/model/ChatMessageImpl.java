package ca.weblite.jdeploy.openai.internal.model;

import ca.weblite.jdeploy.DIContext;
import ca.weblite.jdeploy.openai.internal.transform.ChatMessageRoleTransformer;
import ca.weblite.jdeploy.openai.model.ChatMessageRole;
import com.theokanning.openai.completion.chat.ChatMessage;
import ca.weblite.jdeploy.openai.model.ChatFunctionCall;

public class ChatMessageImpl implements ca.weblite.jdeploy.openai.model.ChatMessage{

    private final ChatMessage chatMessage;

    public ChatMessageImpl(ChatMessage chatMessage) {
        this.chatMessage = chatMessage;
    }

    @Override
    public ChatMessageRole getRole() {
        return DIContext.getInstance()
                .getInstance(ChatMessageRoleTransformer.class)
                .toDomain(com.theokanning.openai.completion.chat.ChatMessageRole.valueOf(chatMessage.getRole()));
    }

    @Override
    public String getName() {
        return chatMessage.getName();
    }

    @Override
    public String getContent() {
        return chatMessage.getContent();
    }

    @Override
    public ChatFunctionCall getFunctionCall() {
        if (chatMessage.getFunctionCall() == null) {
            return null;
        }
        return new ChatFunctionCallImpl(chatMessage.getFunctionCall());
    }

    public com.theokanning.openai.completion.chat.ChatMessage getInternalChatMessage() {
        return this.chatMessage;
    }
}
