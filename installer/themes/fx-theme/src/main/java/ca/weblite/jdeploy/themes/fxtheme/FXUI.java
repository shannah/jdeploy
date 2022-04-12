package ca.weblite.jdeploy.themes.fxtheme;

import ca.weblite.jdeploy.installer.views.UI;
import javafx.application.Platform;

public class FXUI implements UI {
    static boolean initialized;
    @Override
    public void run(Runnable r) {
        if (!initialized) {
            r.run();
            return;
        }
        Platform.runLater(r);
    }

    @Override
    public boolean isOnUIThread() {
        if (!initialized) {
            return false;
        }
        return Platform.isFxApplicationThread();
    }
}
