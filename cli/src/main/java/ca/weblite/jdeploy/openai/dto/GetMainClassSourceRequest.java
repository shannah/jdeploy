package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class GetMainClassSourceRequest {


    @JsonPropertyDescription("The app name")
    public String appName;

    @JsonPropertyDescription("The main class name")
    public String mainClassName;

    @JsonPropertyDescription("Java package where the main class is located")
    public String packageName;

}
