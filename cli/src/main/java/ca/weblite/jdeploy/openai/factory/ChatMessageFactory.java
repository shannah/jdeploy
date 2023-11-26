package ca.weblite.jdeploy.openai.factory;

import ca.weblite.jdeploy.openai.model.ChatMessage;

import javax.inject.Singleton;

@Singleton
public interface ChatMessageFactory {
    ChatMessage createChatMessage(ca.weblite.jdeploy.openai.model.ChatMessageRole role,
                                  String content,
                                  String name
    );
}
