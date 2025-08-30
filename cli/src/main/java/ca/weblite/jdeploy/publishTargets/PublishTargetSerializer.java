package ca.weblite.jdeploy.publishTargets;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PublishTargetSerializer {
    public JSONObject serialize(PublishTargetInterface target) {
        JSONObject obj = new JSONObject();
        obj.put("name", target.getName());
        obj.put("type", target.getType().name());
        obj.put("url", target.getUrl());
        if (target.isDefault()) {
            obj.put("isDefault", true);
        }
        return obj;
    }

    public JSONArray serialize(List<PublishTargetInterface> targets) {
        JSONArray arr = new JSONArray();
        for (PublishTargetInterface target : targets) {
            arr.put(serialize(target));
        }
        return arr;
    }

    public PublishTargetInterface deserialize(JSONObject obj) {
        String name = obj.getString("name");
        PublishTargetType type = PublishTargetType.valueOf(obj.getString("type"));
        String url = obj.getString("url");
        boolean isDefault = obj.optBoolean("isDefault", false);
        return new PublishTarget(name, type, url, isDefault);
    }

    public List<PublishTargetInterface> deserialize(JSONArray arr) {
        List<PublishTargetInterface> targets = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            targets.add(deserialize(arr.getJSONObject(i)));
        }
        return targets;
    }
}
