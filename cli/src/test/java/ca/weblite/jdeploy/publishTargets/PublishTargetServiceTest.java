package ca.weblite.jdeploy.publishTargets;

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

    @BeforeEach
    public void setup() {
        serializer = mock(PublishTargetSerializer.class);
        fileSystem = mock(FileSystemInterface.class);
        service = new PublishTargetService(serializer, fileSystem);
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
        List<PublishTargetInterface> targets = service.getTargetsForProject(projectPath);

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
        List<PublishTargetInterface> targets = service.getTargetsForProject(projectPath);

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
}
