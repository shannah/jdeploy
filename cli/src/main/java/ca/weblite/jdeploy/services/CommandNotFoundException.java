package ca.weblite.jdeploy.services;

/**
 * Exception thrown when attempting to run a command that is not defined in the project's package.json.
 */
public class CommandNotFoundException extends Exception {

    private final String commandName;

    public CommandNotFoundException(String commandName) {
        super("Command not found: " + commandName);
        this.commandName = commandName;
    }

    public CommandNotFoundException(String commandName, String message) {
        super(message);
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
    }
}
