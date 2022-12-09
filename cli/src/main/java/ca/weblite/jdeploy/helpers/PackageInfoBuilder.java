package ca.weblite.jdeploy.helpers;

import ca.weblite.tools.io.IOUtil;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * A class for generating an npm-compatible package-info.json file
 */
public class PackageInfoBuilder {
    private JSONObject json = new JSONObject();
    private String namePrefix = "";

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public PackageInfoBuilder load(InputStream inputStream) throws IOException {
        json = new JSONObject(IOUtil.readToString(inputStream));

        return this;
    }

    public PackageInfoBuilder addVersion(String version, InputStream inputStream) throws IOException {
        if (!json.has("versions")) {
            json.put("versions", new JSONObject());
            setCreatedTime();
        }
        setVersionTimestamp(version);
        json.getJSONObject("versions").put(version, new JSONObject(IOUtil.readToString(inputStream)));
        setVersionTimestamp(version);
        setModifiedTime();

        String name = getVersion(version).getString("name");
        if (!namePrefix.isEmpty() && !name.startsWith(namePrefix)) {
            name = namePrefix + name;
            getVersion(version).put("name", name);
        }
        JSONObject jdeployObject = getVersion(version).getJSONObject("jdeploy");
        if (jdeployObject != null) {
            String commitHash = jdeployObject.getString("commitHash");
            if (commitHash != null && !commitHash.isEmpty()) {
                if (!json.has("commit-hashes")) {
                    json.put("commit-hashes", new JSONObject());
                }
                json.getJSONObject("commit-hashes").put(version, commitHash);
            }
        }

        json.put("name", name);
        json.put("_id", name);



        return this;
    }

    public PackageInfoBuilder addDistTag(String name, String value) {
        if (!json.has("dist-tags")) {
            json.put("dist-tags", new JSONObject());
        }
        json.getJSONObject("dist-tags").put(name, value);

        return this;
    }

    public PackageInfoBuilder setVersionTimestamp(String version) {
        if (!json.has("time")) {
            json.put("time", new JSONObject());
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        json.getJSONObject("time").put(version, fmt.format(new Date()));

        return this;
    }

    public PackageInfoBuilder setSource(String source) {
        json.put("source", source);
        return this;
    }

    public PackageInfoBuilder setModifiedTime() {
        setVersionTimestamp("modified");
        return this;
    }

    public PackageInfoBuilder setCreatedTime() {
        setVersionTimestamp("created");
        return this;
    }

    public PackageInfoBuilder setLatestVersion(String version) {
        addDistTag("latest", version);
        return this;
    }

    public JSONObject getVersion(String version) {
        return json.getJSONObject("versions").getJSONObject(version);
    }

    public PackageInfoBuilder save(OutputStream outputStream) throws IOException {
        outputStream.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
        return this;
    }

}
