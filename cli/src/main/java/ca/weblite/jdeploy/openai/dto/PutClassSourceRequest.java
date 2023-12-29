package ca.weblite.jdeploy.openai.dto;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class PutClassSourceRequest {
    @JsonPropertyDescription("The name of the project to get. This is also the folder name within the projects folder")
    public String projectName;
    @JsonPropertyDescription("The simple name of the class to get.  E.g. MyClass")
    public String className;
    @JsonPropertyDescription("Java package where the class is located.  E.g. com.mycompany.myapp")
    public String packageName;

    @JsonPropertyDescription("The Java source code for the class")
    public String source;

}
