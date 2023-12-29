package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class GenerateProjectResponse {

    @JsonPropertyDescription("The path to the generated project")
    public String path;

    @JsonPropertyDescription("An error message if there was an error")
    public String error;

    @JsonPropertyDescription("The main class name for the generated project")
    public String mainClassName;

    @JsonPropertyDescription("The package name where the main class is located, in the generated project")
    public String packageName;

    @JsonPropertyDescription("The name of the app that was generated")
    public String appName;
}
