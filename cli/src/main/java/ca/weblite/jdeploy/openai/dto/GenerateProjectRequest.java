package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class GenerateProjectRequest {

    @JsonPropertyDescription("The name of the github repository where to publish the project.  e.g. username/repo-name")
    public String githubRepository;

    @JsonPropertyDescription("The project template to use.  Must be one of codenameone, javafx, or swing")
    public String template;

}
