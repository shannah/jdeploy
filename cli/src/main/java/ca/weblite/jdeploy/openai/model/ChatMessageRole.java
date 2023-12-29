package ca.weblite.jdeploy.openai.model;

public enum ChatMessageRole {
    SYSTEM("system"),
    USER("user"),
    ASSISTANT("assistant"),
    FUNCTION("function");

    private final String value;

    private ChatMessageRole(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }
}

