package ca.weblite.jdeploy.cheerpj.services;

import ca.weblite.tools.io.FileUtil;
import ca.weblite.tools.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

public class BuildCheerpjAppService {

    public static class Params {
        private File iconFile;
        private String appName;
        private File appJar;
        private File outputDir;

        private String cheerpjLoader = "https://cjrtnc.leaningtech.com/3.0rc2/cj3loader.js";

        public File getOutputDir() {
            return outputDir;
        }

        public Params setOutputDir(File outputDir) {
            this.outputDir = outputDir;
            return this;
        }

        public File getAppJar() {
            return appJar;
        }

        public Params setAppJar(File appJar) {
            this.appJar = appJar;
            return this;
        }

        public String getAppName() {
            return appName;
        }

        public Params setAppName(String appName) {
            this.appName = appName;
            return this;
        }

        public File getIconFile() {
            return iconFile;
        }

        public Params setIconFile(File iconFile) {
            this.iconFile = iconFile;
            return this;
        }

        public Params setCheerpjLoader(String cheerpjLoader) {
            this.cheerpjLoader = cheerpjLoader;
            return this;
        }

        public String getCheerpjLoader() {
            return cheerpjLoader;
        }
    }
    public void build(Params params) throws IOException {
        String mainClass = extractMainClass(params.getAppJar());
        String classPath = extractClassPath(params.getAppJar()) + ":jdeploy-cheerpj.jar";
        String indexHtml = IOUtil.readToString(
                this.getClass().getResourceAsStream("/ca/weblite/jdeploy/cheerpj/index.html")
        );

        indexHtml = indexHtml.replace("{{ cheerpjLoader }}", params.getCheerpjLoader());
        indexHtml = indexHtml.replace("{{ mainClass }}", mainClass);
        indexHtml = indexHtml.replace("{{ classPath }}", classPath);
        indexHtml = indexHtml.replace("{{ appName }}", params.getAppName());
        indexHtml = indexHtml.replace("{{ title }}", params.getAppName());

        FileUtil.writeStringToFile(indexHtml, new File(params.getOutputDir(), "index.html"));
        copyJarToOutputDirectoryWithDependencies(params.getAppJar(), params.getOutputDir());
        IOUtil.copyResourceToFile(
                getClass(),
                "/ca/weblite/jdeploy/cheerpj/jdeploy-cheerpj.jar",
                new File(params.getOutputDir(), "jdeploy-cheerpj.jar")
        );

    }

    private static void copyJarToOutputDirectoryWithDependencies(File jar, File outputDir) throws IOException {
        // Copy the jar file to the output directory
        Files.copy(jar.toPath(), Paths.get(outputDir.getAbsolutePath(), jar.getName()), StandardCopyOption.REPLACE_EXISTING);

        try (JarFile jarFile = new JarFile(jar)) {
            // Get the manifest file from the jar
            Manifest manifest = jarFile.getManifest();

            // Get the Class-Path attribute from the manifest file
            String classPath = manifest.getMainAttributes().getValue("Class-Path");

            if (classPath != null) {
                // Split the classPath into individual paths
                String[] classPathEntries = classPath.split(" ");

                // Iterate over each path and copy the files to the output directory
                for (String entry : classPathEntries) {
                    // Resolve the dependency path relative to the original jar file's parent directory
                    File dependency = new File(jar.getParent(), entry);
                    if (dependency.exists() && dependency.isFile() && dependency.getName().endsWith(".jar")) {
                        Path targetPath = Paths.get(outputDir.getAbsolutePath(), entry);
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(dependency.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /**
     * Extracts the Main Class from the Manifest of the provided executable JAR file.
     *
     * @param jarFile the JAR file
     * @return the name of the Main Class, or null if not found
     * @throws IOException if an I/O error has occurred
     */
    private static String extractMainClass(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                return attributes.getValue(Attributes.Name.MAIN_CLASS);
            }
        }
        return null;
    }

    /**
     * Extracts the dependent JAR files from the Manifest of the provided executable JAR file.
     *
     * @param jarFile the JAR file
     * @return a list of dependent JAR files
     * @throws IOException if an I/O error has occurred
     */
    private static List<File> extractDependentJars(File jarFile) throws IOException {
        List<File> dependentJars = new ArrayList<>();
        try (JarFile jar = new JarFile(jarFile)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                Attributes attributes = manifest.getMainAttributes();
                String classPath = attributes.getValue(Attributes.Name.CLASS_PATH);
                if (classPath != null) {
                    String[] classPathEntries = classPath.split(" ");
                    for (String entry : classPathEntries) {
                        URL url = new URL(entry);
                        dependentJars.add(new File(url.getFile()));
                    }
                }
            }
        }
        return dependentJars;
    }

    private static String extractClassPath(File jarFilePath) throws IOException {
        try (JarFile jarFile = new JarFile(jarFilePath)) {
            Attributes attributes = jarFile.getManifest().getMainAttributes();
            String classPath = attributes.getValue(Attributes.Name.CLASS_PATH);
            if (classPath != null) {
                // Replace spaces with colons
                classPath = classPath.replace(" ", ":");
                StringBuilder sb = new StringBuilder();
                for (String entry : classPath.split(":")) {

                    if (!entry.endsWith(".jar")) {
                        continue;
                    }
                    if (sb.length() > 0) {
                        sb.append(":");
                    }
                    sb.append(entry);

                }
                if (sb.length() > 0) {
                    return jarFilePath.getName() + ":" + sb.toString();
                } else {
                    return jarFilePath.getName();
                }
            } else {
                return jarFilePath.getName();
            }
        }
    }

}
