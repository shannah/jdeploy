# Create a Mock Network Publishing Test

Create a new mock network publishing test class for the jDeploy project. The user will describe the publishing feature or scenario they want to test. Follow this guide exactly.

## Architecture Overview

Mock network publishing tests run real jDeploy publish drivers against containerized mock services:

- **Verdaccio** (port 4873) — real npm registry for NPM publish tests
- **WireMock "GitHub"** (port 8080) — mocks GitHub API (releases, assets)
- **WireMock "jDeploy"** (port 8081) — mocks jDeploy registry (register.php, publish.php)

Tests make real HTTP calls. WireMock stubs return canned responses. The test infrastructure resets request journals and cleans up dynamic stubs between each test method automatically.

## Key Files to Read Before Writing Tests

Always read these files to understand the current infrastructure:

1. **Base class**: `cli/src/test/java/ca/weblite/jdeploy/publishing/BaseMockNetworkPublishingTest.java`
2. **WireMock client**: `cli/src/test/java/ca/weblite/jdeploy/publishing/WireMockAdminClient.java`
3. **Existing tests**: `cli/src/test/java/ca/weblite/jdeploy/publishing/MockNetworkPublishingTest.java`
4. **Static stubs**: `test-infra/wiremock/github/mappings/*.json` and `test-infra/wiremock/jdeploy/mappings/*.json`

Also read the production classes being tested to understand their API:
- `cli/src/main/java/ca/weblite/jdeploy/publishing/github/GitHubPublishDriver.java`
- `cli/src/main/java/ca/weblite/jdeploy/publishing/npm/NPMPublishDriver.java`
- `cli/src/main/java/ca/weblite/jdeploy/publishing/PublishingContext.java`
- `cli/src/main/java/ca/weblite/jdeploy/publishing/ResourceUploader.java`

## Step-by-Step Instructions

### 1. Create the Test Class

Place it in `cli/src/test/java/ca/weblite/jdeploy/publishing/` (or a subdirectory).

The class name MUST match one of these patterns (to be picked up by CI):
- `*MockNetwork*Test.java`
- `*MockPublish*Test.java`

```java
package ca.weblite.jdeploy.publishing;

import ca.weblite.jdeploy.appbundler.BundlerSettings;
import ca.weblite.jdeploy.publishing.github.GitHubPublishDriver;
import org.json.JSONObject;
import org.junit.jupiter.api.*;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class YourFeatureMockNetworkTest extends BaseMockNetworkPublishingTest {

    @Test
    @Order(10)
    @DisplayName("Descriptive name of what this test verifies")
    void testMethodName() throws Exception {
        // 1. Scaffold project
        // 2. Optionally add dynamic stubs to override defaults
        // 3. Run the publish driver
        // 4. Assert on artifacts and WireMock request journal
    }
}
```

### 2. Scaffold a Project

Use the base class helpers. Prefer `scaffoldProject()` for the common case:

```java
// Full scaffold: creates projectDir with JAR, package.json, icon.png, jdeploy-bundle/
File projectDir = scaffoldProject(
    "my-test-dir",        // subdirectory name under tempDir
    "my-package-name",    // npm package name
    "1.0.0",              // version
    "app-1.0.0.jar",      // JAR filename
    "com.example.Main"    // main class
);
```

For more control, use individual helpers:
```java
File projectDir = new File(tempDir, "custom-project");
projectDir.mkdirs();
File jarFile = createTestJar(projectDir, "app.jar", "com.example.Main");
File packageJsonFile = createPackageJson(projectDir, "my-pkg", "1.0.0", "app.jar", null);
createIcon(projectDir);
createJdeployBundle(projectDir, jarFile);
```

### 3. Override WireMock Stubs for Your Scenario

The static stubs (loaded from `test-infra/wiremock/`) provide these defaults:

| Stub | Method | URL Pattern | Default Response | Priority |
|------|--------|-------------|-----------------|----------|
| fetch-release-by-tag | GET | `/repos/[^/]+/[^/]+/releases/tags/.*` | 404 (not found) | 10 |
| create-release | POST | `/repos/[^/]+/[^/]+/releases` | 201 (created) | - |
| upload-asset | POST | `/uploads/repos/release-assets` | 201 (created) | - |
| download-asset | GET | `/api/assets/.*` | 200 with ETag | - |
| delete-asset | DELETE | `/api/assets/.*` | 204 | - |
| download-release-asset | GET | `/[owner]/[repo]/releases/download/.*` | 404 | 10 |
| release-tags-page | GET | `/[owner]/[repo]/releases/tags/.*` | 404 | 20 |
| register | GET | `/register\.php` | 200 with random code | - |
| publish | POST | `/publish.php` | 200 with code 200 | - |

To override a default, add a dynamic stub with **lower priority number** (= higher priority):

```java
// Example: make "fetch release by tag" return 200 instead of 404
// (simulates a release that already exists)
JSONObject existingRelease = new JSONObject()
    .put("id", 42)
    .put("tag_name", "jdeploy-1.0.0")
    .put("assets", new JSONArray());
githubWireMock.stubGetUrlMatching(
    "/repos/.*/releases/tags/.*",  // URL regex pattern
    200,                            // status code
    existingRelease,                // response body (JSONObject or null)
    1                               // priority (lower = wins over static stubs at 10)
);
```

Dynamic stubs are automatically cleaned up in `@AfterEach`, so they won't leak between tests.

Available stub methods on `githubWireMock` and `jdeployWireMock`:
- `stubGetUrlMatching(urlPattern, status, responseBody)` — priority defaults to 1
- `stubGetUrlMatching(urlPattern, status, responseBody, priority)`
- `stubPostUrlMatching(urlPattern, status, responseBody)`
- `stubPostUrlMatching(urlPattern, status, responseBody, priority)`
- `addStub(JSONObject fullStubMapping)` — for full control (custom headers, body patterns, etc.)

### 4. Wire Up and Run the Publish Driver

For GitHub publishing:
```java
GitHubPublishDriver driver = createGitHubPublishDriver();
PublishingContext ctx = createPublishingContext(projectDir);
PublishTargetInterface target = createGitHubTarget("testuser", "testrepo");

driver.prepare(ctx, target, new BundlerSettings());
driver.publish(ctx, target, null);
```

For NPM publishing:
```java
NPM npm = new NPM(System.out, System.err);
npm.setRegistryUrl(getNpmRegistry());
npm.publish(publishDir, false, null, null);
```

For resource uploads:
```java
Config config = createMockConfig();
PackagingConfig packagingConfig = new PackagingConfig(config);
ResourceUploader uploader = new ResourceUploader(packagingConfig);
uploader.uploadResources(publishingContext);
```

### 5. Write Assertions

#### Assert on artifacts (files produced by prepare/publish):
```java
File releaseFilesDir = ctx.getGithubReleaseFilesDir();
File packageInfoFile = new File(releaseFilesDir, "package-info.json");
assertTrue(packageInfoFile.exists(), "package-info.json should be generated");

JSONObject packageInfo = new JSONObject(
    FileUtils.readFileToString(packageInfoFile, StandardCharsets.UTF_8)
);
assertTrue(packageInfo.getJSONObject("versions").has("1.0.0"));
```

#### Assert on WireMock request journal (verify HTTP calls were made):
```java
// Verify a request was made at least once
githubWireMock.verifyRequestMade("POST", "/repos/.*/releases");

// Verify exact count
githubWireMock.verifyRequestCount("POST", "/repos/.*/releases", 1);

// Verify a request was NOT made
githubWireMock.verifyNoRequestMade("DELETE", "/repos/.*/releases/assets/.*");

// Inspect request details (body, headers, etc.)
List<JSONObject> requests = githubWireMock.findRequestsMatching(
    "POST", "/repos/.*/releases"
);
for (JSONObject req : requests) {
    JSONObject requestBody = new JSONObject(req.getJSONObject("request").getString("body"));
    assertEquals("jdeploy-1.0.0", requestBody.getString("tag_name"));
}
```

### 6. Testing Error Scenarios

To test error handling, override stubs to return error responses:

```java
// Simulate GitHub API rate limiting
githubWireMock.stubPostUrlMatching("/repos/.*/releases", 403,
    new JSONObject().put("message", "API rate limit exceeded"));

// Simulate jDeploy registry down
jdeployWireMock.stubGetUrlMatching("/register\\.php", 500, null);

// Then assert the driver handles the error correctly
assertThrows(SomeExpectedException.class, () -> {
    driver.publish(ctx, target, null);
});
```

## Running Tests

### Locally with Docker Compose:
```bash
cd test-infra && ./run-mock-network-tests.sh --up     # start services
cd test-infra && ./run-mock-network-tests.sh --local   # run tests
cd test-infra && ./run-mock-network-tests.sh --down    # stop services
```

### Entirely in Docker:
```bash
cd test-infra && ./run-mock-network-tests.sh
```

### In CI:
Tests run automatically via `.github/workflows/mock-network-tests.yml` on push/PR to master.

## Checklist Before Finishing

- [ ] Class extends `BaseMockNetworkPublishingTest`
- [ ] Class name matches `*MockNetwork*` or `*MockPublish*` pattern
- [ ] Package is `ca.weblite.jdeploy.publishing` (or subpackage)
- [ ] Each test method has `@Test`, `@Order(N)`, and `@DisplayName`
- [ ] Dynamic stubs use priority < 10 to override static defaults
- [ ] Assertions use `githubWireMock.verify*()` / `jdeployWireMock.verify*()`  for HTTP verification
- [ ] No new WireMock static stubs needed (use dynamic stubs per test instead)
- [ ] Java 8 compatible (no `var`, no `List.of()`, no `Map.of()`, etc.)

## Adding New Static Stubs (rare)

Only add static stubs in `test-infra/wiremock/*/mappings/` if ALL tests need the same default response for a new endpoint. Prefer dynamic per-test stubs for scenario-specific behavior.

If you do add a static stub, include `"transformers": ["response-template"]` in the response only if it uses WireMock template expressions (e.g., `{{jsonPath request.body '$.field'}}`).
