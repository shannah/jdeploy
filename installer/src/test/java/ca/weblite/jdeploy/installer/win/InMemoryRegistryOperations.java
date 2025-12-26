package ca.weblite.jdeploy.installer.win;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashMap;

/**
 * In-memory implementation of RegistryOperations for testing purposes.
 * Simulates a Windows registry hierarchy using nested maps.
 * Suitable for unit tests that need to verify registry operations without actual Windows registry access.
 */
public class InMemoryRegistryOperations implements RegistryOperations {

    private static final String DEFAULT_VALUE_NAME = "__DEFAULT__";

    private final Map<String, Map<String, Object>> registry = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    private String normalizeValueName(String valueName) {
        return valueName == null ? DEFAULT_VALUE_NAME : valueName;
    }

    /**
     * Clears all registry data. Useful for test isolation.
     */
    public void clear() {
        registry.clear();
    }

    /**
     * Returns a debug representation of the entire registry state.
     * Useful for test diagnostics and assertions.
     *
     * @return a string representation of the registry hierarchy
     */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("InMemoryRegistry{\n");
        for (Map.Entry<String, Map<String, Object>> keyEntry : registry.entrySet()) {
            sb.append("  ").append(keyEntry.getKey()).append(" = {\n");
            for (Map.Entry<String, Object> valueEntry : keyEntry.getValue().entrySet()) {
                String valueName = valueEntry.getKey();
                Object value = valueEntry.getValue();
                sb.append("    ").append(valueName == null ? "(default)" : valueName)
                    .append(": ").append(value).append("\n");
            }
            sb.append("  }\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    @Override
    public boolean keyExists(String key) {
        return registry.containsKey(key);
    }

    @Override
    public boolean valueExists(String key, String valueName) {
        Map<String, Object> values = registry.get(key);
        if (values == null) {
            return false;
        }
        return values.containsKey(normalizeValueName(valueName));
    }

    @Override
    public String getStringValue(String key, String valueName) {
        Map<String, Object> values = registry.get(key);
        if (values == null) {
            return null;
        }
        Object value = values.get(normalizeValueName(valueName));
        return value == null ? null : value.toString();
    }

    @Override
    public void setStringValue(String key, String valueName, String value) {
        ensureKeyExists(key);
        registry.get(key).put(normalizeValueName(valueName), value);
    }

    @Override
    public void setLongValue(String key, long value) {
        ensureKeyExists(key);
        registry.get(key).put(normalizeValueName(null), value);
    }

    @Override
    public void createKey(String key) {
        ensureKeyExists(key);
    }

    @Override
    public void deleteKey(String key) {
        registry.remove(key);
    }

    @Override
    public void deleteValue(String key, String valueName) {
        Map<String, Object> values = registry.get(key);
        if (values != null) {
            values.remove(normalizeValueName(valueName));
        }
    }

    @Override
    public Set<String> getKeys(String key) {
        Set<String> subkeys = new HashSet<>();
        if (key == null) return subkeys;
        String prefix = key.isEmpty() ? "" : (key.endsWith("\\") ? key : key + "\\");
        for (String registryKey : registry.keySet()) {
            if (registryKey.regionMatches(true, 0, prefix, 0, prefix.length()) && !registryKey.equalsIgnoreCase(key)) {
                String remainder = registryKey.substring(prefix.length());
                if (remainder.isEmpty()) continue;
                // Extract the immediate subkey (first component after prefix)
                int nextBackslash = remainder.indexOf('\\');
                String subkey = nextBackslash == -1 ? remainder : remainder.substring(0, nextBackslash);
                subkeys.add(subkey);
            }
        }
        return subkeys;
    }

    @Override
    public Map<String, Object> getValues(String key) {
        Map<String, Object> values = registry.get(key);
        TreeMap<String, Object> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (values != null) {
            copy.putAll(values);
        }
        return copy;
    }

    /**
     * Ensures that the given key exists in the registry, creating it if necessary.
     * This implementation creates parent keys recursively.
     *
     * @param key the registry key path
     */
    private void ensureKeyExists(String key) {
        if (key == null || key.isEmpty()) return;

        String[] parts = key.split("\\\\");
        StringBuilder currentPath = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                currentPath.append("\\");
            }
            currentPath.append(parts[i]);
            String path = currentPath.toString();
            if (!registry.containsKey(path)) {
                registry.put(path, new TreeMap<>(String.CASE_INSENSITIVE_ORDER));
            }
        }
    }
}
