package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class PutMainClassSourceRequest {


    @JsonPropertyDescription("The app name")
    public String appName;

    @JsonPropertyDescription("The main class name")
    public String mainClassName;

    @JsonPropertyDescription("Java package where the main class is located")
    public String packageName;

    @JsonPropertyDescription("The new source code for the main class")
    public String sourceCode;

}
