package ca.weblite.jdeploy.installer.win;

import java.util.Map;
import java.util.Set;

/**
 * Interface for Windows registry operations targeting HKEY_CURRENT_USER.
 * Provides abstraction over JNA-based registry access to enable testing and pluggable implementations.
 */
public interface RegistryOperations {

    /**
     * Checks if a registry key exists in HKEY_CURRENT_USER.
     *
     * @param key the registry key path (e.g., "Software\\MyApp")
     * @return true if the key exists, false otherwise
     */
    boolean keyExists(String key);

    /**
     * Checks if a registry value exists under the given key in HKEY_CURRENT_USER.
     *
     * @param key the registry key path
     * @param valueName the value name; null represents the default value
     * @return true if the value exists, false otherwise
     */
    boolean valueExists(String key, String valueName);

    /**
     * Retrieves a string value from the registry under the given key in HKEY_CURRENT_USER.
     *
     * @param key the registry key path
     * @param valueName the value name; null represents the default value
     * @return the string value, or null if the key or value does not exist
     */
    String getStringValue(String key, String valueName);

    /**
     * Sets a string value in the registry under the given key in HKEY_CURRENT_USER.
     * Creates the key if it does not exist.
     *
     * @param key the registry key path
     * @param valueName the value name; null represents the default value
     * @param value the string value to set
     */
    void setStringValue(String key, String valueName, String value);

    /**
     * Sets a long value in the registry under the given key in HKEY_CURRENT_USER.
     * Creates the key if it does not exist.
     *
     * @param key the registry key path
     * @param value the long value to set
     */
    void setLongValue(String key, long value);

    /**
     * Creates a registry key in HKEY_CURRENT_USER.
     * If the key already exists, this method has no effect.
     *
     * @param key the registry key path to create
     */
    void createKey(String key);

    /**
     * Deletes a registry key from HKEY_CURRENT_USER.
     * The key must be empty (no subkeys or values) to be deleted.
     *
     * @param key the registry key path to delete
     */
    void deleteKey(String key);

    /**
     * Deletes a value from a registry key in HKEY_CURRENT_USER.
     *
     * @param key the registry key path
     * @param valueName the value name to delete; null represents the default value
     */
    void deleteValue(String key, String valueName);

    /**
     * Retrieves all subkey names under the given registry key in HKEY_CURRENT_USER.
     *
     * @param key the registry key path
     * @return a set of subkey names; empty set if the key has no subkeys
     */
    Set<String> getKeys(String key);

    /**
     * Retrieves all values under the given registry key in HKEY_CURRENT_USER.
     *
     * @param key the registry key path
     * @return a map of value names to their values; empty map if the key has no values
     */
    Map<String, Object> getValues(String key);
}
