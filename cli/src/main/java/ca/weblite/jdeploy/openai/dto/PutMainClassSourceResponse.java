package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class PutMainClassSourceResponse {

    @JsonPropertyDescription("The source code for the main class")
    public String source;

    @JsonPropertyDescription("An error message if there was an error")
    public String error;
}
