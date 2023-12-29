package ca.weblite.jdeploy.openai.internal.model;

import com.theokanning.openai.completion.chat.ChatFunctionCall;

public class ChatFunctionCallImpl implements ca.weblite.jdeploy.openai.model.ChatFunctionCall {

    private final ChatFunctionCall chatFunctionCall;

    public ChatFunctionCallImpl(ChatFunctionCall chatFunctionCall) {
        this.chatFunctionCall = chatFunctionCall;
    }

    @Override
    public String getName() {
        return chatFunctionCall.getName();
    }
}
