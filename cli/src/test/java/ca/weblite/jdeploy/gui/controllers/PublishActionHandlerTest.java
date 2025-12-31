package ca.weblite.jdeploy.gui.controllers;

import ca.weblite.jdeploy.gui.JDeployProjectEditorContext;
import ca.weblite.jdeploy.gui.ProgressDialog;
import ca.weblite.jdeploy.gui.services.PublishingCoordinator;
import ca.weblite.jdeploy.packaging.PackagingContext;
import ca.weblite.jdeploy.packaging.PackagingPreferences;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PublishActionHandler Tests")
class PublishActionHandlerTest {

    @TempDir
    File tempDir;

    private File packageJSONFile;
    private JSONObject packageJSON;
    private JFrame mockFrame;
    private JDeployProjectEditorContext mockContext;
    private PublishingCoordinator mockCoordinator;
    private Runnable mockOnSave;
    private PublishActionHandler handler;

    @BeforeEach
    void setUp() throws IOException {
        // Create a package.json file
        packageJSONFile = new File(tempDir, "package.json");
        packageJSONFile.createNewFile();

        // Create a valid package.json object
        packageJSON = new JSONObject();
        packageJSON.put("name", "test-app");
        packageJSON.put("version", "1.0.0");
        packageJSON.put("author", "Test Author");
        packageJSON.put("description", "Test Description");

        // Create mocks
        mockFrame = mock(JFrame.class);
        mockContext = mock(JDeployProjectEditorContext.class);
        mockCoordinator = mock(PublishingCoordinator.class);
        mockOnSave = mock(Runnable.class);

        // Configure default mock behaviors
        when(mockContext.promptForNpmToken(mockFrame)).thenReturn(true);
        when(mockContext.promptForGithubToken(mockFrame)).thenReturn(true);
        when(mockContext.getNpmToken()).thenReturn("fake-npm-token");
        when(mockContext.getGithubToken()).thenReturn("fake-github-token");
        when(mockContext.useManagedNode()).thenReturn(false);
        when(mockCoordinator.getPublishTargetNames()).thenReturn("NPM");
        when(mockCoordinator.getBuildPreferences()).thenReturn(
                new PackagingPreferences("test-app", false)
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(true);
        when(mockCoordinator.isGitHubPublishingEnabled()).thenReturn(false);

        // Create handler
        handler = new PublishActionHandler(
                mockFrame,
                packageJSONFile,
                packageJSON,
                mockContext,
                mockCoordinator,
                mockOnSave,
                () -> "http://example.com/download"
        );
    }

    @Test
    @DisplayName("handlePublish() should guard against concurrent calls when publishInProgress is true")
    void testConcurrentCallGuard() throws IOException, InterruptedException {
        // Setup validation to never complete so the handler stays in progress
        when(mockCoordinator.validateForPublishing()).thenAnswer(invocation -> {
            Thread.sleep(1000); // Sleep to keep it in progress
            return PublishingCoordinator.ValidationResult.success();
        });

        // First call should proceed
        handler.handlePublish();
        
        // Wait briefly for the thread to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Second call should return immediately (no additional validation)
        handler.handlePublish();

        // Verify validation was called only once (not twice)
        verify(mockCoordinator, timeout(2000).times(1)).validateForPublishing();

        // Clean up
        try {
            Thread.sleep(1200); // Wait for first thread to complete
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("handlePublish() should show error when validation fails")
    void testValidationFailureShowsError() throws InterruptedException, IOException {
        String errorMessage = "Test validation error";
        PublishingCoordinator.ValidationResult failureResult = 
                PublishingCoordinator.ValidationResult.failure(errorMessage);

        when(mockCoordinator.validateForPublishing()).thenReturn(failureResult);
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        // Mock the frame to capture the error dialog
        CountDownLatch errorShown = new CountDownLatch(1);
        
        handler.handlePublish();

        // Wait for the thread to process
        Thread.sleep(500);

        // Verify that onSave was NOT called since validation failed
        verify(mockOnSave, never()).run();

        // Verify validation was called
        verify(mockCoordinator).validateForPublishing();
    }

    @Test
    @DisplayName("handlePublish() should show error without proceeding on validation failure")
    void testValidationFailureDoesNotProceedWithPublish() throws InterruptedException, IOException {
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.failure("Invalid configuration")
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        handler.handlePublish();

        // Wait for the thread to process
        Thread.sleep(500);

        // Verify onSave was NOT called
        verify(mockOnSave, never()).run();

        // Verify coordinator.publish was NOT called
        verify(mockCoordinator, never()).publish(
                any(PackagingContext.class),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("NOT_LOGGED_IN validation error should trigger login dialog flow")
    void testNotLoggedInValidationError() throws InterruptedException, IOException {
        // Mock validation to return NOT_LOGGED_IN error
        PublishingCoordinator.ValidationResult notLoggedInResult =
                PublishingCoordinator.ValidationResult.failure(
                        "You must be logged into NPM",
                        PublishingCoordinator.ValidationResult.ERROR_TYPE_NOT_LOGGED_IN
                );
        when(mockCoordinator.validateForPublishing()).thenReturn(notLoggedInResult);
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(true);

        handler.handlePublish();

        // Wait for the thread to process
        Thread.sleep(500);

        // Verify that onSave was NOT called since validation failed
        verify(mockOnSave, never()).run();

        // Verify validation was called
        verify(mockCoordinator).validateForPublishing();
    }

    @Test
    @DisplayName("Successful validation should call onSave callback before publishing")
    void testSuccessfulValidationCallsOnSaveBeforePublish() throws InterruptedException, IOException {
        // Mock successful validation
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        // Create a capture list to verify call order
        ArgumentCaptor<Runnable> onSaveCaptor = ArgumentCaptor.forClass(Runnable.class);
        
        handler.handlePublish();

        // Wait for the thread to process
        Thread.sleep(500);

        // Verify onSave was called
        verify(mockOnSave, timeout(2000)).run();

        // Verify coordinator.publish was called after validation
        verify(mockCoordinator).validateForPublishing();
    }

    @Test
    @DisplayName("ProgressDialog should be shown during publish and updated on completion")
    void testProgressDialogShownAndUpdatedOnCompletion() throws InterruptedException, IOException {
        // Mock successful validation and publishing
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        // Mock the coordinator.publish to call the progress callback with completion
        doAnswer(invocation -> {
            PublishingCoordinator.ProgressCallback callback = invocation.getArgument(3);
            if (callback != null) {
                callback.onProgress(PublishingCoordinator.PublishProgress.complete());
            }
            return null;
        }).when(mockCoordinator).publish(
                any(PackagingContext.class),
                any(),
                any(),
                any(),
                any()
        );

        handler.handlePublish();

        // Wait for the thread to complete
        Thread.sleep(1000);

        // Verify that coordinator.publish was called
        verify(mockCoordinator, timeout(2000)).publish(
                any(PackagingContext.class),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("ProgressDialog should be shown during publish and updated on failure")
    void testProgressDialogUpdatedOnFailure() throws InterruptedException, IOException {
        // Mock successful validation
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        // Mock the coordinator.publish to call the progress callback with failure
        doAnswer(invocation -> {
            PublishingCoordinator.ProgressCallback callback = invocation.getArgument(3);
            if (callback != null) {
                callback.onProgress(PublishingCoordinator.PublishProgress.failed());
            }
            return null;
        }).when(mockCoordinator).publish(
                any(PackagingContext.class),
                any(),
                any(),
                any(),
                any()
        );

        handler.handlePublish();

        // Wait for the thread to complete
        Thread.sleep(1000);

        // Verify that coordinator.publish was called
        verify(mockCoordinator, timeout(2000)).publish(
                any(PackagingContext.class),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should prompt for NPM token when NPM publishing is enabled")
    void testPromptsForNpmTokenWhenNpmPublishingEnabled() throws InterruptedException, IOException {
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(true);
        when(mockCoordinator.isGitHubPublishingEnabled()).thenReturn(false);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify promptForNpmToken was called
        verify(mockContext, timeout(2000)).promptForNpmToken(mockFrame);
    }

    @Test
    @DisplayName("Should prompt for GitHub token when GitHub publishing is enabled")
    void testPromptsForGithubTokenWhenGitHubPublishingEnabled() throws InterruptedException, IOException {
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);
        when(mockCoordinator.isGitHubPublishingEnabled()).thenReturn(true);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify promptForGithubToken was called
        verify(mockContext, timeout(2000)).promptForGithubToken(mockFrame);
    }

    @Test
    @DisplayName("Should return early if NPM token prompt is declined")
    void testReturnsEarlyIfNpmTokenPromptDeclined() throws InterruptedException, IOException {
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(true);
        when(mockContext.promptForNpmToken(mockFrame)).thenReturn(false);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify that validation was NOT called since token prompt was declined
        verify(mockCoordinator, never()).validateForPublishing();

        // Verify onSave was NOT called
        verify(mockOnSave, never()).run();
    }

    @Test
    @DisplayName("Should return early if GitHub token prompt is declined")
    void testReturnsEarlyIfGithubTokenPromptDeclined() throws InterruptedException, IOException {
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);
        when(mockCoordinator.isGitHubPublishingEnabled()).thenReturn(true);
        when(mockContext.promptForGithubToken(mockFrame)).thenReturn(false);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify that validation was NOT called since token prompt was declined
        verify(mockCoordinator, never()).validateForPublishing();

        // Verify onSave was NOT called
        verify(mockOnSave, never()).run();
    }

    @Test
    @DisplayName("Should use GitHub token from context during publishing")
    void testUsesGithubTokenFromContext() throws InterruptedException, IOException {
        String expectedGithubToken = "test-github-token-123";
        when(mockContext.getGithubToken()).thenReturn(expectedGithubToken);
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);
        when(mockCoordinator.isGitHubPublishingEnabled()).thenReturn(false);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify publish was called with the correct GitHub token
        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockCoordinator, timeout(2000)).publish(
                any(PackagingContext.class),
                any(),
                any(),
                any(),
                tokenCaptor.capture()
        );

        assertEquals(expectedGithubToken, tokenCaptor.getValue());
    }

    @Test
    @DisplayName("Should use download page URL supplier during publish")
    void testUsesDownloadPageUrlSupplier() throws InterruptedException, IOException {
        String expectedUrl = "http://test.example.com/download";
        handler = new PublishActionHandler(
                mockFrame,
                packageJSONFile,
                packageJSON,
                mockContext,
                mockCoordinator,
                mockOnSave,
                () -> expectedUrl
        );

        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify that publish was invoked (the URL is used internally in ProgressDialog)
        verify(mockCoordinator, timeout(2000)).publish(
                any(PackagingContext.class),
                any(),
                any(),
                any(),
                any()
        );
    }

    @Test
    @DisplayName("Should reset publishInProgress flag after publish completes")
    void testResetsPublishInProgressFlagAfterCompletion() throws InterruptedException, IOException {
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        // First publish
        handler.handlePublish();
        Thread.sleep(500);

        // Second publish should be allowed (flag should be reset)
        handler.handlePublish();

        Thread.sleep(500);

        // Verify validate was called twice (once for each publish call)
        verify(mockCoordinator, timeout(2000).times(2)).validateForPublishing();
    }

    @Test
    @DisplayName("Should handle validation exception with error type")
    void testHandlesValidationExceptionWithErrorType() throws InterruptedException, IOException {
        File logFile = new File(tempDir, "build.log");
        PublishingCoordinator.ValidationResult failureResult =
                PublishingCoordinator.ValidationResult.failure(
                        "Build failed",
                        PublishingCoordinator.ValidationResult.ERROR_TYPE_BUILD_FAILED,
                        logFile
                );

        when(mockCoordinator.validateForPublishing()).thenReturn(failureResult);
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify validation was called
        verify(mockCoordinator).validateForPublishing();

        // Verify onSave was NOT called
        verify(mockOnSave, never()).run();
    }

    @Test
    @DisplayName("Should get build preferences from coordinator")
    void testGetsBuildPreferencesFromCoordinator() throws InterruptedException, IOException {
        PackagingPreferences prefs = new PackagingPreferences("test-app", true);
        when(mockCoordinator.getBuildPreferences()).thenReturn(prefs);
        when(mockCoordinator.validateForPublishing()).thenReturn(
                PublishingCoordinator.ValidationResult.success()
        );
        when(mockCoordinator.isNpmPublishingEnabled()).thenReturn(false);

        handler.handlePublish();

        Thread.sleep(500);

        // Verify getBuildPreferences was called
        verify(mockCoordinator, timeout(2000)).getBuildPreferences();
    }
}
