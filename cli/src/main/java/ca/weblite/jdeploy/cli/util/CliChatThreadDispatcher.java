package ca.weblite.jdeploy.cli.util;

import ca.weblite.jdeploy.openai.interop.ChatThreadDispatcher;

import javax.inject.Singleton;

@Singleton
public class CliChatThreadDispatcher implements ChatThreadDispatcher {
    @Override
    public void run(Runnable runnable) {
        runnable.run();
    }
}
