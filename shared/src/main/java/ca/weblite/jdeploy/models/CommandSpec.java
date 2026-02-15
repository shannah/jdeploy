package ca.weblite.jdeploy.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable specification for a CLI command installed by the installer.
 */
public class CommandSpec {
    /**
     * Default trigger argument for the updater implementation.
     */
    public static final String DEFAULT_UPDATE_TRIGGER = "update";

    /**
     * Default trigger argument for the uninstaller implementation.
     */
    public static final String DEFAULT_UNINSTALL_TRIGGER = "uninstall";

    private final String name;
    private final String description;
    private final List<String> args;
    private final List<String> implementations;
    private final Boolean embedPlist;
    private final String updateTrigger;
    private final String uninstallTrigger;

    public CommandSpec(String name, String description, List<String> args, List<String> implementations, Boolean embedPlist, String updateTrigger, String uninstallTrigger) {
        if (name == null) {
            throw new IllegalArgumentException("Command name cannot be null");
        }
        this.name = name;
        this.description = description;
        if (args == null) {
            this.args = Collections.emptyList();
        } else {
            this.args = Collections.unmodifiableList(new ArrayList<>(args));
        }
        if (implementations == null) {
            this.implementations = Collections.emptyList();
        } else {
            this.implementations = Collections.unmodifiableList(new ArrayList<>(implementations));
        }
        this.embedPlist = embedPlist;
        this.updateTrigger = (updateTrigger != null && !updateTrigger.isEmpty()) ? updateTrigger : DEFAULT_UPDATE_TRIGGER;
        this.uninstallTrigger = (uninstallTrigger != null && !uninstallTrigger.isEmpty()) ? uninstallTrigger : DEFAULT_UNINSTALL_TRIGGER;
    }

    public CommandSpec(String name, String description, List<String> args, List<String> implementations, Boolean embedPlist, String updateTrigger) {
        this(name, description, args, implementations, embedPlist, updateTrigger, null);
    }

    public CommandSpec(String name, String description, List<String> args, List<String> implementations, Boolean embedPlist) {
        this(name, description, args, implementations, embedPlist, null, null);
    }

    public CommandSpec(String name, String description, List<String> args, List<String> implementations) {
        this(name, description, args, implementations, null, null);
    }

    /**
     * Constructor for backward compatibility (no implementations specified).
     */
    public CommandSpec(String name, String description, List<String> args) {
        this(name, description, args, null, null, null);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getArgs() {
        return args;
    }

    public List<String> getImplementations() {
        return implementations;
    }

    /**
     * Checks if this command implements a specific behavior.
     * @param implementation one of: "updater", "service_controller", "launcher", "uninstaller"
     * @return true if this command implements the given behavior
     */
    public boolean implements_(String implementation) {
        return implementations.contains(implementation);
    }

    /**
     * Returns whether this command should have an embedded LaunchAgent plist
     * generated in the macOS app bundle.
     *
     * @return {@code Boolean.TRUE} to force embedding, {@code Boolean.FALSE} to force
     *         the launchctl fallback, or {@code null} to use the default heuristic
     *         (embed if all args are static).
     */
    public Boolean getEmbedPlist() {
        return embedPlist;
    }

    /**
     * Returns the trigger argument that activates the updater implementation.
     * When the command is invoked with this single argument, it triggers the
     * {@code --jdeploy:update} launcher flag.
     *
     * @return the update trigger argument, defaults to "update"
     */
    public String getUpdateTrigger() {
        return updateTrigger;
    }

    /**
     * Returns the trigger argument that activates the uninstaller implementation.
     * When the command is invoked with this single argument, it triggers the
     * {@code --jdeploy:uninstall} launcher flag.
     *
     * @return the uninstall trigger argument, defaults to "uninstall"
     */
    public String getUninstallTrigger() {
        return uninstallTrigger;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommandSpec that = (CommandSpec) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(args, that.args) &&
               Objects.equals(implementations, that.implementations) &&
               Objects.equals(embedPlist, that.embedPlist) &&
               Objects.equals(updateTrigger, that.updateTrigger) &&
               Objects.equals(uninstallTrigger, that.uninstallTrigger);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, args, implementations, embedPlist, updateTrigger, uninstallTrigger);
    }

    @Override
    public String toString() {
        return "CommandSpec{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", args=" + args +
                ", implementations=" + implementations +
                ", embedPlist=" + embedPlist +
                ", updateTrigger='" + updateTrigger + '\'' +
                ", uninstallTrigger='" + uninstallTrigger + '\'' +
                '}';
    }
}
