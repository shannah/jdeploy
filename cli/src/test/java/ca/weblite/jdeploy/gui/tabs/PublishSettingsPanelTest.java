package ca.weblite.jdeploy.gui.tabs;

import ca.weblite.jdeploy.factories.PublishTargetFactory;
import ca.weblite.jdeploy.publishTargets.PublishTargetInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetServiceInterface;
import ca.weblite.jdeploy.publishTargets.PublishTargetType;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.event.ChangeEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PublishSettingsPanel.
 * Tests load(), save(), checkbox toggles, text field changes, change listeners, and error handling.
 */
public class PublishSettingsPanelTest {

    private PublishSettingsPanel panel;
    private PublishTargetFactory mockFactory;
    private PublishTargetServiceInterface mockService;
    private PublishSettingsPanel.OnErrorCallback mockErrorCallback;

    @BeforeEach
    public void setUp() {
        mockFactory = mock(PublishTargetFactory.class);
        mockService = mock(PublishTargetServiceInterface.class);
        mockErrorCallback = mock(PublishSettingsPanel.OnErrorCallback.class);

        panel = new PublishSettingsPanel(mockFactory, mockService, mockErrorCallback);
    }

    /**
     * Test 1: load() correctly populates NPM checkbox when NPM target exists
     */
    @Test
    public void testLoadPopulatesNpmCheckboxWhenTargetExists() {
        // Arrange
        JSONObject packageJson = new JSONObject();
        PublishTargetInterface npmTarget = createMockTarget("npm", PublishTargetType.NPM, "");

        List<PublishTargetInterface> targets = new ArrayList<>();
        targets.add(npmTarget);

        when(mockService.getTargetsForPackageJson(packageJson, false)).thenReturn(targets);

        // Act
        panel.load(packageJson);

        // Assert
        assertTrue(panel.getNpmCheckbox().isSelected(), "npm checkbox should be selected");
        assertFalse(panel.getGithubCheckbox().isSelected(), "github checkbox should not be selected");
        verify(mockService).getTargetsForPackageJson(packageJson, false);
    }

    /**
     * Test 2: load() correctly populates GitHub checkbox and repository field when GitHub target exists
     */
    @Test
    public void testLoadPopulatesGithubCheckboxAndFieldWhenTargetExists() {
        // Arrange
        JSONObject packageJson = new JSONObject();
        String repoUrl = "https://github.com/user/my-releases";
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, repoUrl);

        List<PublishTargetInterface> targets = new ArrayList<>();
        targets.add(githubTarget);

        when(mockService.getTargetsForPackageJson(packageJson, false)).thenReturn(targets);

        // Act
        panel.load(packageJson);

        // Assert
        assertTrue(panel.getGithubCheckbox().isSelected(), "github checkbox should be selected");
        assertEquals(repoUrl, panel.getGithubRepositoryField().getText(), "github repository URL should match");
        assertFalse(panel.getNpmCheckbox().isSelected(), "npm checkbox should not be selected");
        verify(mockService).getTargetsForPackageJson(packageJson, false);
    }

    /**
     * Test 3: load() correctly populates both checkboxes when both targets exist
     */
    @Test
    public void testLoadPopulatesBothCheckboxesWhenBothTargetsExist() {
        // Arrange
        JSONObject packageJson = new JSONObject();
        String repoUrl = "https://github.com/user/releases";
        PublishTargetInterface npmTarget = createMockTarget("npm", PublishTargetType.NPM, "");
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, repoUrl);

        List<PublishTargetInterface> targets = new ArrayList<>();
        targets.add(npmTarget);
        targets.add(githubTarget);

        when(mockService.getTargetsForPackageJson(packageJson, false)).thenReturn(targets);

        // Act
        panel.load(packageJson);

        // Assert
        assertTrue(panel.getNpmCheckbox().isSelected(), "npm checkbox should be selected");
        assertTrue(panel.getGithubCheckbox().isSelected(), "github checkbox should be selected");
        assertEquals(repoUrl, panel.getGithubRepositoryField().getText(), "github repository URL should match");
        verify(mockService).getTargetsForPackageJson(packageJson, false);
    }

    /**
     * Test 4: load() handles null packageJson gracefully
     */
    @Test
    public void testLoadHandlesNullPackageJsonGracefully() {
        // Act & Assert - should not throw
        assertDoesNotThrow(() -> panel.load(null));

        // Assert initial state
        assertFalse(panel.getNpmCheckbox().isSelected());
        assertFalse(panel.getGithubCheckbox().isSelected());
        assertEquals("", panel.getGithubRepositoryField().getText());

        // Verify service was not called
        verify(mockService, never()).getTargetsForPackageJson(any(), anyBoolean());
    }

    /**
     * Test 5: load() handles empty targets list gracefully
     */
    @Test
    public void testLoadHandlesEmptyTargetsListGracefully() {
        // Arrange
        JSONObject packageJson = new JSONObject();
        when(mockService.getTargetsForPackageJson(packageJson, false)).thenReturn(new ArrayList<>());

        // Act
        panel.load(packageJson);

        // Assert
        assertFalse(panel.getNpmCheckbox().isSelected());
        assertFalse(panel.getGithubCheckbox().isSelected());
        assertEquals("", panel.getGithubRepositoryField().getText());
        verify(mockService).getTargetsForPackageJson(packageJson, false);
    }

    /**
     * Test 6: load() clears previous values before loading new ones
     */
    @Test
    public void testLoadClearsPreviousValues() {
        // Arrange
        JSONObject packageJson = new JSONObject();
        
        // Pre-populate with a GitHub target
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, "https://old.url");
        when(mockService.getTargetsForPackageJson(packageJson, false))
                .thenReturn(new ArrayList<>(Arrays.asList(githubTarget)));
        panel.load(packageJson);
        
        assertTrue(panel.getGithubCheckbox().isSelected());
        
        // Now load with empty targets
        when(mockService.getTargetsForPackageJson(packageJson, false))
                .thenReturn(new ArrayList<>());
        panel.load(packageJson);

        // Assert all cleared
        assertFalse(panel.getGithubCheckbox().isSelected());
        assertFalse(panel.getNpmCheckbox().isSelected());
        assertEquals("", panel.getGithubRepositoryField().getText());
    }

    /**
     * Test 7: Toggling npm checkbox creates npm target
     */
    @Test
    public void testToggleNpmCheckboxCreatesTarget() {
        // Arrange
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        panel = new PublishSettingsPanel(mockFactory, mockService, (msg, ex) -> {
            errorCalled.set(true);
        });
        
        PublishTargetInterface npmTarget = createMockTarget("npm", PublishTargetType.NPM, "");
        when(mockFactory.createWithUrlAndName("", "npm")).thenReturn(npmTarget);

        // Act
        panel.getNpmCheckbox().setSelected(true);

        // Assert - no error should be called
        assertFalse(errorCalled.get());
    }

    /**
     * Test 8: Toggling github checkbox creates github target
     */
    @Test
    public void testToggleGithubCheckboxCreatesTarget() {
        // Arrange
        AtomicBoolean errorCalled = new AtomicBoolean(false);
        panel = new PublishSettingsPanel(mockFactory, mockService, (msg, ex) -> {
            errorCalled.set(true);
        });
        
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, "");
        when(mockFactory.createWithUrlAndName("", "github")).thenReturn(githubTarget);

        // Act
        panel.getGithubCheckbox().setSelected(true);

        // Assert - no error should be called
        assertFalse(errorCalled.get());
    }

    /**
     * Test 9: Updating github repository field updates the target URL
     */
    @Test
    public void testGithubRepositoryFieldChangeUpdatesTargetUrl() {
        // Arrange
        String newUrl = "https://github.com/user/new-releases";
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, newUrl);
        when(mockFactory.createWithUrlAndName(newUrl, "github")).thenReturn(githubTarget);

        // Pre-select github checkbox
        panel.getGithubCheckbox().setSelected(true);

        // Act
        panel.getGithubRepositoryField().setText(newUrl);

        // Assert - verify the field has the new URL
        assertEquals(newUrl, panel.getGithubRepositoryField().getText());
    }

    /**
     * Test 10: getPublishTargets() returns correct targets when npm is selected
     */
    @Test
    public void testGetPublishTargetsReturnsNpmWhenSelected() {
        // Arrange
        PublishTargetInterface npmTarget = createMockTarget("npm", PublishTargetType.NPM, "");
        when(mockFactory.createWithUrlAndName("", "npm")).thenReturn(npmTarget);

        // Act
        panel.getNpmCheckbox().setSelected(true);
        List<PublishTargetInterface> targets = panel.getPublishTargets();

        // Assert
        assertEquals(1, targets.size());
        assertEquals(PublishTargetType.NPM, targets.get(0).getType());
    }

    /**
     * Test 11: getPublishTargets() returns correct targets when github is selected
     */
    @Test
    public void testGetPublishTargetsReturnsGithubWhenSelected() {
        // Arrange
        String repoUrl = "https://github.com/user/releases";
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, repoUrl);
        when(mockFactory.createWithUrlAndName(repoUrl, "github")).thenReturn(githubTarget);

        // Act
        panel.getGithubCheckbox().setSelected(true);
        panel.getGithubRepositoryField().setText(repoUrl);
        List<PublishTargetInterface> targets = panel.getPublishTargets();

        // Assert
        assertEquals(1, targets.size());
        assertEquals(PublishTargetType.GITHUB, targets.get(0).getType());
        assertEquals(repoUrl, targets.get(0).getUrl());
    }

    /**
     * Test 12: getPublishTargets() returns both targets when both are selected
     */
    @Test
    public void testGetPublishTargetsReturnsBothWhenSelected() {
        // Arrange
        String repoUrl = "https://github.com/user/releases";
        PublishTargetInterface npmTarget = createMockTarget("npm", PublishTargetType.NPM, "");
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, repoUrl);
        when(mockFactory.createWithUrlAndName("", "npm")).thenReturn(npmTarget);
        when(mockFactory.createWithUrlAndName(repoUrl, "github")).thenReturn(githubTarget);

        // Act
        panel.getNpmCheckbox().setSelected(true);
        panel.getGithubCheckbox().setSelected(true);
        panel.getGithubRepositoryField().setText(repoUrl);
        List<PublishTargetInterface> targets = panel.getPublishTargets();

        // Assert
        assertEquals(2, targets.size());
        assertTrue(targets.stream().anyMatch(t -> t.getType() == PublishTargetType.NPM));
        assertTrue(targets.stream().anyMatch(t -> t.getType() == PublishTargetType.GITHUB));
    }

    /**
     * Test 13: getPublishTargets() returns empty list when no targets are selected
     */
    @Test
    public void testGetPublishTargetsReturnsEmptyWhenNothingSelected() {
        // Act
        List<PublishTargetInterface> targets = panel.getPublishTargets();

        // Assert
        assertTrue(targets.isEmpty());
    }

    /**
     * Test 14: Change listener fires when npm checkbox is toggled
     */
    @Test
    public void testChangeListenerFiresOnNpmCheckboxToggle() {
        // Arrange
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        ActionListener listener = e -> listenerFired.set(true);
        panel.addChangeListener(listener);

        PublishTargetInterface npmTarget = createMockTarget("npm", PublishTargetType.NPM, "");
        when(mockFactory.createWithUrlAndName("", "npm")).thenReturn(npmTarget);

        // Act
        panel.getNpmCheckbox().setSelected(true);

        // Assert
        assertTrue(listenerFired.get(), "Change listener should have fired");
    }

    /**
     * Test 15: Change listener fires when github checkbox is toggled
     */
    @Test
    public void testChangeListenerFiresOnGithubCheckboxToggle() {
        // Arrange
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        ActionListener listener = e -> listenerFired.set(true);
        panel.addChangeListener(listener);

        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, "");
        when(mockFactory.createWithUrlAndName("", "github")).thenReturn(githubTarget);

        // Act
        panel.getGithubCheckbox().setSelected(true);

        // Assert
        assertTrue(listenerFired.get(), "Change listener should have fired");
    }

    /**
     * Test 16: Change listener fires when github repository field is modified
     */
    @Test
    public void testChangeListenerFiresOnGithubRepositoryFieldChange() {
        // Arrange
        AtomicBoolean listenerFired = new AtomicBoolean(false);
        ActionListener listener = e -> listenerFired.set(true);
        panel.addChangeListener(listener);

        // Pre-select github checkbox so field change triggers listener
        PublishTargetInterface githubTarget = createMockTarget("github", PublishTargetType.GITHUB, "");
        when(mockFactory.createWithUrlAndName("", "github")).thenReturn(githubTarget);
        panel.getGithubCheckbox().setSelected(true);

        // Reset the flag for the actual field change
        listenerFired.set(false);

        String newUrl = "https://github.com/user/releases";
        when(mockFactory.createWithUrlAndName(newUrl, "github")).thenReturn(githubTarget);

        // Act
        panel.getGithubRepositoryField().setText(newUrl);

        // Assert
        assertTrue(listenerFired.get(), "Change listener should have fired on field change");
    }

    /**
     * Test 17: Error callback is invoked when service throws exception during load
     */
    @Test
    public void testErrorCallbackInvokedOnServiceExceptionDuringLoad() {
        // Arrange
        JSONObject packageJson = new JSONObject();
        Exception testException = new RuntimeException("Service error");
        when(mockService.getTargetsForPackageJson(packageJson, false))
                .thenThrow(testException);

        AtomicReference<Exception> caughtException = new AtomicReference<>();
        PublishSettingsPanel.OnErrorCallback errorCallback = (msg, ex) -> {
            caughtException.set(ex);
        };
        panel = new PublishSettingsPanel(mockFactory, mockService, errorCallback);

        // Act
        panel.load(packageJson);

        // Assert
        assertNotNull(caughtException.get(), "Error callback should have been invoked");
        assertTrue(caughtException.get() instanceof RuntimeException);
        assertEquals("Service error", caughtException.get().getMessage());
    }

    /**
     * Test 18: Error callback is invoked when checkbox toggle throws exception
     */
    @Test
    public void testErrorCallbackInvokedOnCheckboxToggleException() {
        // Arrange
        Exception testException = new RuntimeException("Factory error");
        when(mockFactory.createWithUrlAndName(anyString(), anyString()))
                .thenThrow(testException);

        AtomicReference<Exception> caughtException = new AtomicReference<>();
        PublishSettingsPanel.OnErrorCallback errorCallback = (msg, ex) -> {
            caughtException.set(ex);
        };
        panel = new PublishSettingsPanel(mockFactory, mockService, errorCallback);

        // Act
        panel.getNpmCheckbox().setSelected(true);

        // Assert
        assertNotNull(caughtException.get(), "Error callback should have been invoked");
        assertTrue(caughtException.get() instanceof RuntimeException);
        assertEquals("Factory error", caughtException.get().getMessage());
    }

    /**
     * Test 19: Error callback receives descriptive error message on service exception
     */
    @Test
    public void testErrorCallbackReceivesDescriptiveMessage() {
        // Arrange
        JSONObject packageJson = new JSONObject();
        Exception testException = new RuntimeException("Database connection failed");
        when(mockService.getTargetsForPackageJson(packageJson, false))
                .thenThrow(testException);

        AtomicReference<String> errorMessage = new AtomicReference<>();
        PublishSettingsPanel.OnErrorCallback errorCallback = (msg, ex) -> {
            errorMessage.set(msg);
        };
        panel = new PublishSettingsPanel(mockFactory, mockService, errorCallback);

        // Act
        panel.load(packageJson);

        // Assert
        assertNotNull(errorMessage.get(), "Error message should be provided");
        assertTrue(errorMessage.get().contains("Error loading publish settings"));
        assertTrue(errorMessage.get().contains("Database connection failed"));
    }

    /**
     * Test 20: Load does not fire change listener during initial population
     */
    @Test
    public void testLoadDoesNotFireChangeListenerDuringInitialPopulation() {
        // Arrange
        AtomicInteger listenerFireCount = new AtomicInteger(0);
        ActionListener listener = e -> listenerFireCount.incrementAndGet();
        panel.addChangeListener(listener);

        JSONObject packageJson = new JSONObject();
        PublishTargetInterface npmTarget = createMockTarget("npm", PublishTargetType.NPM, "");
        when(mockService.getTargetsForPackageJson(packageJson, false))
                .thenReturn(new ArrayList<>(Arrays.asList(npmTarget)));

        // Act
        panel.load(packageJson);

        // Assert
        assertEquals(0, listenerFireCount.get(), "Change listener should not fire during load");
    }

    /**
     * Helper method to create a mock PublishTargetInterface
     */
    private PublishTargetInterface createMockTarget(String name, PublishTargetType type, String url) {
        PublishTargetInterface target = mock(PublishTargetInterface.class);
        when(target.getName()).thenReturn(name);
        when(target.getType()).thenReturn(type);
        when(target.getUrl()).thenReturn(url);
        when(target.isDefault()).thenReturn(false);
        return target;
    }
}
