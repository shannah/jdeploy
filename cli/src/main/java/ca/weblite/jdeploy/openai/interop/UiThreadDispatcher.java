package ca.weblite.jdeploy.openai.interop;

public interface UiThreadDispatcher {
    void run(Runnable runnable);
}
