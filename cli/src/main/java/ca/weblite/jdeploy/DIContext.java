package ca.weblite.jdeploy;

import ca.weblite.jdeploy.di.JDeployModule;
import org.codejargon.feather.Feather;

public class DIContext {

    private final Feather feather = Feather.with(new JDeployModule());

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
}
