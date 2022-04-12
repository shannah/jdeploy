package ca.weblite.jdeploy.installer.models;

import ca.weblite.jdeploy.installer.Constants;

public enum AutoUpdateSettings {
    Stable,
    MinorOnly,
    PatchesOnly,
    Off;

    private String label;

    public void setLabel(String label) {
        this.label = label;
    }


    @Override
    public String toString() {
        if (label != null) return label;
        return Constants.strings.getString(this.name());
    }
}
