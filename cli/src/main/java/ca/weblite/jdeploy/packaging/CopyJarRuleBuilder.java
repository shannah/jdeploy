package ca.weblite.jdeploy.packaging;

import ca.weblite.tools.io.FileUtil;

import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class CopyJarRuleBuilder {
    private final ClassPathFinder classPathFinder;

    public CopyJarRuleBuilder(ClassPathFinder classPathFinder) {
        this.classPathFinder = classPathFinder;
    }

    public java.util.List<CopyRule> build(
            PackagingContext context,
            File jarFile
    ) throws IOException {
        java.util.List<CopyRule> includes = new ArrayList<CopyRule>();
        String parentPath = jarFile.getParentFile() != null ? jarFile.getParentFile().getPath() : ".";
        List<String> mavenDependencies = (List<String>) context.getList("mavenDependencies", true);
        boolean useMavenDependencies =!mavenDependencies.isEmpty();
        boolean serverProvidedJavaFX = "true".equals(context.getString("javafx", "false"));
        boolean stripJavaFXFilesFlag = "true".equals(context.getString("stripJavaFXFiles", "true"));
        boolean javafxVersionProvided = !context.getString("javafxVersion", "").isEmpty();
        boolean stripFXFiles = stripJavaFXFilesFlag && (serverProvidedJavaFX || javafxVersionProvided);

        includes.add(new CopyRule(context, parentPath, jarFile.getName(), null, true));
        for (String path : classPathFinder.findClassPath(jarFile)) {
            File f = new File(path);

            if (useMavenDependencies) {
                // If we are using maven dependencies, then we won't
                // We add this stripped file placeholder to mark that it was stripped.
                // This also ensures that the parent directory will be included in distribution.

                if (f.getName().endsWith(".jar")) {
                    File strippedFile = f.isAbsolute()
                        ? new File(path + ".stripped")
                            : new File(parentPath,path + ".stripped");
                    if (strippedFile.getParentFile().exists()) {
                        FileUtil.writeStringToFile("", strippedFile);
                        includes.add(new CopyRule(context, parentPath, path + ".stripped", null, true));
                    }
                    continue;
                }
            }
            if (stripFXFiles && f.getName().startsWith("javafx-") && f.getName().endsWith(".jar")) {
                continue;
            }
            includes.add(new CopyRule(context, parentPath, path, null, true));
        }

        return includes;
    }
}
