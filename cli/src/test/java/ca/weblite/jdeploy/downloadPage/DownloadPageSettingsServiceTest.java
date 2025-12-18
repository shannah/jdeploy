package ca.weblite.jdeploy.downloadPage;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DownloadPageSettingsServiceTest {

    @Mock
    private DownloadPageSettingsJsonReader jsonReader;

    @Mock
    private DownloadPageSettingsJsonWriter jsonWriter;

    private DownloadPageSettingsService service;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        service = new DownloadPageSettingsService(jsonReader, jsonWriter);
    }

    @Test
    @DisplayName("Should throw exception when reading from null file")
    void shouldThrowExceptionWhenReadingFromNullFile() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            service.read((File) null)
        );
        
        assertEquals("packageJsonFile cannot be null or does not exist", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when reading from non-existent file")
    void shouldThrowExceptionWhenReadingFromNonExistentFile() {
        File nonExistentFile = new File("non-existent-file.json");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            service.read(nonExistentFile)
        );
        
        assertEquals("packageJsonFile cannot be null or does not exist", exception.getMessage());
    }

    @Test
    @DisplayName("Should read settings from JSON object without jdeploy section")
    void shouldReadSettingsFromJsonObjectWithoutJdeploySection() {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-package");
        packageJson.put("version", "1.0.0");
        
        DownloadPageSettings result = service.read(packageJson);
        
        assertNotNull(result);
        assertTrue(result.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
        verify(jsonReader, never()).readJson(any(), any());
    }

    @Test
    @DisplayName("Should read settings from JSON object with jdeploy section but no downloadPage")
    void shouldReadSettingsFromJsonObjectWithJdeployButNoDownloadPage() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("mainClass", "com.example.Main");
        packageJson.put("jdeploy", jdeploy);
        
        DownloadPageSettings result = service.read(packageJson);
        
        assertNotNull(result);
        assertTrue(result.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
        verify(jsonReader, never()).readJson(any(), any());
    }

    @Test
    @DisplayName("Should read settings from JSON object with jdeploy and downloadPage sections")
    void shouldReadSettingsFromJsonObjectWithJdeployAndDownloadPage() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONObject downloadPage = new JSONObject();
        jdeploy.put("downloadPage", downloadPage);
        packageJson.put("jdeploy", jdeploy);
        
        DownloadPageSettings result = service.read(packageJson);
        
        assertNotNull(result);
        verify(jsonReader).readJson(eq(result), eq(downloadPage));
    }

    @Test
    @DisplayName("Should call jsonReader with correct parameters")
    void shouldCallJsonReaderWithCorrectParameters() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONObject downloadPage = new JSONObject();
        downloadPage.put("platforms", new String[]{"windows-x64", "mac-arm64"});
        jdeploy.put("downloadPage", downloadPage);
        packageJson.put("jdeploy", jdeploy);
        
        service.read(packageJson);
        
        verify(jsonReader).readJson(any(DownloadPageSettings.class), eq(downloadPage));
    }

    @Test
    @DisplayName("Should throw exception when writing to null JSON object")
    void shouldThrowExceptionWhenWritingToNullJsonObject() {
        DownloadPageSettings settings = new DownloadPageSettings();
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            service.write(settings, null)
        );
        
        assertEquals("packageJson cannot be null", exception.getMessage());
    }

    @Test
    @DisplayName("Should throw exception when writing to JSON object without jdeploy section")
    void shouldThrowExceptionWhenWritingToJsonObjectWithoutJdeploySection() {
        DownloadPageSettings settings = new DownloadPageSettings();
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-package");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> 
            service.write(settings, packageJson)
        );
        
        assertEquals("packageJson must have a jdeploy section", exception.getMessage());
    }

    @Test
    @DisplayName("Should write settings to JSON object with existing jdeploy and downloadPage sections")
    void shouldWriteSettingsToJsonObjectWithExistingJdeployAndDownloadPage() {
        DownloadPageSettings settings = new DownloadPageSettings();
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        JSONObject downloadPage = new JSONObject();
        jdeploy.put("downloadPage", downloadPage);
        packageJson.put("jdeploy", jdeploy);
        
        service.write(settings, packageJson);
        
        verify(jsonWriter).write(eq(settings), eq(downloadPage));
        assertTrue(packageJson.has("jdeploy"));
        assertTrue(packageJson.getJSONObject("jdeploy").has("downloadPage"));
    }

    @Test
    @DisplayName("Should create downloadPage section if it doesn't exist")
    void shouldCreateDownloadPageSectionIfItDoesntExist() {
        DownloadPageSettings settings = new DownloadPageSettings();
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJson.put("jdeploy", jdeploy);
        
        assertFalse(jdeploy.has("downloadPage"));
        
        service.write(settings, packageJson);
        
        assertTrue(jdeploy.has("downloadPage"));
        JSONObject downloadPage = jdeploy.getJSONObject("downloadPage");
        assertNotNull(downloadPage);
        verify(jsonWriter).write(eq(settings), eq(downloadPage));
    }

    @Test
    @DisplayName("Should preserve other jdeploy properties when writing")
    void shouldPreserveOtherJdeployPropertiesWhenWriting() {
        DownloadPageSettings settings = new DownloadPageSettings();
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("mainClass", "com.example.Main");
        jdeploy.put("port", 8080);
        packageJson.put("jdeploy", jdeploy);
        
        service.write(settings, packageJson);
        
        JSONObject jdeployAfter = packageJson.getJSONObject("jdeploy");
        assertTrue(jdeployAfter.has("mainClass"));
        assertTrue(jdeployAfter.has("port"));
        assertTrue(jdeployAfter.has("downloadPage"));
        assertEquals("com.example.Main", jdeployAfter.getString("mainClass"));
        assertEquals(8080, jdeployAfter.getInt("port"));
    }

    @Test
    @DisplayName("Should call jsonWriter with correct parameters")
    void shouldCallJsonWriterWithCorrectParameters() {
        DownloadPageSettings settings = new DownloadPageSettings();
        Set<DownloadPageSettings.BundlePlatform> enabledPlatforms = new HashSet<>();
        enabledPlatforms.add(DownloadPageSettings.BundlePlatform.WindowsX64);
        settings.setEnabledPlatforms(enabledPlatforms);
        
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJson.put("jdeploy", jdeploy);
        
        service.write(settings, packageJson);
        
        verify(jsonWriter).write(eq(settings), any(JSONObject.class));
    }

    @Test
    @DisplayName("Should handle complex JSON structure")
    void shouldHandleComplexJsonStructure() {
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        packageJson.put("version", "1.2.3");
        
        JSONObject jdeploy = new JSONObject();
        jdeploy.put("mainClass", "com.example.MainClass");
        jdeploy.put("port", 9090);
        
        JSONObject downloadPage = new JSONObject();
        downloadPage.put("title", "Download My App");
        
        jdeploy.put("downloadPage", downloadPage);
        packageJson.put("jdeploy", jdeploy);
        
        DownloadPageSettings result = service.read(packageJson);
        
        assertNotNull(result);
        verify(jsonReader).readJson(any(DownloadPageSettings.class), eq(downloadPage));
        
        assertEquals("test-app", packageJson.getString("name"));
        assertEquals("1.2.3", packageJson.getString("version"));
        assertEquals("com.example.MainClass", packageJson.getJSONObject("jdeploy").getString("mainClass"));
        assertEquals(9090, packageJson.getJSONObject("jdeploy").getInt("port"));
    }

    @Test
    @DisplayName("Should handle null settings when writing")
    void shouldHandleNullSettingsWhenWriting() {
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJson.put("jdeploy", jdeploy);
        
        service.write(null, packageJson);
        
        verify(jsonWriter).write(eq(null), any(JSONObject.class));
        assertTrue(packageJson.getJSONObject("jdeploy").has("downloadPage"));
    }

    @Test
    @DisplayName("Should return new settings instance on each read call")
    void shouldReturnNewSettingsInstanceOnEachReadCall() {
        JSONObject packageJson = new JSONObject();
        
        DownloadPageSettings result1 = service.read(packageJson);
        DownloadPageSettings result2 = service.read(packageJson);
        
        assertNotSame(result1, result2);
        assertNotNull(result1);
        assertNotNull(result2);
    }

    @Test
    @DisplayName("Should create downloadPage object correctly")
    void shouldCreateDownloadPageObjectCorrectly() {
        DownloadPageSettings settings = new DownloadPageSettings();
        JSONObject packageJson = new JSONObject();
        JSONObject jdeploy = new JSONObject();
        packageJson.put("jdeploy", jdeploy);
        
        service.write(settings, packageJson);
        
        JSONObject downloadPageObject = jdeploy.getJSONObject("downloadPage");
        assertNotNull(downloadPageObject);
        assertTrue(downloadPageObject.isEmpty() || downloadPageObject.length() >= 0);
    }

    @Test
    @DisplayName("Should read settings from valid file without jdeploy section")
    void shouldReadSettingsFromValidFileWithoutJdeploySection() throws IOException {
        File packageJsonFile = new File(tempDir, "package.json");
        String jsonContent = "{\n" +
            "  \"name\": \"test-package\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"description\": \"A test package\"\n" +
            "}";
        FileUtils.writeStringToFile(packageJsonFile, jsonContent, StandardCharsets.UTF_8);
        
        DownloadPageSettings result = service.read(packageJsonFile);
        
        assertNotNull(result);
        assertTrue(result.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
        verify(jsonReader, never()).readJson(any(), any());
    }

    @Test
    @DisplayName("Should read settings from valid file with jdeploy section but no downloadPage")
    void shouldReadSettingsFromValidFileWithJdeployButNoDownloadPage() throws IOException {
        File packageJsonFile = new File(tempDir, "package.json");
        String jsonContent = "{\n" +
            "  \"name\": \"test-package\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"jdeploy\": {\n" +
            "    \"mainClass\": \"com.example.Main\",\n" +
            "    \"port\": 8080\n" +
            "  }\n" +
            "}";
        FileUtils.writeStringToFile(packageJsonFile, jsonContent, StandardCharsets.UTF_8);
        
        DownloadPageSettings result = service.read(packageJsonFile);
        
        assertNotNull(result);
        assertTrue(result.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
        verify(jsonReader, never()).readJson(any(), any());
    }

    @Test
    @DisplayName("Should read settings from valid file with jdeploy and downloadPage sections")
    void shouldReadSettingsFromValidFileWithJdeployAndDownloadPage() throws IOException {
        File packageJsonFile = new File(tempDir, "package.json");
        String jsonContent = "{\n" +
            "  \"name\": \"test-package\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"jdeploy\": {\n" +
            "    \"mainClass\": \"com.example.Main\",\n" +
            "    \"downloadPage\": {\n" +
            "      \"title\": \"Download My App\",\n" +
            "      \"platforms\": [\"windows-x64\", \"mac-arm64\"]\n" +
            "    }\n" +
            "  }\n" +
            "}";
        FileUtils.writeStringToFile(packageJsonFile, jsonContent, StandardCharsets.UTF_8);
        
        DownloadPageSettings result = service.read(packageJsonFile);
        
        assertNotNull(result);
        verify(jsonReader).readJson(eq(result), any(JSONObject.class));
    }

    @Test
    @DisplayName("Should read settings from valid file with complex downloadPage configuration")
    void shouldReadSettingsFromValidFileWithComplexDownloadPageConfig() throws IOException {
        File packageJsonFile = new File(tempDir, "package.json");
        String jsonContent = "{\n" +
            "  \"name\": \"complex-app\",\n" +
            "  \"version\": \"2.1.0\",\n" +
            "  \"description\": \"A complex application\",\n" +
            "  \"jdeploy\": {\n" +
            "    \"mainClass\": \"com.example.ComplexMain\",\n" +
            "    \"port\": 9090,\n" +
            "    \"downloadPage\": {\n" +
            "      \"title\": \"Complex App Downloads\",\n" +
            "      \"description\": \"Download the latest version\",\n" +
            "      \"platforms\": [\"windows-x64\", \"mac-arm64\", \"linux-x64\"],\n" +
            "      \"customField\": \"customValue\"\n" +
            "    }\n" +
            "  },\n" +
            "  \"author\": \"Test Author\"\n" +
            "}";
        FileUtils.writeStringToFile(packageJsonFile, jsonContent, StandardCharsets.UTF_8);
        
        DownloadPageSettings result = service.read(packageJsonFile);
        
        assertNotNull(result);
        verify(jsonReader).readJson(eq(result), any(JSONObject.class));
    }

    @Test
    @DisplayName("Should read settings from minimal valid file")
    void shouldReadSettingsFromMinimalValidFile() throws IOException {
        File packageJsonFile = new File(tempDir, "package.json");
        String jsonContent = "{\n" +
            "  \"name\": \"minimal-app\",\n" +
            "  \"version\": \"1.0.0\"\n" +
            "}";
        FileUtils.writeStringToFile(packageJsonFile, jsonContent, StandardCharsets.UTF_8);
        
        DownloadPageSettings result = service.read(packageJsonFile);
        
        assertNotNull(result);
        assertTrue(result.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
        verify(jsonReader, never()).readJson(any(), any());
    }

    @Test
    @DisplayName("Should read settings from file with empty jdeploy section")
    void shouldReadSettingsFromFileWithEmptyJdeploySection() throws IOException {
        File packageJsonFile = new File(tempDir, "package.json");
        String jsonContent = "{\n" +
            "  \"name\": \"empty-jdeploy-app\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"jdeploy\": {}\n" +
            "}";
        FileUtils.writeStringToFile(packageJsonFile, jsonContent, StandardCharsets.UTF_8);
        
        DownloadPageSettings result = service.read(packageJsonFile);
        
        assertNotNull(result);
        assertTrue(result.getEnabledPlatforms().contains(DownloadPageSettings.BundlePlatform.Default));
        verify(jsonReader, never()).readJson(any(), any());
    }

    @Test
    @DisplayName("Should read settings from file with empty downloadPage section")
    void shouldReadSettingsFromFileWithEmptyDownloadPageSection() throws IOException {
        File packageJsonFile = new File(tempDir, "package.json");
        String jsonContent = "{\n" +
            "  \"name\": \"empty-download-page-app\",\n" +
            "  \"version\": \"1.0.0\",\n" +
            "  \"jdeploy\": {\n" +
            "    \"mainClass\": \"com.example.Main\",\n" +
            "    \"downloadPage\": {}\n" +
            "  }\n" +
            "}";
        FileUtils.writeStringToFile(packageJsonFile, jsonContent, StandardCharsets.UTF_8);
        
        DownloadPageSettings result = service.read(packageJsonFile);
        
        assertNotNull(result);
        verify(jsonReader).readJson(eq(result), any(JSONObject.class));
    }
}