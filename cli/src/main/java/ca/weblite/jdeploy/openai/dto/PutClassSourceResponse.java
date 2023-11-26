package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class PutClassSourceResponse {
    @JsonPropertyDescription("True if the source was successfully saved")
    public boolean success;
    @JsonPropertyDescription("The error message if there was an error")
    public String error;
}
