package ca.weblite.jdeploy.openai.internal.factory;

import ca.weblite.jdeploy.openai.factory.ChatThreadFactory;
import ca.weblite.jdeploy.openai.internal.model.ChatThreadImpl;
import ca.weblite.jdeploy.openai.model.ChatThread;

import javax.inject.Singleton;

@Singleton
public class ChatThreadFactoryImpl implements ChatThreadFactory {
    public ChatThread createChatThread() {
        return new ChatThreadImpl();
    }
}
