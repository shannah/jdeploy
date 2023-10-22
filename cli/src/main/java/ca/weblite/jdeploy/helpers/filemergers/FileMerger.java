package ca.weblite.jdeploy.helpers.filemergers;

import java.io.File;
import java.io.IOException;

public interface FileMerger {
    public void merge(File base, File patch) throws Exception;
    public boolean isApplicableTo(File base, File patch);
}
