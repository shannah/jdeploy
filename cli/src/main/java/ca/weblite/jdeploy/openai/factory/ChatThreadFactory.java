package ca.weblite.jdeploy.openai.factory;

import ca.weblite.jdeploy.openai.model.ChatThread;

import javax.inject.Singleton;

@Singleton
public interface ChatThreadFactory {
    ChatThread createChatThread();
}
