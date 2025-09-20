package ca.weblite.jdeploy.services;

import java.io.File;

/**
 * Example usage of ProjectTypeDetectionService.
 * 
 * This service detects:
 * - Build tools: Maven (pom.xml), Gradle (build.gradle), Ant (build.xml)
 * - Frameworks: JavaFX, Compose Multiplatform, LibGDX, FXGL, Spring Boot, Android
 * - Multi-module projects
 */
public class ProjectTypeDetectionExample {
    
    public static void main(String[] args) {
        ProjectTypeDetectionService service = new ProjectTypeDetectionService();
        
        // Example usage
        File projectDirectory = new File(".");
        ProjectType projectType = service.detectProjectType(projectDirectory);
        
        System.out.println("Project Analysis:");
        System.out.println("Build Tool: " + projectType.getBuildTool());
        System.out.println("Framework: " + projectType.getFramework());
        System.out.println("Multi-module: " + projectType.isMultiModule());
        
        // Convenience methods
        if (service.isJavaFXProject(projectDirectory)) {
            System.out.println("This is a JavaFX project");
        }
        
        if (service.isComposeMultiplatformProject(projectDirectory)) {
            System.out.println("This is a Compose Multiplatform project");
        }
    }
}