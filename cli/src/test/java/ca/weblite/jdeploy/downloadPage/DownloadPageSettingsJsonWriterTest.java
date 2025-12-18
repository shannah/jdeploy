package ca.weblite.jdeploy.downloadPage;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DownloadPageSettingsJsonWriterTest {

    private DownloadPageSettingsJsonWriter writer;
    private DownloadPageSettings settings;
    private JSONObject jsonObject;

    @BeforeEach
    void setUp() {
        writer = new DownloadPageSettingsJsonWriter();
        settings = new DownloadPageSettings();
        jsonObject = new JSONObject();
    }

    @Test
    @DisplayName("Should handle null settings gracefully")
    void shouldHandleNullSettingsGracefully() {
        assertDoesNotThrow(() -> writer.write(null, jsonObject));
        assertFalse(jsonObject.has("platforms"));
    }

    @Test
    @DisplayName("Should handle null JSON object gracefully")
    void shouldHandleNullJsonObjectGracefully() {
        assertDoesNotThrow(() -> writer.write(settings, null));
    }

    @Test
    @DisplayName("Should handle both null parameters gracefully")
    void shouldHandleBothNullParametersGracefully() {
        assertDoesNotThrow(() -> writer.write(null, null));
    }

    @Test
    @DisplayName("Should write default platforms to JSON")
    void shouldWriteDefaultPlatformsToJson() {
        writer.write(settings, jsonObject);
        
        assertTrue(jsonObject.has("platforms"));
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(1, platforms.length());
        assertEquals("default", platforms.getString(0));
    }

    @Test
    @DisplayName("Should write multiple platforms to JSON")
    void shouldWriteMultiplePlatformsToJson() {
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new HashSet<>();
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.MacArm64);
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.LinuxX64);
        settings.setEnabledPlatforms(enabledPlatforms);
        
        writer.write(settings, jsonObject);
        
        assertTrue(jsonObject.has("platforms"));
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(3, platforms.length());
        
        Set<String> platformNames = new HashSet<>();
        for (int i = 0; i < platforms.length(); i++) {
            platformNames.add(platforms.getString(i));
        }
        
        assertTrue(platformNames.contains("windows-x64"));
        assertTrue(platformNames.contains("mac-arm64"));
        assertTrue(platformNames.contains("linux-x64"));
    }

    @Test
    @DisplayName("Should remove existing platforms array before writing")
    void shouldRemoveExistingPlatformsArrayBeforeWriting() {
        JSONArray existingPlatforms = new JSONArray();
        existingPlatforms.put("old-platform");
        jsonObject.put("platforms", existingPlatforms);
        
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new HashSet<>();
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        settings.setEnabledPlatforms(enabledPlatforms);
        
        writer.write(settings, jsonObject);
        
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(1, platforms.length());
        assertEquals("windows-x64", platforms.getString(0));
        
        for (int i = 0; i < platforms.length(); i++) {
            assertNotEquals("old-platform", platforms.getString(i));
        }
    }

    @Test
    @DisplayName("Should write all platform types correctly")
    void shouldWriteAllPlatformTypesCorrectly() {
        Set<DownloadPageSettings.BundlePlatform> allPlatforms = new HashSet<>();
        allPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsArm64);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.MacArm64);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.MacX64);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.MacHighSierra);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.LinuxArm64);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.LinuxX64);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.DebianArm64);
        allPlatforms.add(DownloadPageSettings.BundlePlatform.DebianX64);
        settings.setEnabledPlatforms(allPlatforms);
        
        writer.write(settings, jsonObject);
        
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(9, platforms.length());
        
        Set<String> platformNames = new HashSet<>();
        for (int i = 0; i < platforms.length(); i++) {
            platformNames.add(platforms.getString(i));
        }
        
        assertTrue(platformNames.contains("windows-arm64"));
        assertTrue(platformNames.contains("windows-x64"));
        assertTrue(platformNames.contains("mac-arm64"));
        assertTrue(platformNames.contains("mac-x64"));
        assertTrue(platformNames.contains("mac-high-sierra"));
        assertTrue(platformNames.contains("linux-arm64"));
        assertTrue(platformNames.contains("linux-x64"));
        assertTrue(platformNames.contains("debian-arm64"));
        assertTrue(platformNames.contains("debian-x64"));
    }

    @Test
    @DisplayName("Should write All platform correctly")
    void shouldWriteAllPlatformCorrectly() {
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new HashSet<>();
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.All);
        settings.setEnabledPlatforms(enabledPlatforms);
        
        writer.write(settings, jsonObject);
        
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(1, platforms.length());
        assertEquals("all", platforms.getString(0));
    }

    @Test
    @DisplayName("Should write Default platform correctly")
    void shouldWriteDefaultPlatformCorrectly() {
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new HashSet<>();
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.Default);
        settings.setEnabledPlatforms(enabledPlatforms);
        
        writer.write(settings, jsonObject);
        
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(1, platforms.length());
        assertEquals("default", platforms.getString(0));
    }

    @Test
    @DisplayName("Should handle empty enabled platforms")
    void shouldHandleEmptyEnabledPlatforms() {
        settings.setEnabledPlatforms(new HashSet<>());
        
        writer.write(settings, jsonObject);
        
        assertTrue(jsonObject.has("platforms"));
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(1, platforms.length());
        assertEquals("default", platforms.getString(0));
    }

    @Test
    @DisplayName("Should preserve other JSON properties")
    void shouldPreserveOtherJsonProperties() {
        jsonObject.put("otherProperty", "value");
        jsonObject.put("anotherProperty", 123);
        
        writer.write(settings, jsonObject);
        
        assertTrue(jsonObject.has("otherProperty"));
        assertTrue(jsonObject.has("anotherProperty"));
        assertEquals("value", jsonObject.getString("otherProperty"));
        assertEquals(123, jsonObject.getInt("anotherProperty"));
    }

    @Test
    @DisplayName("Should create platforms array if not exists")
    void shouldCreatePlatformsArrayIfNotExists() {
        assertFalse(jsonObject.has("platforms"));
        
        writer.write(settings, jsonObject);
        
        assertTrue(jsonObject.has("platforms"));
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertNotNull(platforms);
    }

    @Test
    @DisplayName("Should write platforms in correct format")
    void shouldWritePlatformsInCorrectFormat() {
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new HashSet<>();
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        settings.setEnabledPlatforms(enabledPlatforms);
        
        writer.write(settings, jsonObject);
        
        JSONArray platforms = jsonObject.getJSONArray("platforms");
        assertEquals(1, platforms.length());
        
        String platformName = platforms.getString(0);
        assertEquals("windows-x64", platformName);
        assertEquals(DownloadPageSettings.BundlePlatform.WindowsX64.getPlatformName(), platformName);
    }
}