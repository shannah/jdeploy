package ca.weblite.jdeploy.openai.dtos;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class GenerateProjectResponse {

    @JsonPropertyDescription("The path to the generated project")
    public String path;

    @JsonPropertyDescription("An error message if there was an error")
    public String error;
}
