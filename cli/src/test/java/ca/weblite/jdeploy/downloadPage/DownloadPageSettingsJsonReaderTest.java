package ca.weblite.jdeploy.downloadPage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DownloadPageSettingsJsonReaderTest {

    private DownloadPageSettingsJsonReader reader;
    private DownloadPageSettings settings;

    @BeforeEach
    void setUp() {
        reader = new DownloadPageSettingsJsonReader();
        settings = new DownloadPageSettings();
    }

    @Test
    @DisplayName("Should handle null settings gracefully")
    void shouldHandleNullSettingsGracefully() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("platforms", new JSONArray("[\"windows-x64\", \"mac-arm64\"]"));
        
        assertDoesNotThrow(() -> reader.readJson(null, jsonObject));
    }

    @Test
    @DisplayName("Should handle null JSON object gracefully")
    void shouldHandleNullJsonObjectGracefully() {
        assertDoesNotThrow(() -> reader.readJson(settings, null));
        
        assertTrue(settings.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should handle both null parameters gracefully")
    void shouldHandleBothNullParametersGracefully() {
        assertDoesNotThrow(() -> reader.readJson(null, null));
    }

    @Test
    @DisplayName("Should read platforms from JSON array")
    void shouldReadPlatformsFromJsonArray() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        platformsArray.put("windows-x64");
        platformsArray.put("mac-arm64");
        platformsArray.put("linux-x64");
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(3, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.WindowsX64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.MacArm64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.LinuxX64));
    }

    @Test
    @DisplayName("Should handle empty platforms array")
    void shouldHandleEmptyPlatformsArray() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(1, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should ignore unknown platforms")
    void shouldIgnoreUnknownPlatforms() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        platformsArray.put("windows-x64");
        platformsArray.put("unknown-platform");
        platformsArray.put("mac-arm64");
        platformsArray.put("another-unknown");
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(2, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.WindowsX64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.MacArm64));
    }

    @Test
    @DisplayName("Should handle null values in platforms array")
    void shouldHandleNullValuesInPlatformsArray() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        platformsArray.put("windows-x64");
        platformsArray.put((String) null);
        platformsArray.put("mac-arm64");
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(2, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.WindowsX64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.MacArm64));
    }

    @Test
    @DisplayName("Should handle JSON without platforms property")
    void shouldHandleJsonWithoutPlatformsProperty() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("otherProperty", "value");
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should read all platform types correctly")
    void shouldReadAllPlatformTypesCorrectly() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        platformsArray.put("windows-arm64");
        platformsArray.put("windows-x64");
        platformsArray.put("mac-arm64");
        platformsArray.put("mac-x64");
        platformsArray.put("mac-high-sierra");
        platformsArray.put("linux-arm64");
        platformsArray.put("linux-x64");
        platformsArray.put("debian-arm64");
        platformsArray.put("debian-x64");
        platformsArray.put("all");
        platformsArray.put("default");
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(1, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.All));
    }

    @Test
    @DisplayName("Should handle case insensitive platform names")
    void shouldHandleCaseInsensitivePlatformNames() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        platformsArray.put("WINDOWS-X64");
        platformsArray.put("Mac-Arm64");
        platformsArray.put("LINUX-x64");
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(3, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.WindowsX64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.MacArm64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.LinuxX64));
    }

    @Test
    @DisplayName("Should preserve order when using LinkedHashSet")
    void shouldPreserveOrderWhenUsingLinkedHashSet() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        platformsArray.put("linux-x64");
        platformsArray.put("windows-x64");
        platformsArray.put("mac-arm64");
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(3, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.LinuxX64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.WindowsX64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.MacArm64));
    }

    @Test
    @DisplayName("Should handle mixed valid and invalid entries")
    void shouldHandleMixedValidAndInvalidEntries() {
        JSONObject jsonObject = new JSONObject();
        JSONArray platformsArray = new JSONArray();
        platformsArray.put("windows-x64");
        platformsArray.put(123);
        platformsArray.put("mac-arm64");
        platformsArray.put(true);
        platformsArray.put("linux-x64");
        jsonObject.put("platforms", platformsArray);
        
        reader.readJson(settings, jsonObject);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        assertEquals(3, enabledPlatforms.size());
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.WindowsX64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.MacArm64));
        assertTrue(enabledPlatforms.contains(DownloadPageSettings.BundlePlatform.LinuxX64));
    }
}