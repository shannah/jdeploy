package ca.weblite.jdeploy.project.service;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.project.model.ProjectDescriptor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Singleton
public class ClassWriter {

    private final JavaClassSourceResolver javaClassSourceResolver;

    private final FileSystemInterface fileSystemInterface;

    @Inject
    public ClassWriter(FileSystemInterface fileSystemInterface, JavaClassSourceResolver javaClassSourceResolver) {
        this.fileSystemInterface = fileSystemInterface;
        this.javaClassSourceResolver = javaClassSourceResolver;
    }
    public void writeClass(
            ProjectDescriptor projectDescriptor,
            String className,
            String packageName,
            String content
    ) throws IOException {
        fileSystemInterface.writeStringToFile(
                javaClassSourceResolver.getSourcePath(projectDescriptor, className, packageName),
                content, StandardCharsets.UTF_8
        );
    }
}
