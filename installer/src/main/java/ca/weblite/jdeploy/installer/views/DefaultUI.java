package ca.weblite.jdeploy.installer.views;

import java.awt.*;

public class DefaultUI implements UI {
    @Override
    public void run(Runnable r) {
        if (EventQueue.isDispatchThread()) {
            r.run();
        } else {
            EventQueue.invokeLater(r);
        }
    }

    @Override
    public boolean isOnUIThread() {
        return EventQueue.isDispatchThread();
    }
}
