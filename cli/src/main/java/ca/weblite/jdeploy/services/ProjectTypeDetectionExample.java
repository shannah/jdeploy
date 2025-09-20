package ca.weblite.jdeploy.services;

import java.io.File;

/**
 * Example usage of ProjectTypeDetectionService.
 * 
 * This service detects:
 * - Build tools: Maven (pom.xml), Gradle (build.gradle), Ant (build.xml)
 * - Frameworks: Compose Multiplatform, FXGL, LibGDX, Spring Boot, Android, JavaFX
 * - Multi-module projects
 * 
 * Detection Order:
 * Frameworks are detected in order of specificity - more specific frameworks
 * are detected before more general ones. For example, FXGL is detected before
 * JavaFX since FXGL projects typically include JavaFX dependencies.
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
        
        // Check specific frameworks
        switch (projectType.getFramework()) {
            case FXGL:
                System.out.println("This is an FXGL game project (will enable platform bundles)");
                break;
            case SPRING_BOOT:
                System.out.println("This is a Spring Boot application");
                break;
            case LIBGDX:
                System.out.println("This is a LibGDX game project (will enable platform bundles)");
                break;
        }
    }
}