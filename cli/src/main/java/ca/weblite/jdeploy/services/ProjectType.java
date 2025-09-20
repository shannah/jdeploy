package ca.weblite.jdeploy.services;

public class ProjectType {
    
    public enum BuildTool {
        MAVEN("pom.xml"),
        GRADLE("build.gradle", "build.gradle.kts"),
        ANT("build.xml"),
        UNKNOWN();
        
        private final String[] configFiles;
        
        BuildTool(String... configFiles) {
            this.configFiles = configFiles;
        }
        
        public String[] getConfigFiles() {
            return configFiles;
        }
    }
    
    public enum Framework {
        JAVAFX,
        COMPOSE_MULTIPLATFORM,
        LIBGDX,
        FXGL,
        SPRING_BOOT,
        ANDROID,
        PLAIN_JAVA,
        UNKNOWN
    }
    
    private final BuildTool buildTool;
    private final Framework framework;
    private final boolean isMultiModule;
    
    public ProjectType(BuildTool buildTool, Framework framework, boolean isMultiModule) {
        this.buildTool = buildTool;
        this.framework = framework;
        this.isMultiModule = isMultiModule;
    }
    
    public BuildTool getBuildTool() {
        return buildTool;
    }
    
    public Framework getFramework() {
        return framework;
    }
    
    public boolean isMultiModule() {
        return isMultiModule;
    }
    
    @Override
    public String toString() {
        return String.format("ProjectType{buildTool=%s, framework=%s, multiModule=%s}", 
                buildTool, framework, isMultiModule);
    }
}