package ca.weblite.jdeploy.models;

import ca.weblite.jdeploy.io.FileSystemInterface;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class JDeployProject {
    private Path packageJSONFile;
    private JSONObject packageJSON;

    public JDeployProject(Path packageJSONFile, JSONObject packageJSON) {
        this.packageJSONFile = packageJSONFile;
        this.packageJSON = packageJSON;
    }

    public Path getPackageJSONFile() {
        return packageJSONFile;
    }

    public JSONObject getPackageJSON() {
        return packageJSON;
    }
}
