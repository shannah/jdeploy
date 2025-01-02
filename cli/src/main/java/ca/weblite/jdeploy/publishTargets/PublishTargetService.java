package ca.weblite.jdeploy.publishTargets;

import ca.weblite.jdeploy.io.FileSystemInterface;
import ca.weblite.tools.io.IOUtil;
import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PublishTargetService implements PublishTargetServiceInterface {

    private final PublishTargetSerializer serializer;

    private final FileSystemInterface fileSystem;

    @Inject
    public PublishTargetService(PublishTargetSerializer serializer, FileSystemInterface fileSystem) {
        this.serializer = serializer;
        this.fileSystem = fileSystem;
    }

    @Override
    public List<PublishTargetInterface> getTargetsForProject(String projectPath) throws IOException {
        if (!fileSystem.exists(getPackageJsonPathForProject(projectPath))) {
            return new ArrayList<PublishTargetInterface>();
        }
        String packageJsonContents = IOUtil.readToString(fileSystem.getInputStream(getPackageJsonPathForProject(projectPath)));
        return getTargetsForPackageJson(new JSONObject(packageJsonContents));
    }

    @Override
    public List<PublishTargetInterface> getTargetsForPackageJson(JSONObject packageJson) {
        return serializer.deserialize(getPublishTargets(packageJson));
    }

    @Override
    public void updatePublishTargetsForProject(String projectPath, List<PublishTargetInterface> targets) throws IOException {
        if (!fileSystem.exists(getPackageJsonPathForProject(projectPath))) {
            throw new FileNotFoundException("package.json does not exist in project path: " + projectPath);
        }
        String packageJsonContents = IOUtil.readToString(fileSystem.getInputStream(getPackageJsonPathForProject(projectPath)));
        JSONObject packageJson = new JSONObject(packageJsonContents);
        updatePublishTargetsForPackageJson(packageJson, targets);
        IOUtils.write(
                packageJson.toString(2),
                fileSystem.getOutputStream(getPackageJsonPathForProject(projectPath)),
                StandardCharsets.UTF_8
        );
    }

    @Override
    public void updatePublishTargetsForPackageJson(JSONObject packageJson, List<PublishTargetInterface> targets) {
        setPublishTargets(packageJson, serializer.serialize(targets));
    }

    private JSONObject getJdeployConfig(JSONObject packageJson) {
        if (!packageJson.has("jdeploy")) {
            packageJson.put("jdeploy", new JSONObject());
        }
        return packageJson.getJSONObject("jdeploy");
    }

    private Path getPackageJsonPathForProject(String projectPath) {
        return Paths.get(projectPath, "package.json");
    }

    private JSONArray getPublishTargets(JSONObject packageJson) {
        JSONObject jdeploy = getJdeployConfig(packageJson);
        if (!jdeploy.has("publishTargets")) {
            jdeploy.put("publishTargets", new JSONArray());
        }
        return jdeploy.getJSONArray("publishTargets");
    }

    private void setPublishTargets(JSONObject packageJson, JSONArray targets) {
        JSONObject jdeploy = getJdeployConfig(packageJson);
        jdeploy.put("publishTargets", targets);
    }
}
