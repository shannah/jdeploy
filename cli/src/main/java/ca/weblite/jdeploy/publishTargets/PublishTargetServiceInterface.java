package ca.weblite.jdeploy.publishTargets;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

public interface PublishTargetServiceInterface {
    List<PublishTargetInterface> getTargetsForProject(String projectPath) throws IOException;
    List<PublishTargetInterface> getTargetsForPackageJson(JSONObject packageJson);

    void updatePublishTargetsForProject(String projectPath, List<PublishTargetInterface> targets) throws IOException;
    void updatePublishTargetsForPackageJson(JSONObject packageJson, List<PublishTargetInterface> targets);

}
