package ca.weblite.jdeploy;

import ca.weblite.jdeploy.cli.di.JDeployCliModule;
import ca.weblite.jdeploy.di.JDeployModule;
import ca.weblite.jdeploy.openai.di.OpenAiModule;
import org.codejargon.feather.Feather;

public class DIContext {

    private final Feather feather = Feather.with(new JDeployModule(), new OpenAiModule(), new JDeployCliModule());

    private static DIContext instance;

    public <T> T getInstance(Class<T> clazz) {
        return feather.instance(clazz);
    }

    public static DIContext getInstance() {
        if (instance == null) {
            synchronized (DIContext.class) {
                if (instance == null) {
                    instance = new DIContext();
                }
            }
        }

        return instance;

    }

    public static <T> T get(Class<T> clazz) {
        return getInstance().getInstance(clazz);
    }
}
