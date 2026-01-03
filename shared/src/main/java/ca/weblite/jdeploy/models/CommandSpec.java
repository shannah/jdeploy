package ca.weblite.jdeploy.models;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable specification for a CLI command installed by the installer.
 */
public class CommandSpec {
    private final String name;
    private final String description;
    private final List<String> args;
    private final List<String> implementations;

    public CommandSpec(String name, String description, List<String> args, List<String> implementations) {
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
    }

    /**
     * Constructor for backward compatibility (no implementations specified).
     */
    public CommandSpec(String name, String description, List<String> args) {
        this(name, description, args, null);
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
     * @param implementation one of: "updater", "service_controller", "launcher"
     * @return true if this command implements the given behavior
     */
    public boolean implements_(String implementation) {
        return implementations.contains(implementation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommandSpec that = (CommandSpec) o;
        return Objects.equals(name, that.name) &&
               Objects.equals(description, that.description) &&
               Objects.equals(args, that.args) &&
               Objects.equals(implementations, that.implementations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, args, implementations);
    }

    @Override
    public String toString() {
        return "CommandSpec{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", args=" + args +
                ", implementations=" + implementations +
                '}';
    }
}
