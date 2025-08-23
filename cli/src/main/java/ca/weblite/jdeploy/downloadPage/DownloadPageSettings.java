package ca.weblite.jdeploy.downloadPage;

import org.json.JSONObject;

import java.util.*;

public class DownloadPageSettings {
    public static final Set<BundlePlatform> DEFAULT_ENABLED_PLATFORMS = new HashSet<BundlePlatform>() {{
        add(BundlePlatform.Default);
    }};

    public static enum BundlePlatform {
        WindowsArm64("windows-arm64"),
        WindowsX64("windows-x64"),
        MacArm64("mac-arm64"),
        MacX64("mac-x64"),
        MacHighSierra("mac-high-sierra"),
        LinuxArm64("linux-arm64"),
        LinuxX64("linux-x64"),
        DebianArm64("debian-arm64"),
        DebianX64("debian-x64"),
        All("all"),
        Default("default");
        private final String platformName;
        BundlePlatform(String platformName) {
            this.platformName = platformName;
        }

        public String getPlatformName() {
            return platformName;
        }

        public static BundlePlatform fromString(String platformName) {
            for (BundlePlatform platform : BundlePlatform.values()) {
                if (platform.platformName.equalsIgnoreCase(platformName)) {
                    return platform;
                }
            }
            throw new IllegalArgumentException("Unknown platform: " + platformName);
        }
    }

    private final Set<BundlePlatform> enabledPlatforms = new HashSet<>();

    public DownloadPageSettings() {
        this.enabledPlatforms.addAll(DEFAULT_ENABLED_PLATFORMS);
    }


    public Set<BundlePlatform> getEnabledPlatforms() {
        return Collections.unmodifiableSet(enabledPlatforms);
    }

    public void setEnabledPlatforms(Set<BundlePlatform> enabledPlatforms) {
        this.enabledPlatforms.clear();
        if (enabledPlatforms != null) {
            this.enabledPlatforms.addAll(enabledPlatforms);
            normalizePlatforms();
        }
    }

    public Set<BundlePlatform> getResolvedPlatforms() {
        Set<BundlePlatform> resolvedPlatforms = new HashSet<>(enabledPlatforms);
        if (resolvedPlatforms.contains(BundlePlatform.All)) {
            resolvedPlatforms.clear();
            resolvedPlatforms.addAll(Arrays.asList(BundlePlatform.values()));
        } else if (resolvedPlatforms.contains(BundlePlatform.Default)) {
            resolvedPlatforms.clear();
            resolvedPlatforms.add(BundlePlatform.Default);
        }
        return Collections.unmodifiableSet(resolvedPlatforms);
    }

    private void normalizePlatforms() {
        if (enabledPlatforms.contains(BundlePlatform.All)) {
            enabledPlatforms.clear();
            enabledPlatforms.add(BundlePlatform.All);
        }
        if (enabledPlatforms.contains(BundlePlatform.Default)) {
            enabledPlatforms.clear();
            enabledPlatforms.add(BundlePlatform.Default);
        }
    }
}
