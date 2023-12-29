package ca.weblite.jdeploy.project.service;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.jdeploy.project.model.ProjectDescriptor;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Path;

@Singleton
public class JavaClassSourceResolver {

    private final FileSystemInterface fileSystemInterface;

    @Inject
    public JavaClassSourceResolver(FileSystemInterface fileSystemInterface) {
        this.fileSystemInterface = fileSystemInterface;
    }

    public Path getSourcePath(ProjectDescriptor projectDescriptor, String className, String packageName) {
        Path sourceRootPath = projectDescriptor.getSourceRootPath();
        Path packagePath = sourceRootPath.resolve(packageName.replace(".", "/"));
        Path classPath = packagePath.resolve(className + ".java");
        return classPath;
    }
}
