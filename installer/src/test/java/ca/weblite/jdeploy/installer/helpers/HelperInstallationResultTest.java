package ca.weblite.jdeploy.installer.helpers;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HelperInstallationResult.
 */
public class HelperInstallationResultTest {

    @Test
    public void testSuccess_CreatesSuccessfulResult() {
        File helperExe = new File("/path/to/helper.exe");
        File contextDir = new File("/path/to/.jdeploy-files");

        HelperInstallationResult result = HelperInstallationResult.success(helperExe, contextDir);

        assertTrue(result.isSuccess(), "Should be successful");
        assertEquals(helperExe, result.getHelperExecutable(), "Helper executable should match");
        assertEquals(contextDir, result.getHelperContextDirectory(), "Context directory should match");
        assertNull(result.getErrorMessage(), "Error message should be null for success");
    }

    @Test
    public void testFailure_CreatesFailedResult() {
        String errorMessage = "Something went wrong";

        HelperInstallationResult result = HelperInstallationResult.failure(errorMessage);

        assertFalse(result.isSuccess(), "Should not be successful");
        assertNull(result.getHelperExecutable(), "Helper executable should be null");
        assertNull(result.getHelperContextDirectory(), "Context directory should be null");
        assertEquals(errorMessage, result.getErrorMessage(), "Error message should match");
    }

    @Test
    public void testFailureWithPaths_PreservesPaths() {
        String errorMessage = "Partial failure";
        File helperExe = new File("/path/to/helper.exe");
        File contextDir = new File("/path/to/.jdeploy-files");

        HelperInstallationResult result = HelperInstallationResult.failure(errorMessage, helperExe, contextDir);

        assertFalse(result.isSuccess(), "Should not be successful");
        assertEquals(helperExe, result.getHelperExecutable(), "Helper executable should be preserved");
        assertEquals(contextDir, result.getHelperContextDirectory(), "Context directory should be preserved");
        assertEquals(errorMessage, result.getErrorMessage(), "Error message should match");
    }

    @Test
    public void testToString_Success() {
        File helperExe = new File("/path/to/helper.exe");
        File contextDir = new File("/path/to/.jdeploy-files");

        HelperInstallationResult result = HelperInstallationResult.success(helperExe, contextDir);
        String toString = result.toString();

        assertTrue(toString.contains("success=true"), "Should indicate success");
        assertTrue(toString.contains("helperExecutable"), "Should contain executable path");
        assertTrue(toString.contains("helperContextDirectory"), "Should contain context directory");
    }

    @Test
    public void testToString_Failure() {
        String errorMessage = "Test error";

        HelperInstallationResult result = HelperInstallationResult.failure(errorMessage);
        String toString = result.toString();

        assertTrue(toString.contains("success=false"), "Should indicate failure");
        assertTrue(toString.contains(errorMessage), "Should contain error message");
    }
}
