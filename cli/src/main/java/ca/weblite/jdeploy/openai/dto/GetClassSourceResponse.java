package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class GetClassSourceResponse {

    @JsonPropertyDescription("The Java source code for the class")
    public String source;

    @JsonPropertyDescription("The error message if there was an error")
    public String error;
}
