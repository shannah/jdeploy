package ca.weblite.jdeploy.installer.npm;

import ca.weblite.jdeploy.ai.models.AiIntegrationConfig;
import ca.weblite.jdeploy.helpers.NPMApplicationHelper;
import ca.weblite.jdeploy.models.CommandSpec;
import ca.weblite.jdeploy.models.CommandSpecParser;
import ca.weblite.jdeploy.models.DocumentTypeAssociation;
import ca.weblite.jdeploy.models.HelperAction;
import ca.weblite.jdeploy.models.NPMApplication;
import ca.weblite.jdeploy.app.permissions.PermissionRequest;
import ca.weblite.jdeploy.app.permissions.PermissionRequestService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NPMPackageVersion {
    private static final String DEFAULT_JAVA_VERSION = "11";
    private final NPMPackage npmPackage;
    private final String version;
    private final JSONObject packageJson;

    NPMPackageVersion(NPMPackage pkg, String version, JSONObject packageJson) {
        this.npmPackage = pkg;
        this.version = version;
        this.packageJson = packageJson;
    }


    public NPMApplication toNPMApplication() {
        return NPMApplicationHelper.createFromPackageJSON(packageJson);
    }

    public NPMPackage getNpmPackage() {
        return npmPackage;
    }

    public String getDescription() {
        return packageJson.getString("description");
    }

    public String getVersion() {
        return version;
    }

    public String getJavaVersion() {
        if (jdeploy().has("javaVersion")) {
            return jdeploy().getString("javaVersion");
        }
        return DEFAULT_JAVA_VERSION;
    }

    private JSONObject jdeploy() {
        return packageJson.getJSONObject("jdeploy");
    }

    public Iterable<DocumentTypeAssociation> getDocumentTypeAssociations() {
        ArrayList<DocumentTypeAssociation> out = new ArrayList<>();
        if (jdeploy().has("documentTypes")) {
            JSONArray documentTypes = jdeploy().getJSONArray("documentTypes");
            int len = documentTypes.length();
            for (int i=0; i<len; i++) {
                JSONObject docType = documentTypes.getJSONObject(i);

                // Check if this is a directory association
                if (docType.has("type") && "directory".equalsIgnoreCase(docType.getString("type"))) {
                    String role = docType.has("role") ? docType.getString("role") : null;
                    String description = docType.has("description") ? docType.getString("description") : null;
                    String icon = docType.has("icon") ? docType.getString("icon") : null;
                    DocumentTypeAssociation dirAssoc = new DocumentTypeAssociation(
                            role,
                            description,
                            icon
                    );
                    if (dirAssoc.isValid()) {
                        out.add(dirAssoc);
                    }
                    continue;
                }

                // Handle file associations
                String ext = docType.has("extension") ? docType.getString("extension") : null;
                if (ext == null) continue;
                String mimetype = docType.has("mimetype") ? docType.getString("mimetype") : null;
                if (mimetype == null) continue;
                String icon = docType.has("icon") ? docType.getString("icon") : null;
                boolean editor = docType.has("editor") && docType.getBoolean("editor");
                DocumentTypeAssociation docTypeObj = new DocumentTypeAssociation(
                        ext,
                        mimetype,
                        icon,
                        editor
                );
                out.add(docTypeObj);
            }
        }
        return out;
    }

    public Iterable<String> getUrlSchemes() {
        ArrayList<String> out = new ArrayList<>();
        if (jdeploy().has("urlSchemes")) {
            JSONArray schemes = jdeploy().getJSONArray("urlSchemes");
            int len = schemes.length();
            for (int i=0; i<len; i++) {
                String scheme = schemes.getString(i);
                out.add(scheme);
            }
        }
        return out;
    }

    public String getInstallerTheme() {
        if (jdeploy().has("installerTheme")) {
            return jdeploy().getString("installerTheme");
        }
        return null;
    }

    public Map<PermissionRequest, String> getPermissionRequests() {
        return new PermissionRequestService().getPermissionRequests(packageJson);
    }

    public String getMainClass() {
        if (jdeploy().has("mainClass")) {
            return jdeploy().getString("mainClass");
        }
        return null;
    }

    public String getWmClassName() {
        if (jdeploy().has("linux") && jdeploy().getJSONObject("linux").has("wmClassName")) {
            return jdeploy().getJSONObject("linux").getString("wmClassName");
        }
        return null;
    }

    public RunAsAdministratorSettings getRunAsAdministratorSettings() {
        if (jdeploy().has("runAsAdministrator")) {
            String str = jdeploy().getString("runAsAdministrator");
            if ("required".equalsIgnoreCase(str)) {
                return RunAsAdministratorSettings.Required;
            } else if ("allowed".equalsIgnoreCase(str)) {
                return RunAsAdministratorSettings.Allowed;
            } else {
                return RunAsAdministratorSettings.Disabled;
            }
        }
        return RunAsAdministratorSettings.Disabled;
    }

    /**
     * Gets the command name from package.json.
     * Priority:
     * 1. jdeploy.command property
     * 2. bin object key that maps to "jdeploy-bundle/jdeploy.js"
     * 3. name property
     *
     * @return the command name, or null if not found
     */
    public String getCommandName() {
        // 1. Check jdeploy.command
        if (jdeploy().has("command")) {
            return jdeploy().getString("command");
        }

        // 2. Check bin object for key that maps to "jdeploy-bundle/jdeploy.js"
        if (packageJson.has("bin")) {
            JSONObject bin = packageJson.getJSONObject("bin");
            for (String key : bin.keySet()) {
                String value = bin.getString(key);
                if ("jdeploy-bundle/jdeploy.js".equals(value)) {
                    return key;
                }
            }
        }

        // 3. Use name property
        if (packageJson.has("name")) {
            return packageJson.getString("name");
        }

        return null;
    }

    /**
     * Parses and returns the list of CommandSpec objects declared in package.json under jdeploy.commands.
     * The returned list is sorted by command name to ensure deterministic ordering.
     *
     * @return list of CommandSpec (empty list if none configured)
     * @throws IllegalArgumentException if invalid command entries are encountered
     */
    public List<CommandSpec> getCommands() {
        return CommandSpecParser.parseCommands(jdeploy());
    }

    /**
     * Gets the JDK provider from package.json (e.g., "jbr", "zulu", etc.).
     *
     * @return the JDK provider, or null if not specified
     */
    public String getJdkProvider() {
        if (jdeploy().has("jdkProvider")) {
            return jdeploy().getString("jdkProvider");
        }
        return null;
    }

    /**
     * Gets the JBR variant from package.json (e.g., "jcef", "sdk_jcef", etc.).
     *
     * @return the JBR variant, or null if not specified
     */
    public String getJbrVariant() {
        if (jdeploy().has("jbrVariant")) {
            return jdeploy().getString("jbrVariant");
        }
        return null;
    }

    /**
     * Gets the helper actions defined in package.json under jdeploy.helper.actions.
     *
     * Helper actions are quick links that appear in the tray menu and service management panel,
     * allowing users to open URLs, custom protocol handlers, or files.
     *
     * @return list of HelperAction (empty list if none configured)
     */
    public List<HelperAction> getHelperActions() {
        List<HelperAction> actions = new ArrayList<>();
        JSONObject jdeployConfig = jdeploy();

        if (jdeployConfig.has("helper")) {
            JSONObject helper = jdeployConfig.getJSONObject("helper");
            if (helper.has("actions")) {
                JSONArray actionsArray = helper.getJSONArray("actions");
                int len = actionsArray.length();
                for (int i = 0; i < len; i++) {
                    JSONObject actionObj = actionsArray.getJSONObject(i);
                    String label = actionObj.optString("label", null);
                    String description = actionObj.optString("description", null);
                    String url = actionObj.optString("url", null);

                    // Only add if required fields are present
                    if (label != null && !label.isEmpty() && url != null && !url.isEmpty()) {
                        try {
                            actions.add(new HelperAction(label, description, url));
                        } catch (IllegalArgumentException e) {
                            // Skip invalid actions
                        }
                    }
                }
            }
        }

        return actions;
    }

    /**
     * Gets the AI integration configuration from the package.json.
     * This includes MCP server config, skills, and agents.
     *
     * @param bundleDir the bundle directory for scanning skills/agents subdirectories
     * @return the AI integration configuration
     */
    public AiIntegrationConfig getAiIntegrationConfig(File bundleDir) {
        return AiIntegrationConfig.fromBundle(packageJson, bundleDir);
    }
}
