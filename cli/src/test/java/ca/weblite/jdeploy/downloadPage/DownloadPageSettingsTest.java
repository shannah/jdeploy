package ca.weblite.jdeploy.downloadPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DownloadPageSettingsTest {

    private DownloadPageSettings settings;

    @BeforeEach
    void setUp() {
        settings = new DownloadPageSettings();
    }

    @Test
    @DisplayName("Should initialize with default platforms")
    void shouldInitializeWithDefaultPlatforms() {
        assertEquals(DownloadPageSettings.DEFAULT_ENABLED_PLATFORMS, settings.getEnabledPlatforms());
        assertTrue(settings.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should return unmodifiable set for enabled platforms")
    void shouldReturnUnmodifiableSetForEnabledPlatforms() {
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = settings.getEnabledPlatforms();
        
        assertThrows(UnsupportedOperationException.class, () -> 
            enabledPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64)
        );
    }

    @Test
    @DisplayName("Should return default platform when enabled platforms is empty")
    void shouldReturnDefaultWhenEmpty() {
        settings.setEnabledPlatforms(new HashSet<>());
        
        Set<DownloadPageSettings.BundlePlatform> result = settings.getEnabledPlatforms();
        assertEquals(1, result.size());
        assertTrue(result.contains(DownloadPageSettings.BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should set enabled platforms correctly")
    void shouldSetEnabledPlatforms() {
        Set<DownloadPageSettings.BundlePlatform> newPlatforms = new HashSet<>();
        newPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        newPlatforms.add(DownloadPageSettings.BundlePlatform.MacX64);
        
        settings.setEnabledPlatforms(newPlatforms);
        
        assertEquals(newPlatforms, settings.getEnabledPlatforms());
    }

    @Test
    @DisplayName("Should handle null enabled platforms")
    void shouldHandleNullEnabledPlatforms() {
        settings.setEnabledPlatforms(null);
        
        assertTrue(settings.getEnabledPlatforms().size() == 1);
        assertTrue(settings.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should normalize platforms to All when All is present")
    void shouldNormalizePlatformsToAll() {
        Set<DownloadPageSettings.BundlePlatform> platforms = new HashSet<>();
        platforms.add(DownloadPageSettings.BundlePlatform.All);
        platforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        platforms.add(DownloadPageSettings.BundlePlatform.MacX64);
        
        settings.setEnabledPlatforms(platforms);
        
        Set<DownloadPageSettings.BundlePlatform> result = settings.getEnabledPlatforms();
        assertEquals(1, result.size());
        assertTrue(result.contains(DownloadPageSettings.BundlePlatform.All));
    }

    @Test
    @DisplayName("Should normalize platforms to Default when Default is present")
    void shouldNormalizePlatformsToDefault() {
        Set<DownloadPageSettings.BundlePlatform> platforms = new HashSet<>();
        platforms.add(DownloadPageSettings.BundlePlatform.Default);
        platforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        platforms.add(DownloadPageSettings.BundlePlatform.MacX64);
        
        settings.setEnabledPlatforms(platforms);
        
        Set<DownloadPageSettings.BundlePlatform> result = settings.getEnabledPlatforms();
        assertEquals(1, result.size());
        assertTrue(result.contains(DownloadPageSettings.BundlePlatform.Default));
    }

    @Test
    @DisplayName("Should resolve All platform to all available platforms")
    void shouldResolveAllPlatform() {
        Set<DownloadPageSettings.BundlePlatform> platforms = new HashSet<>();
        platforms.add(DownloadPageSettings.BundlePlatform.All);
        settings.setEnabledPlatforms(platforms);
        
        Set<DownloadPageSettings.BundlePlatform> resolved = settings.getResolvedPlatforms();
        
        assertEquals(DownloadPageSettings.BundlePlatform.values().length, resolved.size());
        for (DownloadPageSettings.BundlePlatform platform : DownloadPageSettings.BundlePlatform.values()) {
            assertTrue(resolved.contains(platform));
        }
    }

    @Test
    @DisplayName("Should resolve Default platform to Default only")
    void shouldResolveDefaultPlatform() {
        Set<DownloadPageSettings.BundlePlatform> platforms = new HashSet<>();
        platforms.add(DownloadPageSettings.BundlePlatform.Default);
        settings.setEnabledPlatforms(platforms);
        
        Set<DownloadPageSettings.BundlePlatform> resolved = settings.getResolvedPlatforms();

        assertEquals(DownloadPageSettings.DEFAULT_RESOLVED_PLATFORMS.size(), resolved.size());
        for (DownloadPageSettings.BundlePlatform platform : DownloadPageSettings.DEFAULT_RESOLVED_PLATFORMS) {
            assertTrue(resolved.contains(platform));
        }
    }

    @Test
    @DisplayName("Should resolve specific platforms without change")
    void shouldResolveSpecificPlatforms() {
        Set<DownloadPageSettings.BundlePlatform> platforms = new HashSet<>();
        platforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        platforms.add(DownloadPageSettings.BundlePlatform.MacX64);
        settings.setEnabledPlatforms(platforms);
        
        Set<DownloadPageSettings.BundlePlatform> resolved = settings.getResolvedPlatforms();
        
        assertEquals(platforms, resolved);
    }

    @Test
    @DisplayName("Should return unmodifiable set for resolved platforms")
    void shouldReturnUnmodifiableSetForResolvedPlatforms() {
        Set<DownloadPageSettings.BundlePlatform> resolved = settings.getResolvedPlatforms();
        
        assertThrows(UnsupportedOperationException.class, () -> 
            resolved.add(DownloadPageSettings.BundlePlatform.WindowsX64)
        );
    }

    @Test
    @DisplayName("BundlePlatform should have correct platform names")
    void bundlePlatformShouldHaveCorrectNames() {
        assertEquals("windows-arm64", DownloadPageSettings.BundlePlatform.WindowsArm64.getPlatformName());
        assertEquals("windows-x64", DownloadPageSettings.BundlePlatform.WindowsX64.getPlatformName());
        assertEquals("mac-arm64", DownloadPageSettings.BundlePlatform.MacArm64.getPlatformName());
        assertEquals("mac-x64", DownloadPageSettings.BundlePlatform.MacX64.getPlatformName());
        assertEquals("mac-high-sierra", DownloadPageSettings.BundlePlatform.MacHighSierra.getPlatformName());
        assertEquals("linux-arm64", DownloadPageSettings.BundlePlatform.LinuxArm64.getPlatformName());
        assertEquals("linux-x64", DownloadPageSettings.BundlePlatform.LinuxX64.getPlatformName());
        assertEquals("debian-arm64", DownloadPageSettings.BundlePlatform.DebianArm64.getPlatformName());
        assertEquals("debian-x64", DownloadPageSettings.BundlePlatform.DebianX64.getPlatformName());
        assertEquals("all", DownloadPageSettings.BundlePlatform.All.getPlatformName());
        assertEquals("default", DownloadPageSettings.BundlePlatform.Default.getPlatformName());
    }

    @Test
    @DisplayName("BundlePlatform.fromString should parse platform names correctly")
    void bundlePlatformFromStringShouldParseCorrectly() {
        assertEquals(DownloadPageSettings.BundlePlatform.WindowsArm64, 
            DownloadPageSettings.BundlePlatform.fromString("windows-arm64"));
        assertEquals(DownloadPageSettings.BundlePlatform.WindowsX64, 
            DownloadPageSettings.BundlePlatform.fromString("windows-x64"));
        assertEquals(DownloadPageSettings.BundlePlatform.MacArm64, 
            DownloadPageSettings.BundlePlatform.fromString("mac-arm64"));
        assertEquals(DownloadPageSettings.BundlePlatform.MacX64, 
            DownloadPageSettings.BundlePlatform.fromString("mac-x64"));
        assertEquals(DownloadPageSettings.BundlePlatform.MacHighSierra, 
            DownloadPageSettings.BundlePlatform.fromString("mac-high-sierra"));
        assertEquals(DownloadPageSettings.BundlePlatform.LinuxArm64, 
            DownloadPageSettings.BundlePlatform.fromString("linux-arm64"));
        assertEquals(DownloadPageSettings.BundlePlatform.LinuxX64, 
            DownloadPageSettings.BundlePlatform.fromString("linux-x64"));
        assertEquals(DownloadPageSettings.BundlePlatform.DebianArm64, 
            DownloadPageSettings.BundlePlatform.fromString("debian-arm64"));
        assertEquals(DownloadPageSettings.BundlePlatform.DebianX64, 
            DownloadPageSettings.BundlePlatform.fromString("debian-x64"));
        assertEquals(DownloadPageSettings.BundlePlatform.All, 
            DownloadPageSettings.BundlePlatform.fromString("all"));
        assertEquals(DownloadPageSettings.BundlePlatform.Default, 
            DownloadPageSettings.BundlePlatform.fromString("default"));
    }

    @Test
    @DisplayName("BundlePlatform.fromString should be case insensitive")
    void bundlePlatformFromStringShouldBeCaseInsensitive() {
        assertEquals(DownloadPageSettings.BundlePlatform.WindowsX64, 
            DownloadPageSettings.BundlePlatform.fromString("WINDOWS-X64"));
        assertEquals(DownloadPageSettings.BundlePlatform.MacArm64, 
            DownloadPageSettings.BundlePlatform.fromString("Mac-Arm64"));
        assertEquals(DownloadPageSettings.BundlePlatform.All, 
            DownloadPageSettings.BundlePlatform.fromString("ALL"));
    }

    @Test
    @DisplayName("BundlePlatform.fromString should throw exception for unknown platform")
    void bundlePlatformFromStringShouldThrowForUnknown() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            DownloadPageSettings.BundlePlatform.fromString("unknown-platform")
        );
        
        assertEquals("Unknown platform: unknown-platform", exception.getMessage());
    }
}