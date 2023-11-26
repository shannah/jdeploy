package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class GetProjectResponse {

    @JsonPropertyDescription("The package name containing the main class.   E.g. com.example.myapp")
    public String packageName;

    @JsonPropertyDescription("The main class simple name.  E.g. MyApp")
    public String mainClassName;

    @JsonPropertyDescription("An error message if there was an error")
    public String error;

}
