package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class GetProjectRequest {
    @JsonPropertyDescription("The name of the project to get. This is also the folder name within the projects folder")
    public String projectName;
}
