package ca.weblite.jdeploy.publishTargets;

import ca.weblite.jdeploy.factories.PublishTargetFactory;
import ca.weblite.jdeploy.io.FileSystemInterface;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class PublishTargetServiceTest {

    private PublishTargetService service;
    private PublishTargetSerializer serializer;
    private FileSystemInterface fileSystem;

    private PublishTargetFactory publishTargetFactory;

    @BeforeEach
    public void setup() {
        serializer = mock(PublishTargetSerializer.class);
        fileSystem = mock(FileSystemInterface.class);
        publishTargetFactory = mock(PublishTargetFactory.class);
        service = new PublishTargetService(serializer, fileSystem, publishTargetFactory);
    }

    @Test
    public void testGetTargetsForProject_packageJsonExists() throws IOException {
        // Arrange
        String projectPath = "/project/path";
        String packageJsonContent = "{\"jdeploy\": {\"publishTargets\": [{\"name\": \"target1\", \"type\": \"S3\", \"url\": \"s3://bucket/path\"}]}}";
        InputStream packageJsonStream = new ByteArrayInputStream(packageJsonContent.getBytes(StandardCharsets.UTF_8));

        when(fileSystem.exists(any(Path.class))).thenReturn(true);
        when(fileSystem.getInputStream(any(Path.class))).thenReturn(packageJsonStream);
        when(serializer.deserialize(any(JSONArray.class))).thenReturn(Collections.singletonList(mock(PublishTargetInterface.class)));

        // Act
        List<PublishTargetInterface> targets = service.getTargetsForProject(projectPath, false);

        // Assert
        assertNotNull(targets);
        assertEquals(1, targets.size());
        verify(fileSystem).exists(eq(Paths.get(projectPath, "package.json")));
        verify(fileSystem).getInputStream(eq(Paths.get(projectPath, "package.json")));
        verify(serializer).deserialize(any(JSONArray.class));
    }

    @Test
    public void testGetTargetsForProject_packageJsonDoesNotExist() throws IOException {
        // Arrange
        String projectPath = "/project/path";
        when(fileSystem.exists(any(Path.class))).thenReturn(false);

        // Act
        List<PublishTargetInterface> targets = service.getTargetsForProject(projectPath, false);

        // Assert
        assertNotNull(targets);
        assertTrue(targets.isEmpty());
        verify(fileSystem).exists(eq(Paths.get(projectPath, "package.json")));
        verifyNoInteractions(serializer);
    }

    @Test
    public void testUpdatePublishTargetsForProject_success() throws IOException {
        // Arrange
        String projectPath = "/project/path";
        String packageJsonContent = "{}";
        InputStream packageJsonStream = new ByteArrayInputStream(packageJsonContent.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        List<PublishTargetInterface> targets = Collections.singletonList(mock(PublishTargetInterface.class));
        JSONArray serializedTargets = new JSONArray().put(new JSONObject().put("name", "target1"));

        when(fileSystem.exists(any(Path.class))).thenReturn(true);
        when(fileSystem.getInputStream(any(Path.class))).thenReturn(packageJsonStream);
        when(fileSystem.getOutputStream(any(Path.class))).thenReturn(outputStream);
        when(serializer.serialize(anyList())).thenReturn(serializedTargets);

        // Act
        service.updatePublishTargetsForProject(projectPath, targets);

        // Assert
        verify(fileSystem).exists(eq(Paths.get(projectPath, "package.json")));
        verify(fileSystem).getInputStream(eq(Paths.get(projectPath, "package.json")));
        verify(fileSystem).getOutputStream(eq(Paths.get(projectPath, "package.json")));
        verify(serializer).serialize(eq(targets));

        String writtenContent = outputStream.toString(StandardCharsets.UTF_8.name());
        JSONObject updatedJson = new JSONObject(writtenContent);
        assertTrue(updatedJson.has("jdeploy"));
        assertTrue(updatedJson.getJSONObject("jdeploy").has("publishTargets"));
        assertEquals(1, updatedJson.getJSONObject("jdeploy").getJSONArray("publishTargets").length());
    }

    @Test
    public void testUpdatePublishTargetsForProject_packageJsonNotFound() {
        // Arrange
        String projectPath = "/project/path";
        List<PublishTargetInterface> targets = new ArrayList<>();

        when(fileSystem.exists(any(Path.class))).thenReturn(false);

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () ->
                service.updatePublishTargetsForProject(projectPath, targets)
        );
        assertTrue(exception instanceof java.io.FileNotFoundException);
        verify(fileSystem).exists(eq(Paths.get(projectPath, "package.json")));
        verifyNoInteractions(serializer);
    }

    @Test
    public void testDefaultTargetsNotPersistedUntilExplicit() {
        // Test for the specific bug: default NPM target should not be persisted 
        // until user explicitly adds it
        
        // Arrange: Empty package.json (no publish targets defined)
        JSONObject packageJson = new JSONObject();
        packageJson.put("name", "test-app");
        
        PublishTargetInterface defaultNpmTarget = mock(PublishTargetInterface.class);
        when(defaultNpmTarget.getType()).thenReturn(PublishTargetType.NPM);
        when(defaultNpmTarget.isDefault()).thenReturn(true);
        when(defaultNpmTarget.getName()).thenReturn("npm: test-app");
        when(defaultNpmTarget.getUrl()).thenReturn("test-app");
        
        when(publishTargetFactory.createWithUrlAndName("test-app", "test-app", true))
                .thenReturn(defaultNpmTarget);
        when(serializer.serialize(defaultNpmTarget)).thenReturn(new JSONObject()
                .put("name", "npm: test-app")
                .put("type", "NPM")
                .put("url", "test-app")
                .put("isDefault", true));
        when(serializer.deserialize(any(JSONArray.class)))
                .thenReturn(Collections.singletonList(defaultNpmTarget));
        
        // Act: Get targets with includeDefaultTarget=true (UI scenario)
        List<PublishTargetInterface> targets = service.getTargetsForPackageJson(packageJson, true);
        
        // Assert: Should have one default NPM target in memory
        assertEquals(1, targets.size());
        assertEquals(PublishTargetType.NPM, targets.get(0).getType());
        assertTrue(targets.get(0).isDefault());
        
        // But: package.json should still be empty of publish targets
        JSONObject jdeploy = packageJson.optJSONObject("jdeploy");
        if (jdeploy != null && jdeploy.has("publishTargets")) {
            JSONArray publishTargets = jdeploy.getJSONArray("publishTargets");
            // The default target should be in the returned JSONArray but not yet persisted
            assertEquals(1, publishTargets.length());
            assertTrue(publishTargets.getJSONObject(0).optBoolean("isDefault", false));
        }
        
        // When: Updating targets (simulating user interaction)
        // Default targets should be filtered out during persistence
        PublishTargetInterface explicitGithubTarget = mock(PublishTargetInterface.class);
        when(explicitGithubTarget.getType()).thenReturn(PublishTargetType.GITHUB);
        when(explicitGithubTarget.isDefault()).thenReturn(false);
        when(explicitGithubTarget.getUrl()).thenReturn("https://github.com/test/repo");
        
        List<PublishTargetInterface> mixedTargets = new ArrayList<>();
        mixedTargets.add(defaultNpmTarget); // Default NPM target
        mixedTargets.add(explicitGithubTarget); // Explicit GitHub target
        
        when(serializer.serialize(Collections.singletonList(explicitGithubTarget)))
                .thenReturn(new JSONArray().put(new JSONObject()
                        .put("name", "github: test")
                        .put("type", "GITHUB")
                        .put("url", "https://github.com/test/repo")));
        
        service.updatePublishTargetsForPackageJson(packageJson, mixedTargets);
        
        // Then: Only explicit (non-default) targets should be persisted
        JSONObject updatedJdeploy = packageJson.getJSONObject("jdeploy");
        JSONArray persistedTargets = updatedJdeploy.getJSONArray("publishTargets");
        assertEquals(1, persistedTargets.length());
        assertEquals("GITHUB", persistedTargets.getJSONObject(0).getString("type"));
        assertFalse(persistedTargets.getJSONObject(0).optBoolean("isDefault", false));
    }
}
