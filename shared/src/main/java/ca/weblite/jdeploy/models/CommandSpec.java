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

    public CommandSpec(String name, String description, List<String> args) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CommandSpec that = (CommandSpec) o;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(args, that.args);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, args);
    }

    @Override
    public String toString() {
        return "CommandSpec{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", args=" + args +
                '}';
    }
}
