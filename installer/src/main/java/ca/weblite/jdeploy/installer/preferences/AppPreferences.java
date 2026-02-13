package ca.weblite.jdeploy.installer.preferences;

/**
 * Model class representing application preferences.
 *
 * These preferences are stored externally (outside the app bundle) so that:
 * 1. Prebuilt signed apps can have user-specific settings without re-signing
 * 2. User preferences survive app updates
 * 3. Different installation modes (prerelease, specific version) can be configured
 *
 * Preferences are stored in: ~/.jdeploy/preferences/{fqn}/preferences.xml
 * Where {fqn} is the fully-qualified package name (optionally with source hash prefix).
 */
public class AppPreferences {

    /**
     * The version of the preferences schema.
     * Used for forward/backward compatibility.
     */
    private String schemaVersion = "1.0";

    /**
     * The installed app version.
     * Used to track what version the user currently has.
     */
    private String version;

    /**
     * The prerelease channel setting.
     * Values: "stable", "prerelease"
     * "stable" = only stable releases
     * "prerelease" = include pre-release versions
     */
    private String prereleaseChannel = "stable";

    /**
     * Auto-update setting.
     * Values: "all", "minor", "patch", "none"
     * "all" = update to any newer version
     * "minor" = update within same major version
     * "patch" = update within same minor version
     * "none" = no automatic updates
     */
    private String autoUpdate = "minor";

    /**
     * Custom JVM arguments.
     * Optional additional JVM arguments to pass when launching.
     */
    private String jvmArgs;

    /**
     * Whether the app was installed from a prebuilt bundle.
     * If true, the launcher should not attempt to rebuild locally.
     */
    private boolean prebuiltInstallation = false;

    // Getters and Setters

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPrereleaseChannel() {
        return prereleaseChannel;
    }

    public void setPrereleaseChannel(String prereleaseChannel) {
        this.prereleaseChannel = prereleaseChannel;
    }

    public String getAutoUpdate() {
        return autoUpdate;
    }

    public void setAutoUpdate(String autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public String getJvmArgs() {
        return jvmArgs;
    }

    public void setJvmArgs(String jvmArgs) {
        this.jvmArgs = jvmArgs;
    }

    public boolean isPrebuiltInstallation() {
        return prebuiltInstallation;
    }

    public void setPrebuiltInstallation(boolean prebuiltInstallation) {
        this.prebuiltInstallation = prebuiltInstallation;
    }

    /**
     * Creates a builder for constructing AppPreferences.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for AppPreferences.
     */
    public static class Builder {
        private final AppPreferences preferences = new AppPreferences();

        public Builder version(String version) {
            preferences.setVersion(version);
            return this;
        }

        public Builder prereleaseChannel(String channel) {
            preferences.setPrereleaseChannel(channel);
            return this;
        }

        public Builder autoUpdate(String autoUpdate) {
            preferences.setAutoUpdate(autoUpdate);
            return this;
        }

        public Builder jvmArgs(String jvmArgs) {
            preferences.setJvmArgs(jvmArgs);
            return this;
        }

        public Builder prebuiltInstallation(boolean prebuilt) {
            preferences.setPrebuiltInstallation(prebuilt);
            return this;
        }

        public AppPreferences build() {
            return preferences;
        }
    }
}
