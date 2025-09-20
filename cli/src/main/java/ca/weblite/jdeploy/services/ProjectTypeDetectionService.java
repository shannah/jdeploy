package ca.weblite.jdeploy.services;

import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.inject.Singleton;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Singleton
public class ProjectTypeDetectionService {
    
    private static final Pattern JAVAFX_PATTERN = Pattern.compile(
            "(?i).*(?:javafx|jfx|import\\s+javafx).*", Pattern.DOTALL);
    private static final Pattern COMPOSE_PATTERN = Pattern.compile(
            "(?i).*(?:compose[.-]multiplatform|org\\.jetbrains\\.compose).*", Pattern.DOTALL);
    private static final Pattern LIBGDX_PATTERN = Pattern.compile(
            "(?i).*(?:libgdx|gdx-|com\\.badlogicgames\\.gdx).*", Pattern.DOTALL);
    private static final Pattern FXGL_PATTERN = Pattern.compile(
            "(?i).*(?:fxgl|com\\.github\\.almasb).*", Pattern.DOTALL);
    private static final Pattern SPRING_BOOT_PATTERN = Pattern.compile(
            "(?i).*(?:spring-boot|@SpringBootApplication).*", Pattern.DOTALL);
    private static final Pattern ANDROID_PATTERN = Pattern.compile(
            "(?i).*(?:android|com\\.android\\.tools\\.build).*", Pattern.DOTALL);
    
    public ProjectType detectProjectType(File projectDirectory) {
        if (projectDirectory == null || !projectDirectory.exists() || !projectDirectory.isDirectory()) {
            return new ProjectType(ProjectType.BuildTool.UNKNOWN, ProjectType.Framework.UNKNOWN, false);
        }
        
        ProjectType.BuildTool buildTool = detectBuildTool(projectDirectory);
        ProjectType.Framework framework = detectFramework(projectDirectory, buildTool);
        boolean isMultiModule = detectMultiModule(projectDirectory, buildTool);
        
        return new ProjectType(buildTool, framework, isMultiModule);
    }
    
    private ProjectType.BuildTool detectBuildTool(File projectDirectory) {
        for (ProjectType.BuildTool tool : ProjectType.BuildTool.values()) {
            if (tool == ProjectType.BuildTool.UNKNOWN) continue;
            
            for (String configFile : tool.getConfigFiles()) {
                if (new File(projectDirectory, configFile).exists()) {
                    return tool;
                }
            }
        }
        return ProjectType.BuildTool.UNKNOWN;
    }
    
    private ProjectType.Framework detectFramework(File projectDirectory, ProjectType.BuildTool buildTool) {
        if (buildTool == ProjectType.BuildTool.UNKNOWN) {
            return ProjectType.Framework.UNKNOWN;
        }
        
        try {
            String projectContent = gatherProjectContent(projectDirectory, buildTool);
            
            if (projectContent.trim().isEmpty()) {
                return ProjectType.Framework.UNKNOWN;
            }
            
            if (COMPOSE_PATTERN.matcher(projectContent).find()) {
                return ProjectType.Framework.COMPOSE_MULTIPLATFORM;
            }
            if (JAVAFX_PATTERN.matcher(projectContent).find()) {
                return ProjectType.Framework.JAVAFX;
            }
            if (LIBGDX_PATTERN.matcher(projectContent).find()) {
                return ProjectType.Framework.LIBGDX;
            }
            if (FXGL_PATTERN.matcher(projectContent).find()) {
                return ProjectType.Framework.FXGL;
            }
            if (SPRING_BOOT_PATTERN.matcher(projectContent).find()) {
                return ProjectType.Framework.SPRING_BOOT;
            }
            if (ANDROID_PATTERN.matcher(projectContent).find()) {
                return ProjectType.Framework.ANDROID;
            }
            
            return ProjectType.Framework.PLAIN_JAVA;
            
        } catch (IOException e) {
            return ProjectType.Framework.UNKNOWN;
        }
    }
    
    private String gatherProjectContent(File projectDirectory, ProjectType.BuildTool buildTool) throws IOException {
        StringBuilder content = new StringBuilder();
        
        switch (buildTool) {
            case MAVEN:
                appendFileContent(content, new File(projectDirectory, "pom.xml"));
                break;
            case GRADLE:
                appendFileContent(content, new File(projectDirectory, "build.gradle"));
                appendFileContent(content, new File(projectDirectory, "build.gradle.kts"));
                appendFileContent(content, new File(projectDirectory, "settings.gradle"));
                appendFileContent(content, new File(projectDirectory, "settings.gradle.kts"));
                break;
            case ANT:
                appendFileContent(content, new File(projectDirectory, "build.xml"));
                break;
        }
        
        appendFileContent(content, new File(projectDirectory, "package.json"));
        
        appendJavaSourceContent(content, new File(projectDirectory, "src"));
        
        return content.toString();
    }
    
    private void appendFileContent(StringBuilder content, File file) throws IOException {
        if (file.exists() && file.isFile()) {
            content.append(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
            content.append("\n");
        }
    }
    
    private void appendJavaSourceContent(StringBuilder content, File srcDir) throws IOException {
        if (!srcDir.exists() || !srcDir.isDirectory()) {
            return;
        }
        
        appendJavaSourceContentRecursive(content, srcDir, 0);
    }
    
    private void appendJavaSourceContentRecursive(StringBuilder content, File dir, int depth) throws IOException {
        if (depth > 3) return;
        
        File[] files = dir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            if (file.isDirectory()) {
                appendJavaSourceContentRecursive(content, file, depth + 1);
            } else if (file.getName().endsWith(".java") || file.getName().endsWith(".kt")) {
                String fileContent = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                if (fileContent.length() > 5000) {
                    fileContent = fileContent.substring(0, 5000);
                }
                content.append(fileContent);
                content.append("\n");
                
                if (content.length() > 50000) {
                    return;
                }
            }
        }
    }
    
    private boolean detectMultiModule(File projectDirectory, ProjectType.BuildTool buildTool) {
        try {
            switch (buildTool) {
                case MAVEN:
                    return detectMavenMultiModule(projectDirectory);
                case GRADLE:
                    return detectGradleMultiModule(projectDirectory);
                case ANT:
                    return detectAntMultiModule(projectDirectory);
                default:
                    return false;
            }
        } catch (IOException e) {
            return false;
        }
    }
    
    private boolean detectMavenMultiModule(File projectDirectory) throws IOException {
        File pomFile = new File(projectDirectory, "pom.xml");
        if (!pomFile.exists()) return false;
        
        String pomContent = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);
        return pomContent.contains("<modules>") || pomContent.contains("<module>");
    }
    
    private boolean detectGradleMultiModule(File projectDirectory) throws IOException {
        File settingsFile = new File(projectDirectory, "settings.gradle");
        File settingsKtsFile = new File(projectDirectory, "settings.gradle.kts");
        
        if (settingsFile.exists()) {
            String content = FileUtils.readFileToString(settingsFile, StandardCharsets.UTF_8);
            if (content.contains("include") && content.contains(":")) {
                return true;
            }
        }
        
        if (settingsKtsFile.exists()) {
            String content = FileUtils.readFileToString(settingsKtsFile, StandardCharsets.UTF_8);
            if (content.contains("include") && content.contains(":")) {
                return true;
            }
        }
        
        return false;
    }
    
    private boolean detectAntMultiModule(File projectDirectory) {
        File[] subdirs = projectDirectory.listFiles(File::isDirectory);
        if (subdirs == null) return false;
        
        int buildXmlCount = 0;
        for (File subdir : subdirs) {
            if (new File(subdir, "build.xml").exists()) {
                buildXmlCount++;
            }
        }
        
        return buildXmlCount > 1;
    }
    
    public boolean isJavaFXProject(File projectDirectory) {
        ProjectType projectType = detectProjectType(projectDirectory);
        return projectType.getFramework() == ProjectType.Framework.JAVAFX;
    }
    
    public boolean isComposeMultiplatformProject(File projectDirectory) {
        ProjectType projectType = detectProjectType(projectDirectory);
        return projectType.getFramework() == ProjectType.Framework.COMPOSE_MULTIPLATFORM;
    }
}