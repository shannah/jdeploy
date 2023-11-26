package ca.weblite.jdeploy.cli.util;

import ca.weblite.jdeploy.openai.interop.UiThreadDispatcher;

import javax.inject.Singleton;

@Singleton
public class CliUiThreadDispatcher implements UiThreadDispatcher {
    @Override
    public void run(Runnable runnable) {
        runnable.run();
    }
}
