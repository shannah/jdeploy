package ca.weblite.jdeploy.installer.events;

import ca.weblite.jdeploy.installer.views.InstallationForm;

import java.util.ArrayList;
import java.util.List;

public class InstallationFormEventDispatcher {
    private List<InstallationFormEventListener> listeners = new ArrayList<>();
    private InstallationForm installationForm;

    public InstallationFormEventDispatcher(InstallationForm installationForm) {
        this.installationForm = installationForm;
    }

    public InstallationFormEvent fireEvent(InstallationFormEvent event) {
        event.setInstallationForm(installationForm);
        ArrayList<InstallationFormEventListener> sendList = new ArrayList<>(listeners);
        for (InstallationFormEventListener l : sendList) {
            if (event.isConsumed()) break;
            l.on(event);
        }
        return event;
    }

    public InstallationFormEvent fireEvent(InstallationFormEvent.Type type) {
        return fireEvent(new InstallationFormEvent(type));
    }

    public void addEventListener(InstallationFormEventListener l) {
        listeners.add(l);
    }

    public void removeEventListener(InstallationFormEventListener l) {
        listeners.remove(l);
    }
}
