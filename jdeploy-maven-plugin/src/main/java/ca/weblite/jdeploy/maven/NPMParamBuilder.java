package ca.weblite.jdeploy.maven;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NPMParamBuilder {

    private final JDeployClient client;

    public NPMParamBuilder(JDeployClient client) {
        this.client = client;
    }

    private class NPMParam {
        String key;
        String value;

        boolean json;

        NPMParam(String key, String value) {
            this(key, value, false);
        }

        NPMParam(String key, boolean value) {
            this(key, String.valueOf(value), true);
        }

        NPMParam(String key, int value) {
            this(key, String.valueOf(value), true);
        }

        NPMParam(String key, String value, boolean json) {
            this.key = key;
            this.value = value;
            this.json = json;
        }
    }

    private final List<NPMParam> paramList = new ArrayList<>();

    public NPMParamBuilder add(String key, String value) {
        paramList.add(new NPMParam(key, value));
        return this;
    }

    public NPMParamBuilder add(String key, int value) {
        paramList.add(new NPMParam(key, value));
        return this;
    }

    public NPMParamBuilder add(String key, boolean value) {
        paramList.add(new NPMParam(key, value));
        return this;
    }

    public void apply() throws IOException {
        for (NPMParam param : paramList) {
            try {
                client.execNpm(argsFor(param));
            } catch (IOException ex) {
                throw new IOException("Failed to set npm parameter " + param.key + " to value " + param.value, ex);
            }
        }
    }

    private String[] argsFor(NPMParam param) {
        if (param.json) {
            return new String[]{"pkg", "set", param.key+"="+param.value, "--json"};
        } else {
            return new String[]{"pkg", "set", param.key+"="+param.value};
        }
    }
}
