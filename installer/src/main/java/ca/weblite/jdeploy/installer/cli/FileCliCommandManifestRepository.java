package ca.weblite.jdeploy.installer.cli;

import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * File-based implementation of CliCommandManifestRepository.
 * 
 * Stores manifests as JSON files at: ~/.jdeploy/manifests/{arch}/{fqpn}/{packageName}-{timestamp}.json
 * 
 * Where:
 * - {arch} is the system architecture (e.g., "x64" or "arm64")
 * - {fqpn} is the fully-qualified package name (computed from packageName and source)
 * - {packageName} is the package name
 * - {timestamp} is the installation timestamp
 */
public class FileCliCommandManifestRepository implements CliCommandManifestRepository {

    private static final String MANIFEST_DIR_NAME = "manifests";
    private static final String JDEPLOY_HOME = ".jdeploy";

    // JSON field names
    private static final String JSON_PACKAGE_NAME = "packageName";
    private static final String JSON_SOURCE = "source";
    private static final String JSON_BIN_DIR = "binDir";
    private static final String JSON_COMMAND_NAMES = "commandNames";
    private static final String JSON_PATH_UPDATED = "pathUpdated";
    private static final String JSON_TIMESTAMP = "timestamp";

    /**
     * Saves a CLI command manifest to disk.
     *
     * @param manifest the manifest to save
     * @throws IOException if an error occurs during save
     */
    @Override
    public void save(CliCommandManifest manifest) throws IOException {
        File manifestFile = getManifestFile(manifest.getPackageName(), manifest.getSource(), 
                                             manifest.getTimestamp());
        
        // Create parent directories if they don't exist
        File parentDir = manifestFile.getParentFile();
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create manifest directory: " + parentDir);
            }
        }

        // Serialize to JSON
        JSONObject json = manifestToJson(manifest);
        
        // Write to file
        FileUtils.writeStringToFile(manifestFile, json.toString(2), "UTF-8");
    }

    /**
     * Loads a CLI command manifest for the given package.
     * 
     * Returns the most recent manifest if multiple exist (by timestamp in JSON).
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @return an Optional containing the manifest if found, empty otherwise
     * @throws IOException if an error occurs during load
     */
    @Override
    public Optional<CliCommandManifest> load(String packageName, String source) throws IOException {
        File manifestDir = getManifestDirectory(packageName, source);
        
        if (!manifestDir.exists()) {
            return Optional.empty();
        }

        // Find the most recent manifest file in the directory
        File[] files = manifestDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files == null || files.length == 0) {
            return Optional.empty();
        }

        // Sort by timestamp in JSON content (not filesystem modification time)
        File mostRecent = null;
        long mostRecentTimestamp = Long.MIN_VALUE;
        
        for (File file : files) {
            Optional<CliCommandManifest> manifest = loadFromFile(file);
            if (manifest.isPresent() && manifest.get().getTimestamp() > mostRecentTimestamp) {
                mostRecent = file;
                mostRecentTimestamp = manifest.get().getTimestamp();
            }
        }

        if (mostRecent == null) {
            return Optional.empty();
        }

        return loadFromFile(mostRecent);
    }

    /**
     * Deletes all manifest files for the given package.
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @throws IOException if an error occurs during delete
     */
    @Override
    public void delete(String packageName, String source) throws IOException {
        File manifestDir = getManifestDirectory(packageName, source);
        
        if (manifestDir.exists()) {
            FileUtils.deleteDirectory(manifestDir);
        }
    }

    /**
     * Gets the directory where manifests for a package are stored.
     * 
     * Path: ~/.jdeploy/manifests/{arch}/{fqpn}/
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @return the manifest directory
     */
    private File getManifestDirectory(String packageName, String source) {
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);
        
        File jdeployHome = new File(System.getProperty("user.home"), JDEPLOY_HOME);
        File manifestsDir = new File(jdeployHome, MANIFEST_DIR_NAME);
        File archDir = new File(manifestsDir, arch);
        
        return new File(archDir, fqpn);
    }

    /**
     * Gets the file path for a specific manifest.
     * 
     * Path: ~/.jdeploy/manifests/{arch}/{fqpn}/{packageName}-{timestamp}.json
     *
     * @param packageName the package name
     * @param source the GitHub source URL (null for NPM packages)
     * @param timestamp the installation timestamp
     * @return the manifest file
     */
    private File getManifestFile(String packageName, String source, long timestamp) {
        File manifestDir = getManifestDirectory(packageName, source);
        String fileName = packageName + "-" + timestamp + ".json";
        return new File(manifestDir, fileName);
    }

    /**
     * Converts a CliCommandManifest to a JSON object.
     *
     * @param manifest the manifest to convert
     * @return a JSONObject representation
     */
    private JSONObject manifestToJson(CliCommandManifest manifest) {
        JSONObject json = new JSONObject();
        json.put(JSON_PACKAGE_NAME, manifest.getPackageName());
        json.put(JSON_SOURCE, manifest.getSource());
        json.put(JSON_BIN_DIR, manifest.getBinDir().getAbsolutePath());
        json.put(JSON_COMMAND_NAMES, new JSONArray(manifest.getCommandNames()));
        json.put(JSON_PATH_UPDATED, manifest.isPathUpdated());
        json.put(JSON_TIMESTAMP, manifest.getTimestamp());
        return json;
    }

    /**
     * Converts a JSON object to a CliCommandManifest.
     *
     * @param json the JSON object
     * @return a CliCommandManifest
     */
    private CliCommandManifest jsonToManifest(JSONObject json) {
        String packageName = json.getString(JSON_PACKAGE_NAME);
        String source = json.isNull(JSON_SOURCE) ? null : json.getString(JSON_SOURCE);
        File binDir = new File(json.getString(JSON_BIN_DIR));
        
        JSONArray jsonCommandNames = json.getJSONArray(JSON_COMMAND_NAMES);
        List<String> commandNames = new ArrayList<>();
        for (int i = 0; i < jsonCommandNames.length(); i++) {
            commandNames.add(jsonCommandNames.getString(i));
        }
        
        boolean pathUpdated = json.getBoolean(JSON_PATH_UPDATED);
        long timestamp = json.getLong(JSON_TIMESTAMP);
        
        return new CliCommandManifest(packageName, source, binDir, commandNames, pathUpdated, timestamp);
    }

    /**
     * Loads a manifest from a JSON file.
     *
     * @param file the JSON file
     * @return an Optional containing the manifest if successfully loaded, empty otherwise
     * @throws IOException if an error occurs reading the file
     */
    private Optional<CliCommandManifest> loadFromFile(File file) throws IOException {
        try {
            String content = FileUtils.readFileToString(file, "UTF-8");
            JSONObject json = new JSONObject(content);
            CliCommandManifest manifest = jsonToManifest(json);
            return Optional.of(manifest);
        } catch (Exception e) {
            // If JSON parsing fails, return empty
            return Optional.empty();
        }
    }
}
