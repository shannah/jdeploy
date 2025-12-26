package ca.weblite.jdeploy.installer.win;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * JNA-based implementation of RegistryOperations targeting HKEY_CURRENT_USER.
 * Delegates all registry operations to the static methods provided by Advapi32Util.
 */
public class JnaRegistryOperations implements RegistryOperations {

    @Override
    public boolean keyExists(String key) {
        return Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key);
    }

    @Override
    public boolean valueExists(String key, String valueName) {
        return Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, key, valueName);
    }

    @Override
    public String getStringValue(String key, String valueName) {
        return Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, key, valueName);
    }

    @Override
    public void setStringValue(String key, String valueName, String value) {
        Advapi32Util.registrySetStringValue(WinReg.HKEY_CURRENT_USER, key, valueName, value);
    }

    @Override
    public void setLongValue(String key, long value) {
        Advapi32Util.registrySetIntValue(WinReg.HKEY_CURRENT_USER, key, null, (int) value);
    }

    @Override
    public void createKey(String key) {
        Advapi32Util.registryCreateKey(WinReg.HKEY_CURRENT_USER, key);
    }

    @Override
    public void deleteKey(String key) {
        Advapi32Util.registryDeleteKey(WinReg.HKEY_CURRENT_USER, key);
    }

    @Override
    public void deleteValue(String key, String valueName) {
        Advapi32Util.registryDeleteValue(WinReg.HKEY_CURRENT_USER, key, valueName);
    }

    @Override
    public Set<String> getKeys(String key) {
        String[] keys = Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, key);
        return new HashSet<>(Arrays.asList(keys));
    }

    @Override
    public Map<String, Object> getValues(String key) {
        return Advapi32Util.registryGetValues(WinReg.HKEY_CURRENT_USER, key);
    }
}
