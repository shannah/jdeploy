package ca.weblite.jdeploy.openai.internal.transform;

import com.theokanning.openai.completion.chat.ChatMessageRole;

import javax.inject.Singleton;

@Singleton
public class ChatMessageRoleTransformer {
    public ChatMessageRole toImplDomain(ca.weblite.jdeploy.openai.model.ChatMessageRole role) {
        if (role == null) {
            return null;
        }
        switch (role) {
            case SYSTEM:
                return ChatMessageRole.SYSTEM;
            case FUNCTION:
                return ChatMessageRole.FUNCTION;
            case USER:
                return ChatMessageRole.USER;
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }

    public ca.weblite.jdeploy.openai.model.ChatMessageRole toDomain(ChatMessageRole role) {
        if (role == null) {
            return null;
        }
        switch (role) {
            case SYSTEM:
                return ca.weblite.jdeploy.openai.model.ChatMessageRole.SYSTEM;
            case FUNCTION:
                return ca.weblite.jdeploy.openai.model.ChatMessageRole.FUNCTION;
            case USER:
                return ca.weblite.jdeploy.openai.model.ChatMessageRole.USER;
            default:
                throw new IllegalArgumentException("Unknown role: " + role);
        }
    }
}
