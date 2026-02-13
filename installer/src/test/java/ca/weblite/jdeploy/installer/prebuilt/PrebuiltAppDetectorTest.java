package ca.weblite.jdeploy.installer.prebuilt;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PrebuiltAppDetector.
 */
class PrebuiltAppDetectorTest {

    private PrebuiltAppDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PrebuiltAppDetector();
    }

    @Nested
    @DisplayName("hasPrebuiltApp tests")
    class HasPrebuiltAppTests {

        @Test
        @DisplayName("Should return false when packageJson is null")
        void shouldReturnFalseWhenPackageJsonNull() {
            assertFalse(detector.hasPrebuiltApp(null, "win-x64"));
        }

        @Test
        @DisplayName("Should return false when platform is null")
        void shouldReturnFalseWhenPlatformNull() {
            JSONObject packageJson = createPackageJson();
            assertFalse(detector.hasPrebuiltApp(packageJson, null));
        }

        @Test
        @DisplayName("Should return false when jdeploy section is missing")
        void shouldReturnFalseWhenJdeploySectionMissing() {
            JSONObject packageJson = new JSONObject();
            packageJson.put("name", "test-app");
            assertFalse(detector.hasPrebuiltApp(packageJson, "win-x64"));
        }

        @Test
        @DisplayName("Should return false when prebuiltApps array is missing")
        void shouldReturnFalseWhenPrebuiltAppsMissing() {
            JSONObject packageJson = new JSONObject();
            packageJson.put("jdeploy", new JSONObject());
            assertFalse(detector.hasPrebuiltApp(packageJson, "win-x64"));
        }

        @Test
        @DisplayName("Should return true when platform is in prebuiltApps array")
        void shouldReturnTrueWhenPlatformInArray() {
            JSONObject packageJson = createPackageJson("win-x64", "mac-arm64");
            assertTrue(detector.hasPrebuiltApp(packageJson, "win-x64"));
            assertTrue(detector.hasPrebuiltApp(packageJson, "mac-arm64"));
        }

        @Test
        @DisplayName("Should return false when platform is not in prebuiltApps array")
        void shouldReturnFalseWhenPlatformNotInArray() {
            JSONObject packageJson = createPackageJson("win-x64", "mac-arm64");
            assertFalse(detector.hasPrebuiltApp(packageJson, "linux-x64"));
        }
    }

    @Nested
    @DisplayName("getPrebuiltAppPlatforms tests")
    class GetPrebuiltAppPlatformsTests {

        @Test
        @DisplayName("Should return empty list when packageJson is null")
        void shouldReturnEmptyListWhenNull() {
            List<String> platforms = detector.getPrebuiltAppPlatforms(null);
            assertTrue(platforms.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when jdeploy section missing")
        void shouldReturnEmptyListWhenJdeployMissing() {
            JSONObject packageJson = new JSONObject();
            List<String> platforms = detector.getPrebuiltAppPlatforms(packageJson);
            assertTrue(platforms.isEmpty());
        }

        @Test
        @DisplayName("Should return all platforms from prebuiltApps array")
        void shouldReturnAllPlatforms() {
            JSONObject packageJson = createPackageJson("win-x64", "mac-arm64", "linux-x64");
            List<String> platforms = detector.getPrebuiltAppPlatforms(packageJson);
            assertEquals(3, platforms.size());
            assertTrue(platforms.contains("win-x64"));
            assertTrue(platforms.contains("mac-arm64"));
            assertTrue(platforms.contains("linux-x64"));
        }
    }

    @Nested
    @DisplayName("URL construction tests")
    class UrlConstructionTests {

        @Test
        @DisplayName("Should construct correct versioned download URL")
        void shouldConstructVersionedUrl() {
            String url = detector.getGitHubDownloadUrl(
                    "https://github.com/test/repo",
                    "test-app",
                    "1.2.3",
                    "win-x64"
            );
            assertEquals(
                    "https://github.com/test/repo/releases/download/v1.2.3/test-app-1.2.3-win-x64-bin.tgz",
                    url
            );
        }

        @Test
        @DisplayName("Should handle trailing slash in repository URL")
        void shouldHandleTrailingSlash() {
            String url = detector.getGitHubDownloadUrl(
                    "https://github.com/test/repo/",
                    "test-app",
                    "1.0.0",
                    "mac-arm64"
            );
            assertEquals(
                    "https://github.com/test/repo/releases/download/v1.0.0/test-app-1.0.0-mac-arm64-bin.tgz",
                    url
            );
        }

        @Test
        @DisplayName("Should construct correct jdeploy tag URL")
        void shouldConstructJdeployTagUrl() {
            String url = detector.getGitHubJdeployTagUrl(
                    "https://github.com/test/repo",
                    "test-app",
                    "1.2.3",
                    "linux-x64"
            );
            assertEquals(
                    "https://github.com/test/repo/releases/download/jdeploy/test-app-1.2.3-linux-x64-bin.tgz",
                    url
            );
        }
    }

    @Nested
    @DisplayName("tarball name tests")
    class TarballNameTests {

        @Test
        @DisplayName("Should generate correct tarball name")
        void shouldGenerateCorrectTarballName() {
            String name = detector.getTarballName("my-app", "2.0.0", "win-arm64");
            assertEquals("my-app-2.0.0-win-arm64-bin.tgz", name);
        }

        @Test
        @DisplayName("Should handle scoped package names")
        void shouldHandleScopedPackageName() {
            String name = detector.getTarballName("@org/my-app", "1.0.0", "mac-x64");
            assertEquals("@org/my-app-1.0.0-mac-x64-bin.tgz", name);
        }
    }

    @Nested
    @DisplayName("platform detection tests")
    class PlatformDetectionTests {

        @Test
        @DisplayName("Should return valid platform identifier")
        void shouldReturnValidPlatformIdentifier() {
            String platform = detector.detectCurrentPlatform();
            assertNotNull(platform);
            assertTrue(platform.matches("(win|mac|linux)-(x64|arm64)"),
                    "Platform should match pattern: " + platform);
        }
    }

    @Nested
    @DisplayName("GitHub source tests")
    class GitHubSourceTests {

        @Test
        @DisplayName("Should detect GitHub source")
        void shouldDetectGitHubSource() {
            JSONObject packageJson = new JSONObject();
            JSONObject jdeploy = new JSONObject();
            jdeploy.put("source", "https://github.com/user/repo#my-app");
            packageJson.put("jdeploy", jdeploy);

            assertTrue(detector.hasGitHubSource(packageJson));
        }

        @Test
        @DisplayName("Should return false for non-GitHub source")
        void shouldReturnFalseForNonGitHubSource() {
            JSONObject packageJson = new JSONObject();
            JSONObject jdeploy = new JSONObject();
            jdeploy.put("source", "https://gitlab.com/user/repo#my-app");
            packageJson.put("jdeploy", jdeploy);

            assertFalse(detector.hasGitHubSource(packageJson));
        }

        @Test
        @DisplayName("Should extract GitHub repository URL")
        void shouldExtractGitHubRepositoryUrl() {
            JSONObject packageJson = new JSONObject();
            JSONObject jdeploy = new JSONObject();
            jdeploy.put("source", "https://github.com/user/repo#my-app");
            packageJson.put("jdeploy", jdeploy);

            String repoUrl = detector.getGitHubRepositoryUrl(packageJson);
            assertEquals("https://github.com/user/repo", repoUrl);
        }

        @Test
        @DisplayName("Should return null for non-GitHub repository")
        void shouldReturnNullForNonGitHubRepo() {
            JSONObject packageJson = new JSONObject();
            JSONObject jdeploy = new JSONObject();
            jdeploy.put("source", "https://bitbucket.org/user/repo");
            packageJson.put("jdeploy", jdeploy);

            assertNull(detector.getGitHubRepositoryUrl(packageJson));
        }

        @Test
        @DisplayName("Should handle source without hash")
        void shouldHandleSourceWithoutHash() {
            JSONObject packageJson = new JSONObject();
            JSONObject jdeploy = new JSONObject();
            jdeploy.put("source", "https://github.com/user/repo");
            packageJson.put("jdeploy", jdeploy);

            String repoUrl = detector.getGitHubRepositoryUrl(packageJson);
            assertEquals("https://github.com/user/repo", repoUrl);
        }
    }

    // ==================== Helper Methods ====================

    private JSONObject createPackageJson(String... platforms) {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.0.0");

        JSONObject jdeploy = new JSONObject();
        JSONArray prebuiltApps = new JSONArray();
        for (String platform : platforms) {
            prebuiltApps.put(platform);
        }
        jdeploy.put("prebuiltApps", prebuiltApps);
        packageJson.put("jdeploy", jdeploy);

        return packageJson;
    }
}
