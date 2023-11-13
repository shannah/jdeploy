package ca.weblite.jdeploy.helpers.filemergers;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ProjectDirectoryExtensionMerger extends DirectoryMerger {

    @Inject
    public ProjectDirectoryExtensionMerger(
            PackageJsonFileMerger packageJsonFileMerger,
            PomFileMerger pomFileMerger
    ) {
        super(new PackageJsonFileMerger(), new PomFileMerger());
    }
}
