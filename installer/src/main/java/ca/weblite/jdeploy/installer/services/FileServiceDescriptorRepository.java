package ca.weblite.jdeploy.installer.services;

import ca.weblite.jdeploy.installer.util.ArchitectureUtil;
import ca.weblite.jdeploy.installer.util.CliCommandBinDirResolver;
import ca.weblite.jdeploy.models.CommandSpec;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * File-based implementation of ServiceDescriptorRepository.
 *
 * Stores service descriptors as JSON files at:
 * - Non-branch: ~/.jdeploy/services/{arch}/{fqpn}/{commandName}.json
 * - Branch: ~/.jdeploy/services/{arch}/{fqpn}/{branchName}/{commandName}.json
 *
 * Where:
 * - {arch} is the system architecture (e.g., "x64" or "arm64")
 * - {fqpn} is the fully-qualified package name
 * - {branchName} is the branch name (for branch installations)
 * - {commandName} is the command name
 *
 * @author Steve Hannah
 */
public class FileServiceDescriptorRepository implements ServiceDescriptorRepository {

    private static final String SERVICES_DIR_NAME = "services";
    private static final String JDEPLOY_HOME = ".jdeploy";

    // JSON field names for ServiceDescriptor
    private static final String JSON_PACKAGE_NAME = "packageName";
    private static final String JSON_VERSION = "version";
    private static final String JSON_SOURCE = "source";
    private static final String JSON_BRANCH_NAME = "branchName";
    private static final String JSON_INSTALLED_TIMESTAMP = "installedTimestamp";
    private static final String JSON_LAST_MODIFIED = "lastModified";
    private static final String JSON_COMMAND_SPEC = "commandSpec";

    // JSON field names for CommandSpec
    private static final String JSON_CMD_NAME = "name";
    private static final String JSON_CMD_DESCRIPTION = "description";
    private static final String JSON_CMD_ARGS = "args";
    private static final String JSON_CMD_IMPLEMENTATIONS = "implements";

    @Override
    public void save(ServiceDescriptor descriptor) throws IOException {
        File descriptorFile = getDescriptorFile(
            descriptor.getPackageName(),
            descriptor.getSource(),
            descriptor.getCommandName(),
            descriptor.getBranchName()
        );

        // Create parent directories if they don't exist
        File parentDir = descriptorFile.getParentFile();
        if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new IOException("Failed to create service descriptor directory: " + parentDir);
            }
        }

        // Serialize to JSON
        JSONObject json = descriptorToJson(descriptor);

        // Write to file
        FileUtils.writeStringToFile(descriptorFile, json.toString(2), "UTF-8");
    }

    @Override
    public Optional<ServiceDescriptor> load(String packageName, String source, String commandName, String branchName)
            throws IOException {
        File descriptorFile = getDescriptorFile(packageName, source, commandName, branchName);

        if (!descriptorFile.exists()) {
            return Optional.empty();
        }

        return loadFromFile(descriptorFile);
    }

    @Override
    public boolean delete(String packageName, String source, String commandName, String branchName) throws IOException {
        File descriptorFile = getDescriptorFile(packageName, source, commandName, branchName);

        if (!descriptorFile.exists()) {
            return false;
        }

        return descriptorFile.delete();
    }

    @Override
    public List<ServiceDescriptor> listByPackage(String packageName, String source) throws IOException {
        List<ServiceDescriptor> result = new ArrayList<>();
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);

        File jdeployHome = new File(System.getProperty("user.home"), JDEPLOY_HOME);
        File servicesDir = new File(jdeployHome, SERVICES_DIR_NAME);
        File archDir = new File(servicesDir, arch);
        File packageDir = new File(archDir, fqpn);

        if (!packageDir.exists()) {
            return Collections.emptyList();
        }

        // Collect descriptors from non-branch directory
        collectDescriptorsFromDirectory(packageDir, result);

        // Collect descriptors from branch subdirectories
        File[] subdirs = packageDir.listFiles(File::isDirectory);
        if (subdirs != null) {
            for (File subdir : subdirs) {
                collectDescriptorsFromDirectory(subdir, result);
            }
        }

        return result;
    }

    @Override
    public List<ServiceDescriptor> listByPackageAndBranch(String packageName, String source, String branchName)
            throws IOException {
        List<ServiceDescriptor> result = new ArrayList<>();
        File directory = getPackageDirectory(packageName, source, branchName);

        if (!directory.exists()) {
            return Collections.emptyList();
        }

        collectDescriptorsFromDirectory(directory, result);
        return result;
    }

    @Override
    public boolean exists(String packageName, String source, String commandName, String branchName) {
        File descriptorFile = getDescriptorFile(packageName, source, commandName, branchName);
        return descriptorFile.exists();
    }

    @Override
    public int deleteAllByPackage(String packageName, String source) throws IOException {
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);

        File jdeployHome = new File(System.getProperty("user.home"), JDEPLOY_HOME);
        File servicesDir = new File(jdeployHome, SERVICES_DIR_NAME);
        File archDir = new File(servicesDir, arch);
        File packageDir = new File(archDir, fqpn);

        if (!packageDir.exists()) {
            return 0;
        }

        // Count descriptors before deletion
        int count = listByPackage(packageName, source).size();

        // Delete the entire package directory
        FileUtils.deleteDirectory(packageDir);

        return count;
    }

    @Override
    public int deleteAllByPackageAndBranch(String packageName, String source, String branchName) throws IOException {
        File directory = getPackageDirectory(packageName, source, branchName);

        if (!directory.exists()) {
            return 0;
        }

        // Count descriptors before deletion
        int count = listByPackageAndBranch(packageName, source, branchName).size();

        // Delete the directory
        if (branchName == null || branchName.trim().isEmpty()) {
            // For non-branch, delete all .json files but not subdirectories
            File[] jsonFiles = directory.listFiles((dir, name) -> name.endsWith(".json"));
            if (jsonFiles != null) {
                for (File file : jsonFiles) {
                    file.delete();
                }
            }
        } else {
            // For branch, delete the entire branch directory
            FileUtils.deleteDirectory(directory);
        }

        return count;
    }

    /**
     * Gets the directory where service descriptors for a package are stored.
     *
     * @param packageName the package name
     * @param source the source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param branchName the branch name, or null for non-branch installations
     * @return the service descriptor directory
     */
    private File getPackageDirectory(String packageName, String source, String branchName) {
        String arch = ArchitectureUtil.getArchitecture();
        String fqpn = CliCommandBinDirResolver.computeFullyQualifiedPackageName(packageName, source);

        File jdeployHome = new File(System.getProperty("user.home"), JDEPLOY_HOME);
        File servicesDir = new File(jdeployHome, SERVICES_DIR_NAME);
        File archDir = new File(servicesDir, arch);
        File packageDir = new File(archDir, fqpn);

        if (branchName == null || branchName.trim().isEmpty()) {
            return packageDir;
        } else {
            return new File(packageDir, branchName);
        }
    }

    /**
     * Gets the file path for a specific service descriptor.
     *
     * @param packageName the package name
     * @param source the source URL (null for NPM packages, GitHub URL for GitHub packages)
     * @param commandName the command name
     * @param branchName the branch name, or null for non-branch installations
     * @return the descriptor file
     */
    private File getDescriptorFile(String packageName, String source, String commandName, String branchName) {
        File packageDir = getPackageDirectory(packageName, source, branchName);
        String fileName = commandName + ".json";
        return new File(packageDir, fileName);
    }

    /**
     * Collects service descriptors from a directory.
     *
     * @param directory the directory to scan
     * @param result the list to add descriptors to
     * @throws IOException if an error occurs reading files
     */
    private void collectDescriptorsFromDirectory(File directory, List<ServiceDescriptor> result)
            throws IOException {
        File[] jsonFiles = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (jsonFiles == null) {
            return;
        }

        for (File file : jsonFiles) {
            Optional<ServiceDescriptor> descriptor = loadFromFile(file);
            descriptor.ifPresent(result::add);
        }
    }

    /**
     * Converts a ServiceDescriptor to a JSON object.
     *
     * @param descriptor the descriptor to convert
     * @return a JSONObject representation
     */
    private JSONObject descriptorToJson(ServiceDescriptor descriptor) {
        JSONObject json = new JSONObject();
        json.put(JSON_PACKAGE_NAME, descriptor.getPackageName());
        json.put(JSON_VERSION, descriptor.getVersion());
        json.put(JSON_SOURCE, descriptor.getSource());
        json.put(JSON_BRANCH_NAME, descriptor.getBranchName());
        json.put(JSON_INSTALLED_TIMESTAMP, descriptor.getInstalledTimestamp());
        json.put(JSON_LAST_MODIFIED, descriptor.getLastModified());
        json.put(JSON_COMMAND_SPEC, commandSpecToJson(descriptor.getCommandSpec()));
        return json;
    }

    /**
     * Converts a CommandSpec to a JSON object.
     *
     * @param spec the command spec to convert
     * @return a JSONObject representation
     */
    private JSONObject commandSpecToJson(CommandSpec spec) {
        JSONObject json = new JSONObject();
        json.put(JSON_CMD_NAME, spec.getName());

        if (spec.getDescription() != null) {
            json.put(JSON_CMD_DESCRIPTION, spec.getDescription());
        }

        if (!spec.getArgs().isEmpty()) {
            json.put(JSON_CMD_ARGS, new JSONArray(spec.getArgs()));
        }

        if (!spec.getImplementations().isEmpty()) {
            json.put(JSON_CMD_IMPLEMENTATIONS, new JSONArray(spec.getImplementations()));
        }

        return json;
    }

    /**
     * Converts a JSON object to a ServiceDescriptor.
     *
     * @param json the JSON object
     * @return a ServiceDescriptor
     */
    private ServiceDescriptor jsonToDescriptor(JSONObject json) {
        String packageName = json.getString(JSON_PACKAGE_NAME);
        String version = json.getString(JSON_VERSION);
        String source = json.has(JSON_SOURCE) && !json.isNull(JSON_SOURCE) ? json.getString(JSON_SOURCE) : null;
        String branchName = json.isNull(JSON_BRANCH_NAME) ? null : json.getString(JSON_BRANCH_NAME);
        long installedTimestamp = json.getLong(JSON_INSTALLED_TIMESTAMP);
        long lastModified = json.getLong(JSON_LAST_MODIFIED);
        CommandSpec commandSpec = jsonToCommandSpec(json.getJSONObject(JSON_COMMAND_SPEC));

        return new ServiceDescriptor(
            commandSpec,
            packageName,
            version,
            source,
            branchName,
            installedTimestamp,
            lastModified
        );
    }

    /**
     * Converts a JSON object to a CommandSpec.
     *
     * @param json the JSON object
     * @return a CommandSpec
     */
    private CommandSpec jsonToCommandSpec(JSONObject json) {
        String name = json.getString(JSON_CMD_NAME);
        String description = json.has(JSON_CMD_DESCRIPTION) && !json.isNull(JSON_CMD_DESCRIPTION)
            ? json.getString(JSON_CMD_DESCRIPTION)
            : null;

        List<String> args = new ArrayList<>();
        if (json.has(JSON_CMD_ARGS)) {
            JSONArray argsArray = json.getJSONArray(JSON_CMD_ARGS);
            for (int i = 0; i < argsArray.length(); i++) {
                args.add(argsArray.getString(i));
            }
        }

        List<String> implementations = new ArrayList<>();
        if (json.has(JSON_CMD_IMPLEMENTATIONS)) {
            JSONArray implArray = json.getJSONArray(JSON_CMD_IMPLEMENTATIONS);
            for (int i = 0; i < implArray.length(); i++) {
                implementations.add(implArray.getString(i));
            }
        }

        return new CommandSpec(name, description, args, implementations);
    }

    /**
     * Loads a service descriptor from a JSON file.
     *
     * @param file the JSON file
     * @return an Optional containing the descriptor if successfully loaded, empty otherwise
     * @throws IOException if an error occurs reading the file
     */
    private Optional<ServiceDescriptor> loadFromFile(File file) throws IOException {
        try {
            String content = FileUtils.readFileToString(file, "UTF-8");
            JSONObject json = new JSONObject(content);
            ServiceDescriptor descriptor = jsonToDescriptor(json);
            return Optional.of(descriptor);
        } catch (Exception e) {
            // If JSON parsing or deserialization fails, return empty
            return Optional.empty();
        }
    }
}
