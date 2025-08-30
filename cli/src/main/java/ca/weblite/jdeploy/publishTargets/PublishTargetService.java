package ca.weblite.jdeploy.publishTargets;

import ca.weblite.jdeploy.factories.PublishTargetFactory;
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

    private final PublishTargetFactory publishTargetFactory;

    @Inject
    public PublishTargetService(
            PublishTargetSerializer serializer,
            FileSystemInterface fileSystem,
            PublishTargetFactory publishTargetFactory
    ) {
        this.serializer = serializer;
        this.fileSystem = fileSystem;
        this.publishTargetFactory = publishTargetFactory;
    }

    @Override
    public List<PublishTargetInterface> getTargetsForProject(String projectPath, boolean includeDefaultTarget) throws IOException {
        if (!fileSystem.exists(getPackageJsonPathForProject(projectPath))) {
            return new ArrayList<PublishTargetInterface>();
        }
        String packageJsonContents = IOUtil.readToString(fileSystem.getInputStream(getPackageJsonPathForProject(projectPath)));
        return getTargetsForPackageJson(new JSONObject(packageJsonContents), includeDefaultTarget);
    }

    @Override
    public List<PublishTargetInterface> getTargetsForPackageJson(JSONObject packageJson, boolean includeDefaultTarget) {
        return serializer.deserialize(getPublishTargets(packageJson, includeDefaultTarget));
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
        List<PublishTargetInterface> explicitTargets = targets.stream()
                .filter(target -> !target.isDefault())
                .collect(java.util.stream.Collectors.toList());
        setPublishTargets(packageJson, serializer.serialize(explicitTargets));
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

    private JSONArray getPublishTargets(JSONObject packageJson, boolean includeDefaultTarget) {
        JSONObject jdeploy = getJdeployConfig(packageJson);
        if (!jdeploy.has("publishTargets")) {
            jdeploy.put("publishTargets", new JSONArray());
        }
        JSONArray targets = jdeploy.getJSONArray("publishTargets");
        if (includeDefaultTarget && targets.isEmpty()) {
            PublishTargetInterface defaultTarget = publishTargetFactory
                    .createWithUrlAndName(
                            packageJson.getString("name"),
                            packageJson.getString("name"),
                            true
                    );

            targets.put(serializer.serialize(defaultTarget));
        }

        return targets;
    }

    private void setPublishTargets(JSONObject packageJson, JSONArray targets) {
        JSONObject jdeploy = getJdeployConfig(packageJson);
        jdeploy.put("publishTargets", targets);
    }
}
