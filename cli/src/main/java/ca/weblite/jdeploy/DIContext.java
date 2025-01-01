package ca.weblite.jdeploy;

import ca.weblite.jdeploy.cli.di.JDeployCliModule;
import ca.weblite.jdeploy.di.JDeployModule;
import ca.weblite.jdeploy.openai.di.OpenAiModule;
import org.codejargon.feather.Feather;

import java.util.Arrays;

public class DIContext {

    private final Feather feather;

    private static DIContext instance;

    public DIContext(Object ...modules) {
        java.util.List<Object> args = new java.util.ArrayList<Object>();
        args.add(new JDeployModule());
        args.add(new OpenAiModule());
        args.add(new JDeployCliModule());
        args.addAll(Arrays.asList(modules));
        feather = Feather.with(args);
    }

    public <T> T getInstance(Class<T> clazz) {
        return feather.instance(clazz);
    }

    public static void initialize(Object ...modules) {
        synchronized (DIContext.class) {
            instance = new DIContext(modules);
        }
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
