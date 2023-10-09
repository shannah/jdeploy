package ca.weblite.jdeploy.services;

import java.io.File;


import ca.weblite.jdeploy.helpers.StringUtils;
import org.apache.commons.io.FileUtils;

public class ProjectGenerator {

    private StringUtils stringUtils = new StringUtils();
    private File parentDirectory;
    private String projectName;
    private String appTitle;
    private String templateDirectory;


    private String templateName;
    private String groupId;
    private String artifactId;

    private String packageName;

    private String mainClassName;


    public static class Params {

        private String magicArg;

        private File parentDirectory;
        private String projectName;
        private String appTitle;
        private String templateDirectory;

        private String templateName;
        private String groupId;
        private String artifactId;

        private String packageName;

        private String mainClassName;


        public Params setTemplateName(String templateName) {
            this.templateName = templateName;

            return this;
        }

        public Params setParentDirectory(File parentDirectory) {
            this.parentDirectory = parentDirectory;
            return this;
        }

        public Params setProjectName(String projectName) {
            this.projectName = projectName;
            return this;
        }

        private String getProjectName() {
            if (projectName != null) {
                return projectName;
            }
            StringUtils stringUtils = new StringUtils();
            if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
                if (stringUtils.countCharInstances(magicArg, '.') > 2) {
                    String pkg = splitPackageIntoPackageAndClassName(magicArg)[0];
                    return stringUtils.camelCaseToLowerCaseWithSeparator(
                            pkg.substring(pkg.lastIndexOf(".")+1),
                            "-"
                    );
                }
                return  stringUtils.camelCaseToLowerCaseWithSeparator(
                        splitPackageIntoPackageAndClassName(magicArg)[1],
                        "-"
                );
            }

            if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
                if (magicArg.contains(".")) {
                    return stringUtils.camelCaseToLowerCaseWithSeparator(magicArg.substring(magicArg.lastIndexOf(".")+1), "-");
                }
                return stringUtils.camelCaseToLowerCaseWithSeparator(magicArg, "-");
            }

            return "my-app";
        }

        public Params setAppTitle(String appTitle) {
            this.appTitle = appTitle;
            return this;
        }

        private String getAppTitle() {
            if (appTitle != null) {
                return appTitle;
            }

            StringUtils stringUtils = new StringUtils();
            if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
                return stringUtils.ucWords(
                        stringUtils.camelCaseToLowerCaseWithSeparator(
                                splitPackageIntoPackageAndClassName(magicArg)[1],
                                " "
                        )
                );
            }

            if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
                if (magicArg.contains(".")) {
                    return stringUtils.ucWords(
                            stringUtils.camelCaseToLowerCaseWithSeparator(
                                    magicArg.substring(magicArg.lastIndexOf(".")+1),
                                    " "
                            )
                    );
                }
                return stringUtils.camelCaseToLowerCaseWithSeparator(magicArg, " ");
            }

            return "My App";
        }

        public Params setTemplateDirectory(String templateDirectory) {
            this.templateDirectory = templateDirectory;
            return this;
        }

        public Params setGroupId(String groupId) {
            this.groupId = groupId;
            return this;
        }

        private String getGroupId() {
            if (groupId != null) {
                return groupId;
            }
            StringUtils stringUtils = new StringUtils();
            if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {

                String out =  splitPackageIntoPackageAndClassName(magicArg)[0];
                if (stringUtils.countCharInstances(out, '.') > 1) {
                    out = out.substring(0, out.lastIndexOf("."));
                }

                return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
            }

            if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
                String out =  magicArg;
                if (stringUtils.countCharInstances(out, '.') > 1) {
                    out = out.substring(0, out.lastIndexOf("."));
                }

                return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
            }

            return "com.example";
        }

        public Params setArtifactId(String artifactId) {
            this.artifactId = artifactId;
            return this;
        }

        private String getArtifactId() {
            if (artifactId != null) {
                return artifactId;
            }
            StringUtils stringUtils = new StringUtils();
            if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
                String pkg = splitPackageIntoPackageAndClassName(magicArg)[0];
                String out;
                if (stringUtils.countCharInstances(pkg, '.') > 1) {
                    out = pkg.substring(pkg.lastIndexOf(".")+1);
                } else {
                    out = splitPackageIntoPackageAndClassName(magicArg)[1];
                }

                return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
            }

            if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
                String pkg = magicArg;
                String out;
                if (stringUtils.countCharInstances(pkg, '.') > 0) {
                    out = pkg.substring(pkg.lastIndexOf(".")+1);

                } else {
                    out = magicArg;
                }

                return stringUtils.camelCaseToLowerCaseWithSeparator(out, "-");
            }

            if (projectName != null) {
                return stringUtils.camelCaseToLowerCaseWithSeparator(projectName, "-");
            }

            return "my-app";
        }

        private String getMainClassName() {
            if (mainClassName != null) {
                return mainClassName;
            }
            StringUtils stringUtils = new StringUtils();
            if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
                return splitPackageIntoPackageAndClassName(magicArg)[1];
            }

            return "Main";
        }

        private String getPackageName() {
            if (packageName != null) {
                return packageName;
            }
            StringUtils stringUtils = new StringUtils();
            if (magicArg != null && isPackageAndClassName(stringUtils, magicArg)) {
                return splitPackageIntoPackageAndClassName(magicArg)[0];
            }

            if (magicArg != null && stringUtils.isValidJavaClassName(magicArg)) {
                return magicArg;
            }

            if (groupId != null) {
                String pkg = stringUtils.lowerCaseWithSeparatorToCamelCase(groupId, "-");
                if (artifactId != null) {
                    pkg += "." + stringUtils.lowerCaseWithSeparatorToCamelCase(artifactId, "-");
                }
                return pkg;
            }

            if (artifactId != null) {
                return "com.example." + stringUtils.lowerCaseWithSeparatorToCamelCase(artifactId, "-");
            }

            return "com.example.myapp";
        }

        public ProjectGenerator build() {
            return new ProjectGenerator(this);
        }

        public String getMagicArg() {
            return magicArg;
        }

        public Params setMagicArg(String magicArg) {
            this.magicArg = magicArg;
            return this;
        }


    }

    public ProjectGenerator(Params params) {
        this.parentDirectory = params.parentDirectory;
        this.projectName = params.getProjectName();
        this.appTitle = params.getAppTitle();
        this.templateDirectory = params.templateDirectory;
        this.templateName = params.templateName;
        this.groupId = params.getGroupId();
        this.artifactId = params.getArtifactId();
        this.mainClassName = params.getMainClassName();
        this.packageName = params.getPackageName();
    }

    public File generate() throws Exception {
        File projectDir = new File(parentDirectory, stringUtils.camelCaseToLowerCaseWithSeparator(projectName, "-"));
        if ( projectDir.exists() ) {
            throw new Exception("Project directory already exists: " + projectDir.getAbsolutePath());
        }
        projectDir.mkdirs();
        if (templateDirectory == null && templateName != null) {
            ProjectTemplateCatalog catalog = new ProjectTemplateCatalog();
            catalog.update();
            templateDirectory = catalog.getTemplate(templateName).getAbsolutePath();
        }
        if (templateDirectory == null) {
            throw new Exception("Template directory is not set");
        }
        File templateDir = new File(templateDirectory);
        if ( !templateDir.exists() ) {
            throw new Exception("Template directory does not exist: " + templateDir.getAbsolutePath());
        }
        File[] files = templateDir.listFiles();
        for ( File file : files) {
            if ( file.isDirectory() ) {
                FileUtils.copyDirectory(file, new File(projectDir, file.getName()));
            } else {
                FileUtils.copyFileToDirectory(file, projectDir);
            }
        }
        updateFilesInDirectory(projectDir);
        return projectDir;
    }

    private String updateApplicationProperties(String buildFileSource) throws Exception {
        buildFileSource = buildFileSource.replace("{{ appName }}", projectName);
        buildFileSource = buildFileSource.replace("{{ appTitle }}", appTitle);
        buildFileSource = buildFileSource.replace("{{ groupId }}", groupId);
        buildFileSource = buildFileSource.replace("{{ artifactId }}", artifactId);
        buildFileSource = buildFileSource.replace("{{ mainClass }}", mainClassName);
        buildFileSource = buildFileSource.replace("{{ packageName }}", packageName);
        buildFileSource = buildFileSource.replace("{{ packagePath }}", packageName.replace(".", "/"));

        return buildFileSource;
    }

    private File updateFilesInDirectory(File dir) throws Exception {
        File[] files = dir.listFiles();
        for ( File file : files) {
            if ( file.isDirectory() ) {
                updateFilesInDirectory(file);
            } else {
                String name = file.getName();
                if ( name.endsWith(".java") || name.endsWith(".properties") || name.endsWith(".xml") || name.endsWith(".gradle") || name.endsWith(".json") ) {
                    String source = FileUtils.readFileToString(file);
                    String updated = updateApplicationProperties(source);
                    if ( !updated.equals(source) ) {
                        FileUtils.writeStringToFile(file, updated);
                    }
                }
            }
            moveFile(file);
        }
        return dir;
    }


    private File moveFile(File file) throws Exception {
        String name = updateApplicationProperties(file.getName());
        if (!file.getName().equals(name)) {
            File destFile = new File(file.getParentFile(), name);
            if (file.isDirectory()) {
                FileUtils.moveDirectory(file, destFile);
            } else {
                FileUtils.moveFile(file, destFile);
            }

            return destFile;
        }

        return file;
    }

    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    private static String[] splitPackageIntoPackageAndClassName(String packageName) {
        String[] parts = packageName.split("\\.");
        String className = parts[parts.length - 1];
        String[] packageParts = new String[parts.length - 1];
        System.arraycopy(parts, 0, packageParts, 0, parts.length - 1);
        return new String[]{String.join(".", packageParts), className};
    }

    private static boolean isPackageAndClassName(StringUtils stringUtils, String packageName) {
        if (!stringUtils.isValidJavaClassName(packageName)) {
            return false;
        }

        String[] parts = packageName.split("\\.");
        if (parts.length < 2) {
            return false;
        }

        if (parts[parts.length - 1].isEmpty()) {
            return false;
        }

        return (Character.isUpperCase(parts[parts.length - 1].charAt(0)));
    }
}
