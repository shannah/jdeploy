package ca.weblite.jdeploy.project.service;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.project.model.ProjectDescriptor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Singleton
public class ClassReader {

    private final JavaClassSourceResolver javaClassSourceResolver;

    private final FileSystemInterface fileSystemInterface;

    @Inject
    public ClassReader(FileSystemInterface fileSystemInterface, JavaClassSourceResolver javaClassSourceResolver) {
        this.fileSystemInterface = fileSystemInterface;
        this.javaClassSourceResolver = javaClassSourceResolver;
    }
    public String readClass(
            ProjectDescriptor projectDescriptor,
            String className,
            String packageName
    ) throws IOException {
        return fileSystemInterface.readToString(
                javaClassSourceResolver.getSourcePath(projectDescriptor, className, packageName),
                StandardCharsets.UTF_8
        );
    }
}
