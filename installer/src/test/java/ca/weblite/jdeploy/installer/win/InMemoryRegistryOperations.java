package ca.weblite.jdeploy.installer.win;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In-memory implementation of RegistryOperations for testing purposes.
 * Simulates a Windows registry hierarchy using nested maps.
 * Suitable for unit tests that need to verify registry operations without actual Windows registry access.
 */
public class InMemoryRegistryOperations implements RegistryOperations {

    private final Map<String, Map<String, Object>> registry = new HashMap<>();

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
        return values.containsKey(valueName);
    }

    @Override
    public String getStringValue(String key, String valueName) {
        Map<String, Object> values = registry.get(key);
        if (values == null) {
            return null;
        }
        Object value = values.get(valueName);
        return value == null ? null : value.toString();
    }

    @Override
    public void setStringValue(String key, String valueName, String value) {
        ensureKeyExists(key);
        registry.get(key).put(valueName, value);
    }

    @Override
    public void setLongValue(String key, long value) {
        ensureKeyExists(key);
        registry.get(key).put(null, value);
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
            values.remove(valueName);
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
        if (values == null) {
            return new HashMap<>();
        }
        return new HashMap<>(values);
    }

    /**
     * Ensures that the given key exists in the registry, creating it if necessary.
     * This implementation creates parent keys recursively.
     *
     * @param key the registry key path
     */
    private void ensureKeyExists(String key) {
        if (key == null || key.isEmpty()) return;

        if (!registry.containsKey(key)) {
            int lastBackslash = key.lastIndexOf('\\');
            if (lastBackslash != -1) {
                ensureKeyExists(key.substring(0, lastBackslash));
            }
            registry.put(key, new HashMap<>());
        }
    }
}
