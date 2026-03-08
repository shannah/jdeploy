package ca.weblite.jdeploy.services.verification;

import ca.weblite.jdeploy.models.CommandSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Contains resolved package information needed for installation verification.
 */
public class ResolvedPackageInfo {

    private final String packageName;
    private final String source;        // null for NPM packages
    private final String title;
    private final String version;
    private final List<CommandSpec> commands;
    private final String winAppDir;     // Windows custom install directory

    private ResolvedPackageInfo(Builder builder) {
        this.packageName = builder.packageName;
        this.source = builder.source;
        this.title = builder.title;
        this.version = builder.version;
        this.commands = builder.commands != null
                ? Collections.unmodifiableList(new ArrayList<>(builder.commands))
                : Collections.emptyList();
        this.winAppDir = builder.winAppDir;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public String getVersion() {
        return version;
    }

    public List<CommandSpec> getCommands() {
        return commands;
    }

    public String getWinAppDir() {
        return winAppDir;
    }

    public boolean hasCommands() {
        return commands != null && !commands.isEmpty();
    }

    public boolean isGitHubSource() {
        return source != null && !source.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String packageName;
        private String source;
        private String title;
        private String version;
        private List<CommandSpec> commands;
        private String winAppDir;

        public Builder packageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder source(String source) {
            this.source = source;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder commands(List<CommandSpec> commands) {
            this.commands = commands;
            return this;
        }

        public Builder winAppDir(String winAppDir) {
            this.winAppDir = winAppDir;
            return this;
        }

        public ResolvedPackageInfo build() {
            if (packageName == null || packageName.isEmpty()) {
                throw new IllegalStateException("packageName is required");
            }
            if (title == null || title.isEmpty()) {
                // Default title to package name
                title = packageName;
            }
            return new ResolvedPackageInfo(this);
        }
    }
}
