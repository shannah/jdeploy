# Launcher Splash HTML Implementation Plan

> **Status:** ✅ **IMPLEMENTATION COMPLETE** (Phase 1 & 2) - October 7, 2025
>
> Core implementation finished. Testing and documentation pending.

## Overview
This document describes the implementation plan for adding HTML splash screen support to the jDeploy launcher (Client4JLauncher). This splash screen is displayed in a WebView during app/JVM update downloads.

## Background

### Three Types of Splash Screens in jDeploy
1. **`splash.png/jpg/gif`** - Java application splash screen (shown when the deployed app launches)
2. **`installsplash.png`** - Installer application splash screen (the installer itself is a Java app)
3. **`launcher-splash.html`** (NEW) - HTML page displayed during launcher update/download progress

## Architecture

### Client4JLauncher Integration
- Client4JLauncher (separate project) displays progress during app/JVM downloads
- It reads the `splash` attribute from `app.xml` containing a data URI in format: `data:text/html;base64,<base64-encoded-html>`
- The launcher renders this HTML in a WebView while downloads are in progress

### jDeploy's Role
1. Read `launcher-splash.html` file from project directory
2. Convert HTML to base64-encoded data URI
3. Add `splash` attribute to `app.xml`
4. Publish the file so installers can access it at runtime

## File Naming
**Recommended:** `launcher-splash.html`
- Clearly indicates it's for the launcher phase (not installer or app)
- Located in project root directory alongside `icon.png`, `splash.png`, etc.

## Implementation Phases

### Phase 1: GitHub Publishing (Initial Implementation)
Support launcher splash HTML for projects using GitHub releases for distribution.

### Phase 2: NPM Publishing (Future)
Add support for uploading launcher splash HTML to jdeploy.com for NPM-distributed apps.

---

## Phase 1 Implementation Details

### 1. AppDescription.java
**File:** `shared/src/main/java/ca/weblite/jdeploy/appbundler/AppDescription.java`

Add splash data URI field:

```java
private String splashDataURI;

public String getSplashDataURI() {
    return splashDataURI;
}

public void setSplashDataURI(String splashDataURI) {
    this.splashDataURI = splashDataURI;
}
```

### 2. LauncherWriterHelper.java
**File:** `shared/src/main/java/ca/weblite/jdeploy/helpers/LauncherWriterHelper.java`
**Lines:** 52-73

Update `processAppXml()` method to include splash attribute in app.xml:

```java
private static void processAppXml(AppDescription app, File dest) throws Exception {
    p("Processing the app.xml file");
    XMLWriter out = new XMLWriter(dest);
    out.header();

    if (app.getNpmPackage() != null && app.getNpmVersion() != null) {
        out.start("app",
                "name", app.getName(),
                "package", app.getNpmPackage(),
                "source", app.getNpmSource(),
                "version", app.getNpmVersion(),
                "icon", app.getIconDataURI(),
                "splash", app.getSplashDataURI(),  // ADD THIS
                "prerelease", app.isNpmPrerelease()+"",
                "registry-url", app.getJDeployRegistryUrl(),
                "fork", ""+app.isFork()
        ).end();
    } else {
        out.start("app",
                "name", app.getName(),
                "url", app.getUrl(),
                "icon", app.getIconDataURI(),
                "splash", app.getSplashDataURI()  // ADD THIS
        ).end();
    }
    out.close();
}
```

**Note:** Handle null splash values gracefully (XMLWriter should skip null attributes).

### 3. Bundler.java
**File:** `shared/src/main/java/ca/weblite/jdeploy/appbundler/Bundler.java`

#### Add HTML to Data URI conversion method (around line 82):

```java
private static String toHtmlDataURI(URL url) throws IOException {
    return "data:text/html;base64," + Base64.getEncoder().encodeToString(IOUtils.toByteArray(url));
}
```

#### Update `createAppDescription()` method (around line 96):

```java
URL iconURL = URLUtil.url(appInfo.getAppURL(), "icon.png");
app.setIconDataURI(toDataURI(iconURL));

// Add splash HTML if available
try {
    URL splashURL = URLUtil.url(appInfo.getAppURL(), "launcher-splash.html");
    app.setSplashDataURI(toHtmlDataURI(splashURL));
} catch (Exception e) {
    // Splash is optional, ignore if not found
    app.setSplashDataURI(null);
}
```

### 4. PackageService.java
**File:** `cli/src/main/java/ca/weblite/jdeploy/packaging/PackageService.java`

#### Add launcher splash bundling (around line 335):

Similar to how `installsplash.png` and `icon.png` are bundled, add:

```java
File bundledLauncherSplashFile = null;
{
    File jarFile = new File(context.directory, toNativePath(context.getString("jar", null)));
    File absoluteParent = jarFile.getAbsoluteFile().getParentFile();
    File launcherSplashFile = new File(absoluteParent, "launcher-splash.html");

    if (launcherSplashFile.exists()) {
        File bin = context.getJdeployBundleDir();
        bundledLauncherSplashFile = new File(bin, "launcher-splash.html");
        FileUtils.copyFile(launcherSplashFile, bundledLauncherSplashFile);
    }
}
```

#### Add to archive (around line 383):

```java
if (bundledLauncherSplashFile != null && bundledLauncherSplashFile.exists()) {
    filesToAdd.add(new ArchiveUtil.ArchiveFile(
        bundledLauncherSplashFile,
        newName + "/.jdeploy-files/launcher-splash.html"
    ));
}
```

### 5. GitHubPublishDriver.java
**File:** `cli/src/main/java/ca/weblite/jdeploy/publishing/github/GitHubPublishDriver.java`
**Lines:** 272-280

Copy launcher splash to release files directory:

```java
File installSplash = new File(context.directory(),"installsplash.png");
File launcherSplash = new File(context.directory(),"launcher-splash.html");

// ... existing installSplash code ...

if (launcherSplash.exists()) {
    FileUtils.copyFile(launcherSplash, new File(releaseFilesDir, launcherSplash.getName()));
}
```

### 6. BasePublishDriver.java
**File:** `cli/src/main/java/ca/weblite/jdeploy/publishing/BasePublishDriver.java`
**Lines:** 95-97

Add checksum calculation for launcher splash:

```java
File installSplash = new File(context.packagingContext.directory, "installsplash.png");
if (installSplash.exists()) {
    checksums.put("installsplash.png", MD5.getMD5Checksum(installSplash));
}

File launcherSplash = new File(context.packagingContext.directory, "launcher-splash.html");
if (launcherSplash.exists()) {
    checksums.put("launcher-splash.html", MD5.getMD5Checksum(launcherSplash));
}
```

### 7. GUI Editor (Optional Enhancement)
**File:** `cli/src/main/java/ca/weblite/jdeploy/gui/JDeployProjectEditor.java`

Add UI controls for selecting launcher splash HTML file.

#### Add field (around line 123):

```java
private JButton icon, installSplash, splash, launcherSplash, selectJar;
```

#### Add accessor method (around line 306):

```java
private File getLauncherSplashFile() {
    return new File(packageJSONFile.getAbsoluteFile().getParentFile(), "launcher-splash.html");
}
```

#### Add button initialization (around line 915):

```java
mainFields.launcherSplash = new JButton();
if (getLauncherSplashFile().exists()) {
    mainFields.launcherSplash.setText("launcher-splash.html (present)");
} else {
    mainFields.launcherSplash.setText("Select launcher splash HTML...");
}

mainFields.launcherSplash.addActionListener(evt->{
    File selected = showFileChooser("Select Launcher Splash HTML", "html");
    if (selected == null) {
        return;
    }
    try {
        FileUtils.copyFile(selected, getLauncherSplashFile());
        mainFields.launcherSplash.setText("launcher-splash.html (present)");
    } catch (Exception ex) {
        ex.printStackTrace();
    }
});
```

#### Add to form layout (around line 1353):

```java
.add("Install Splash Screen", mainFields.installSplash)
.add("Launcher Splash (HTML)", mainFields.launcherSplash)
.add("Splash Screen", mainFields.splash)
```

---

## Phase 2 Implementation Details (NPM Publishing)

### 8. ResourceUploader.java
**File:** `cli/src/main/java/ca/weblite/jdeploy/publishing/ResourceUploader.java`
**Lines:** 31-84

**Context**: ResourceUploader is called automatically during NPM publishing via PublishService.java:92-93. It uploads icon.png and installsplash.png to jdeploy.com's `publish.php` API endpoint as base64-encoded strings in a JSON payload.

**Changes needed**:

```java
public void uploadResources(PublishingContext context) throws IOException {
    File icon = new File(context.directory(), "icon.png");
    File installSplash = new File(context.directory(),"installsplash.png");
    File launcherSplash = new File(context.directory(),"launcher-splash.html");  // ADD THIS
    File publishDir = new File(context.directory(), "jdeploy" + File.separator + "publish");
    JSONObject packageJSON = new JSONObject(FileUtils.readFileToString(new File(publishDir, "package.json"), "UTF-8"));

    if (icon.exists() || installSplash.exists() || launcherSplash.exists()) {  // UPDATE CONDITION
        // If there is a custom icon or install splash we need to upload
        // them to jdeploy.com so that they are available when generating
        // the installer.  Without this, jdeploy.com would need to download the
        // full package from npm and extract the icon and installsplash from there.
        JSONObject jdeployFiles = new JSONObject();
        byte[] iconBytes = FileUtils.readFileToByteArray(icon);
        jdeployFiles.put("icon.png", Base64.getEncoder().encodeToString(iconBytes));
        if (installSplash.exists()) {
            byte[] splashBytes = FileUtils.readFileToByteArray(installSplash);
            jdeployFiles.put("installsplash.png", Base64.getEncoder().encodeToString(splashBytes));
        }
        // ADD THIS BLOCK
        if (launcherSplash.exists()) {
            byte[] launcherSplashBytes = FileUtils.readFileToByteArray(launcherSplash);
            jdeployFiles.put("launcher-splash.html", Base64.getEncoder().encodeToString(launcherSplashBytes));
        }
        jdeployFiles.put("packageName", packageJSON.get("name"));
        jdeployFiles.put("version", VersionCleaner.cleanVersion(""+packageJSON.get("version")));
        try {
            context.out().println("Uploading icon to jdeploy.com...");
            // ... rest of existing code ...
```

**How it works**:
1. When publishing to NPM, PublishService checks if `target.getType().requiresAssetsUploadToJdeployServer()` returns true (which it does for NPM)
2. Calls `resourceUploader.uploadResources(context)`
3. ResourceUploader reads icon.png, installsplash.png, and launcher-splash.html
4. Base64 encodes all files
5. Sends JSON payload to `https://www.jdeploy.com/publish.php` with structure:
   ```json
   {
     "icon.png": "base64encodeddata...",
     "installsplash.png": "base64encodeddata...",
     "launcher-splash.html": "base64encodeddata...",
     "packageName": "my-package",
     "version": "1.0.0"
   }
   ```
6. jdeploy.com server stores these files
7. When installer requests files via `download.php?code=X&version=Y&jdeploy_files=true`, the server includes launcher-splash.html in the `.jdeploy-files.zip`

**Server-side considerations** (outside this codebase):
- The jdeploy.com `publish.php` endpoint should already handle any files in the JSON payload generically
- No server-side code changes should be needed unless there's specific validation for allowed file names
- The `download.php` endpoint needs to include launcher-splash.html when building the jdeploy-files.zip

---

## HTML File Requirements

### Self-Contained HTML
The `launcher-splash.html` file must be completely self-contained:
- **Inline CSS:** All styles must be in `<style>` tags or inline
- **Base64 Images:** All images must be embedded as base64 data URIs
- **No External Resources:** No external scripts, stylesheets, fonts, or images
- **No JavaScript:** JavaScript may not be supported or may be disabled for security

### Example Template

```html
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <style>
        body {
            margin: 0;
            padding: 40px;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: white;
            text-align: center;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            justify-content: center;
            align-items: center;
        }
        h1 {
            font-size: 2.5em;
            margin-bottom: 20px;
        }
        .logo {
            width: 120px;
            height: 120px;
            margin-bottom: 30px;
        }
        .message {
            font-size: 1.2em;
            opacity: 0.9;
        }
    </style>
</head>
<body>
    <img class="logo" src="data:image/png;base64,iVBORw0KG..." alt="Logo">
    <h1>Updating Your Application</h1>
    <p class="message">Please wait while we download the latest updates...</p>
</body>
</html>
```

### Size Recommendations
- Keep file size reasonable (< 500KB recommended)
- Larger files increase bundle size and download time
- Use optimized/compressed images when converting to base64

---

## Testing Plan

### Unit Tests

#### 1. AppDescription Tests
**File:** `shared/src/test/java/ca/weblite/jdeploy/appbundler/AppDescriptionTest.java`

```java
@Test
public void testSplashDataURIGetterSetter() {
    AppDescription app = new AppDescription();
    String testDataURI = "data:text/html;base64,PGh0bWw+PGJvZHk+VGVzdDwvYm9keT48L2h0bWw+";

    app.setSplashDataURI(testDataURI);
    assertEquals(testDataURI, app.getSplashDataURI());
}

@Test
public void testSplashDataURIDefaultsToNull() {
    AppDescription app = new AppDescription();
    assertNull(app.getSplashDataURI());
}
```

**Purpose:** Verify the splash data URI field works correctly with null and non-null values.

#### 2. Bundler Tests
**File:** `shared/src/test/java/ca/weblite/jdeploy/appbundler/BundlerTest.java`

```java
@Test
public void testToHtmlDataURIEncodesCorrectly() throws IOException {
    String htmlContent = "<!DOCTYPE html><html><body>Test</body></html>";
    URL mockURL = createMockURL(htmlContent);

    String result = Bundler.toHtmlDataURI(mockURL);

    assertTrue(result.startsWith("data:text/html;base64,"));
    String decoded = new String(Base64.getDecoder().decode(
        result.substring("data:text/html;base64,".length())
    ));
    assertEquals(htmlContent, decoded);
}

@Test
public void testCreateAppDescriptionWithLauncherSplash() throws IOException {
    // Setup mock AppInfo with launcher-splash.html available
    AppInfo mockAppInfo = createMockAppInfo(true);

    AppDescription result = Bundler.createAppDescription(mockAppInfo, "file:///test");

    assertNotNull(result.getSplashDataURI());
    assertTrue(result.getSplashDataURI().startsWith("data:text/html;base64,"));
}

@Test
public void testCreateAppDescriptionWithoutLauncherSplash() throws IOException {
    // Setup mock AppInfo without launcher-splash.html
    AppInfo mockAppInfo = createMockAppInfo(false);

    AppDescription result = Bundler.createAppDescription(mockAppInfo, "file:///test");

    assertNull(result.getSplashDataURI());
    // Should not throw exception
}

@Test
public void testToHtmlDataURIHandlesSpecialCharacters() throws IOException {
    String htmlContent = "<!DOCTYPE html><html><body>Test with 中文 and émojis 🎉</body></html>";
    URL mockURL = createMockURL(htmlContent);

    String result = Bundler.toHtmlDataURI(mockURL);
    String decoded = new String(Base64.getDecoder().decode(
        result.substring("data:text/html;base64,".length())
    ), StandardCharsets.UTF_8);
    assertEquals(htmlContent, decoded);
}

@Test
public void testToHtmlDataURIHandlesLargeFiles() throws IOException {
    // Test with 500KB HTML file
    String htmlContent = generateLargeHtml(500 * 1024);
    URL mockURL = createMockURL(htmlContent);

    String result = Bundler.toHtmlDataURI(mockURL);
    assertNotNull(result);
    assertTrue(result.length() > htmlContent.length()); // Base64 is larger
}
```

**Purpose:** Test HTML to base64 encoding and integration with AppDescription creation.

#### 3. LauncherWriterHelper Tests
**File:** `shared/src/test/java/ca/weblite/jdeploy/helpers/LauncherWriterHelperTest.java`

```java
@Test
public void testProcessAppXmlIncludesSplashForNpmApp() throws Exception {
    AppDescription app = new AppDescription();
    app.setName("TestApp");
    app.setNpmPackage("test-package");
    app.setNpmVersion("1.0.0");
    app.setIconDataURI("data:image/png;base64,test");
    app.setSplashDataURI("data:text/html;base64,PHRlc3Q+");

    File tempXml = File.createTempFile("app", ".xml");
    LauncherWriterHelper.processAppXml(app, tempXml);

    String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
    assertTrue(xmlContent.contains("splash=\"data:text/html;base64,PHRlc3Q+\""));

    tempXml.delete();
}

@Test
public void testProcessAppXmlIncludesSplashForUrlApp() throws Exception {
    AppDescription app = new AppDescription();
    app.setName("TestApp");
    app.setUrl("http://example.com/app.xml");
    app.setIconDataURI("data:image/png;base64,test");
    app.setSplashDataURI("data:text/html;base64,PHRlc3Q+");

    File tempXml = File.createTempFile("app", ".xml");
    LauncherWriterHelper.processAppXml(app, tempXml);

    String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
    assertTrue(xmlContent.contains("splash=\"data:text/html;base64,PHRlc3Q+\""));

    tempXml.delete();
}

@Test
public void testProcessAppXmlHandlesNullSplash() throws Exception {
    AppDescription app = new AppDescription();
    app.setName("TestApp");
    app.setNpmPackage("test-package");
    app.setNpmVersion("1.0.0");
    app.setIconDataURI("data:image/png;base64,test");
    app.setSplashDataURI(null);

    File tempXml = File.createTempFile("app", ".xml");
    LauncherWriterHelper.processAppXml(app, tempXml);

    String xmlContent = FileUtils.readFileToString(tempXml, "UTF-8");
    // XMLWriter should omit null attributes
    assertFalse(xmlContent.contains("splash="));

    tempXml.delete();
}
```

**Purpose:** Verify app.xml generation includes splash attribute correctly.

#### 4. PackageService Tests
**File:** `cli/src/test/java/ca/weblite/jdeploy/packaging/PackageServiceTest.java`

```java
@Test
public void testBundleLauncherSplashWhenPresent() throws Exception {
    // Setup test project with launcher-splash.html
    File testProject = createTestProject();
    File launcherSplash = new File(testProject, "launcher-splash.html");
    FileUtils.write(launcherSplash, "<html><body>Test</body></html>", "UTF-8");

    PackagingContext context = createMockContext(testProject);
    PackageService service = new PackageService();

    service.packageInstaller(context, "mac-amd64");

    File bundledFile = new File(context.getJdeployBundleDir(), "launcher-splash.html");
    assertTrue("launcher-splash.html should be bundled", bundledFile.exists());
    assertEquals("<html><body>Test</body></html>",
                 FileUtils.readFileToString(bundledFile, "UTF-8"));
}

@Test
public void testBundleWithoutLauncherSplash() throws Exception {
    // Setup test project without launcher-splash.html
    File testProject = createTestProject();

    PackagingContext context = createMockContext(testProject);
    PackageService service = new PackageService();

    // Should not throw exception
    service.packageInstaller(context, "mac-amd64");

    File bundledFile = new File(context.getJdeployBundleDir(), "launcher-splash.html");
    assertFalse("launcher-splash.html should not be bundled", bundledFile.exists());
}

@Test
public void testLauncherSplashAddedToArchive() throws Exception {
    File testProject = createTestProject();
    File launcherSplash = new File(testProject, "launcher-splash.html");
    FileUtils.write(launcherSplash, "<html><body>Test</body></html>", "UTF-8");

    PackagingContext context = createMockContext(testProject);
    PackageService service = new PackageService();

    File installerArchive = service.packageInstaller(context, "mac-amd64");

    // Verify archive contains launcher-splash.html in .jdeploy-files/
    List<String> archiveContents = listArchiveContents(installerArchive);
    assertTrue(archiveContents.stream()
        .anyMatch(path -> path.endsWith("/.jdeploy-files/launcher-splash.html")));
}
```

**Purpose:** Test bundling and archiving of launcher splash HTML file.

#### 5. GitHubPublishDriver Tests
**File:** `cli/src/test/java/ca/weblite/jdeploy/publishing/github/GitHubPublishDriverTest.java`

```java
@Test
public void testSaveGithubReleaseFilesIncludesLauncherSplash() throws Exception {
    File testProject = createTestProject();
    File launcherSplash = new File(testProject, "launcher-splash.html");
    FileUtils.write(launcherSplash, "<html><body>Test</body></html>", "UTF-8");

    PublishingContext context = createMockPublishingContext(testProject);
    GitHubPublishDriver driver = new GitHubPublishDriver();

    driver.saveGithubReleaseFiles(context, mockTarget);

    File releaseFile = new File(context.getGithubReleaseFilesDir(), "launcher-splash.html");
    assertTrue("launcher-splash.html should be in release files", releaseFile.exists());
    assertEquals("<html><body>Test</body></html>",
                 FileUtils.readFileToString(releaseFile, "UTF-8"));
}

@Test
public void testSaveGithubReleaseFilesWithoutLauncherSplash() throws Exception {
    File testProject = createTestProject();

    PublishingContext context = createMockPublishingContext(testProject);
    GitHubPublishDriver driver = new GitHubPublishDriver();

    // Should not throw exception
    driver.saveGithubReleaseFiles(context, mockTarget);

    File releaseFile = new File(context.getGithubReleaseFilesDir(), "launcher-splash.html");
    assertFalse("launcher-splash.html should not be in release files", releaseFile.exists());
}
```

**Purpose:** Verify launcher splash is copied to GitHub release files directory.

#### 6. BasePublishDriver Tests
**File:** `cli/src/test/java/ca/weblite/jdeploy/publishing/BasePublishDriverTest.java`

```java
@Test
public void testChecksumIncludesLauncherSplash() throws Exception {
    File testProject = createTestProject();
    File launcherSplash = new File(testProject, "launcher-splash.html");
    FileUtils.write(launcherSplash, "<html><body>Test</body></html>", "UTF-8");

    PublishingContext context = createMockPublishingContext(testProject);
    BasePublishDriver driver = new BasePublishDriver();

    Map<String, String> checksums = driver.preparePackageJson(context);

    assertTrue("Checksums should include launcher-splash.html",
               checksums.containsKey("launcher-splash.html"));
    assertNotNull(checksums.get("launcher-splash.html"));
    // Verify it's a valid MD5 checksum (32 hex characters)
    assertEquals(32, checksums.get("launcher-splash.html").length());
}

@Test
public void testChecksumWithoutLauncherSplash() throws Exception {
    File testProject = createTestProject();

    PublishingContext context = createMockPublishingContext(testProject);
    BasePublishDriver driver = new BasePublishDriver();

    Map<String, String> checksums = driver.preparePackageJson(context);

    assertFalse("Checksums should not include launcher-splash.html",
                checksums.containsKey("launcher-splash.html"));
}

@Test
public void testChecksumChangesWhenContentChanges() throws Exception {
    File testProject = createTestProject();
    File launcherSplash = new File(testProject, "launcher-splash.html");

    FileUtils.write(launcherSplash, "<html><body>Version 1</body></html>", "UTF-8");
    PublishingContext context1 = createMockPublishingContext(testProject);
    String checksum1 = new BasePublishDriver().preparePackageJson(context1)
        .get("launcher-splash.html");

    FileUtils.write(launcherSplash, "<html><body>Version 2</body></html>", "UTF-8");
    PublishingContext context2 = createMockPublishingContext(testProject);
    String checksum2 = new BasePublishDriver().preparePackageJson(context2)
        .get("launcher-splash.html");

    assertNotEquals("Checksums should differ for different content", checksum1, checksum2);
}
```

**Purpose:** Test MD5 checksum calculation for launcher splash file.

#### 7. ResourceUploader Tests
**File:** `cli/src/test/java/ca/weblite/jdeploy/publishing/ResourceUploaderTest.java`

```java
@Test
public void testUploadResourcesIncludesLauncherSplash() throws Exception {
    File testProject = createTestProject();
    File launcherSplash = new File(testProject, "launcher-splash.html");
    String htmlContent = "<html><body>Test Upload</body></html>";
    FileUtils.write(launcherSplash, htmlContent, "UTF-8");

    PublishingContext context = createMockPublishingContext(testProject);
    ResourceUploader uploader = new ResourceUploader(mockConfig);

    // Mock the HTTP call to jdeploy.com
    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("{\"status\":\"success\"}"));

    uploader.uploadResources(context);

    RecordedRequest request = server.takeRequest();
    String requestBody = request.getBody().readUtf8();
    JSONObject payload = new JSONObject(requestBody);

    assertTrue("Payload should include launcher-splash.html",
               payload.has("launcher-splash.html"));

    String encodedHtml = payload.getString("launcher-splash.html");
    String decodedHtml = new String(Base64.getDecoder().decode(encodedHtml), "UTF-8");
    assertEquals(htmlContent, decodedHtml);
}

@Test
public void testUploadResourcesWithoutLauncherSplash() throws Exception {
    File testProject = createTestProject();

    PublishingContext context = createMockPublishingContext(testProject);
    ResourceUploader uploader = new ResourceUploader(mockConfig);

    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("{\"status\":\"success\"}"));

    uploader.uploadResources(context);

    RecordedRequest request = server.takeRequest();
    String requestBody = request.getBody().readUtf8();
    JSONObject payload = new JSONObject(requestBody);

    assertFalse("Payload should not include launcher-splash.html",
                payload.has("launcher-splash.html"));
    // But should still include icon
    assertTrue("Payload should include icon.png", payload.has("icon.png"));
}

@Test
public void testUploadResourcesHandlesLargeHtml() throws Exception {
    File testProject = createTestProject();
    File launcherSplash = new File(testProject, "launcher-splash.html");
    String largeHtml = generateLargeHtml(500 * 1024); // 500KB
    FileUtils.write(launcherSplash, largeHtml, "UTF-8");

    PublishingContext context = createMockPublishingContext(testProject);
    ResourceUploader uploader = new ResourceUploader(mockConfig);

    MockWebServer server = new MockWebServer();
    server.enqueue(new MockResponse().setBody("{\"status\":\"success\"}"));

    // Should not throw exception
    uploader.uploadResources(context);

    RecordedRequest request = server.takeRequest();
    assertNotNull(request);
}
```

**Purpose:** Test NPM resource upload includes launcher splash as base64-encoded data.

### Test Coverage Goals
- **Line Coverage:** >80% for modified files
- **Branch Coverage:** >70% for conditional logic
- **Edge Cases:**
  - Missing launcher-splash.html file
  - Null splash data URI
  - Empty HTML file
  - Large HTML files (>500KB)
  - Special characters and Unicode in HTML
  - Malformed HTML (should still encode)

### Integration Tests

#### GitHub Publishing Tests
1. Create test project with `launcher-splash.html` file
2. Run packaging and verify:
   - HTML file is copied to `.jdeploy-files/launcher-splash.html`
   - `app.xml` contains correct `splash` attribute with base64 data URI
   - Base64 decoding produces valid HTML
3. Test GitHub publishing:
   - Verify `launcher-splash.html` is included in release files
   - Verify checksum is calculated correctly
4. Test with missing splash file:
   - Verify build succeeds without errors
   - Verify `app.xml` omits splash attribute or sets to null

#### NPM Publishing Tests (Phase 2)
1. Create test project with `launcher-splash.html` file
2. Run `jdeploy publish` (NPM target)
3. Verify ResourceUploader is called
4. Verify launcher-splash.html is base64 encoded and sent to jdeploy.com
5. Check jdeploy.com API response for success
6. Verify installer can download `.jdeploy-files.zip` containing launcher-splash.html
7. Test with missing launcher-splash.html (should still succeed with icon/installsplash only)

### End-to-End Testing

#### GitHub Workflow
1. Create test app with custom launcher splash HTML
2. Publish to GitHub releases
3. Install app using jDeploy installer
4. Trigger update and verify HTML splash displays in Client4JLauncher

#### NPM Workflow
1. Create test app with custom launcher splash HTML
2. Publish to NPM
3. Install app using jDeploy installer
4. Trigger update and verify HTML splash displays in Client4JLauncher
5. Verify HTML is loaded from jdeploy.com cached files

---

## Implementation Status

### Phase 1: GitHub Publishing
- [x] AppDescription.java - Add splashDataURI field ✅ **COMPLETED** (2025-10-07)
- [x] LauncherWriterHelper.java - Add splash attribute to app.xml ✅ **COMPLETED** (2025-10-07)
- [x] Bundler.java - Add toHtmlDataURI() method ✅ **COMPLETED** (2025-10-07)
- [x] Bundler.java - Set splash data URI in createAppDescription() ✅ **COMPLETED** (2025-10-07)
- [x] PackageService.java - Bundle launcher splash file ✅ **COMPLETED** (2025-10-07)
- [x] GitHubPublishDriver.java - Copy to release files ✅ **COMPLETED** (2025-10-07)
- [x] BasePublishDriver.java - Add checksum calculation ✅ **COMPLETED** (2025-10-07)
- [ ] GUI Editor - Add launcher splash selection (optional) ⏸️ **DEFERRED**
- [ ] Testing - Unit and integration tests ⏸️ **PENDING**
- [ ] Documentation - User-facing docs and examples ⏸️ **PENDING**

### Phase 2: NPM Publishing
- [x] ResourceUploader.java - Upload to jdeploy.com ✅ **COMPLETED** (2025-10-07)
- [ ] Testing - Verify NPM publishing workflow ⏸️ **PENDING**
- [ ] Documentation - Update docs for NPM workflow ⏸️ **PENDING**

### Implementation Notes

**Date:** October 7, 2025
**Status:** Core implementation complete, testing and documentation pending

**Changes Made:**
1. **AppDescription.java** - Added `splashDataURI` field with getter/setter methods
2. **Bundler.java** - Added `toHtmlDataURI()` method and updated `createAppDescription()` to optionally load and encode `launcher-splash.html`
3. **LauncherWriterHelper.java** - Updated `processAppXml()` to include `splash` attribute in both NPM and URL-based app configurations
4. **PackageService.java** - Added bundling logic to copy `launcher-splash.html` to `.jdeploy-files/` directory when present
5. **GitHubPublishDriver.java** - Added file copy to GitHub release files directory
6. **BasePublishDriver.java** - Added MD5 checksum calculation for `launcher-splash.html`
7. **ResourceUploader.java** - Added NPM publishing support to upload HTML file as base64-encoded data to jdeploy.com

**Build Status:**
- ✅ Shared module compiled successfully with Java 1.8
- ✅ CLI module compiled successfully with Java 1.8
- No compilation errors

**Backward Compatibility:**
- All changes are backward compatible
- `launcher-splash.html` is optional - builds succeed without it
- Existing projects continue to work without modification

**Next Steps:**
1. End-to-end testing with Client4JLauncher
2. Create example `launcher-splash.html` templates
3. Update user documentation
4. Optional: Add GUI editor support for selecting launcher splash HTML

---

## Documentation Requirements

### User Documentation
- [ ] Document `launcher-splash.html` purpose and location
- [ ] Provide example HTML templates
- [ ] Explain self-contained HTML requirements
- [ ] Document size recommendations
- [ ] Clarify distinction between three splash screen types
- [ ] Add troubleshooting guide

### Developer Documentation
- [ ] Document app.xml schema change (splash attribute)
- [ ] Document data URI format: `data:text/html;base64,...`
- [ ] Update bundling documentation
- [ ] Update publishing workflow docs

---

## Security Considerations

### HTML Content
- HTML is converted to base64 and embedded in app.xml
- No server-side rendering or processing
- Client4JLauncher is responsible for:
  - Disabling JavaScript execution
  - Preventing navigation/link following
  - Sandboxing WebView rendering

### File Size Limits
- Consider adding validation for maximum file size
- Large files impact bundle size and user experience
- Recommend compression for images

---

## Future Enhancements

### Potential Improvements
1. Support multiple splash screens (different per platform/theme)
2. Add splash screen validation tools
3. Provide splash screen gallery/templates
4. Support dynamic content (e.g., progress bars via special markup)
5. Add preview functionality in GUI editor

---

## References

### Related Files
- `shared/src/main/java/ca/weblite/jdeploy/appbundler/AppDescription.java` - App metadata
- `shared/src/main/java/ca/weblite/jdeploy/helpers/LauncherWriterHelper.java` - app.xml generation
- `shared/src/main/java/ca/weblite/jdeploy/appbundler/Bundler.java` - Bundling logic
- `cli/src/main/java/ca/weblite/jdeploy/packaging/PackageService.java` - Packaging
- `cli/src/main/java/ca/weblite/jdeploy/publishing/github/GitHubPublishDriver.java` - GitHub publishing
- `cli/src/main/java/ca/weblite/jdeploy/publishing/ResourceUploader.java` - NPM asset upload to jdeploy.com
- `cli/src/main/java/ca/weblite/jdeploy/publishing/PublishService.java` - Orchestrates publishing and calls ResourceUploader
- `cli/src/main/java/ca/weblite/jdeploy/publishing/BasePublishDriver.java` - Checksum generation
- `installer/src/main/java/ca/weblite/jdeploy/installer/DefaultInstallationContext.java` - Downloads .jdeploy-files.zip

### External Dependencies
- **Client4JLauncher** - Consumes the splash attribute from app.xml
- **XMLWriter** - Writes app.xml with attributes
- **URLUtil** - Resolves file URLs
- **IOUtils/FileUtils** - File operations
- **jdeploy.com API** - `publish.php` and `download.php` endpoints for NPM publishing

---

## Contact

For questions or clarifications about this implementation plan, please refer to the jDeploy project documentation or create an issue on GitHub.