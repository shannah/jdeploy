package ca.weblite.jdeploy.helpers.filemergers;

import javax.inject.Singleton;
import java.io.File;

@Singleton
public class PackageJsonFileMerger extends JSONFileMerger {
    @Override
    public boolean isApplicableTo(File base, File patch) {
        return base.getName().equals("package.json")
                && patch.getName().equals(base.getName())
                && base.isFile()
                && patch.isFile();
    }
}
