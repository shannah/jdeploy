package ca.weblite.jdeploy.services;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a Windows Docker test run.
 */
public class WindowsTestResult {

    /**
     * Status of an individual check.
     */
    public enum CheckStatus {
        PASSED, FAILED, SKIPPED
    }

    /**
     * A single verification check result.
     */
    public static class Check {
        private final String name;
        private final CheckStatus status;
        private final String details;

        public Check(String name, CheckStatus status, String details) {
            this.name = name;
            this.status = status;
            this.details = details;
        }

        public String getName() {
            return name;
        }

        public CheckStatus getStatus() {
            return status;
        }

        public String getDetails() {
            return details;
        }
    }

    private boolean success;
    private String errorMessage;
    private List<Check> checks = new ArrayList<>();
    private String timestamp;

    public WindowsTestResult() {
    }

    public boolean isSuccess() {
        return success;
    }

    public WindowsTestResult setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public WindowsTestResult setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public List<Check> getChecks() {
        return checks;
    }

    public WindowsTestResult setChecks(List<Check> checks) {
        this.checks = checks;
        return this;
    }

    public WindowsTestResult addCheck(Check check) {
        this.checks.add(check);
        return this;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public WindowsTestResult setTimestamp(String timestamp) {
        this.timestamp = timestamp;
        return this;
    }

    /**
     * Returns exit code: 0 for success, 1 for failure.
     */
    public int getExitCode() {
        return success ? 0 : 1;
    }

    /**
     * Counts the number of passed checks.
     */
    public int getPassedCount() {
        return (int) checks.stream()
                .filter(c -> c.getStatus() == CheckStatus.PASSED)
                .count();
    }

    /**
     * Counts the number of failed checks.
     */
    public int getFailedCount() {
        return (int) checks.stream()
                .filter(c -> c.getStatus() == CheckStatus.FAILED)
                .count();
    }

    /**
     * Prints the result to the given output stream.
     */
    public void print(PrintStream out) {
        if (success) {
            out.println();
            out.println("✓ Windows installation verified");
            out.println("  Checks passed: " + getPassedCount() + "/" + checks.size());
            for (Check check : checks) {
                String statusSymbol = check.getStatus() == CheckStatus.PASSED ? "✓" :
                        check.getStatus() == CheckStatus.FAILED ? "✗" : "○";
                out.println("  " + statusSymbol + " " + check.getName() + ": " + check.getStatus());
            }
        } else {
            out.println();
            out.println("✗ Windows installation verification failed");
            if (errorMessage != null && !errorMessage.isEmpty()) {
                out.println("  Error: " + errorMessage);
            }
            out.println("  Checks passed: " + getPassedCount() + "/" + checks.size());
            for (Check check : checks) {
                String statusSymbol = check.getStatus() == CheckStatus.PASSED ? "✓" :
                        check.getStatus() == CheckStatus.FAILED ? "✗" : "○";
                out.print("  " + statusSymbol + " " + check.getName() + ": " + check.getStatus());
                if (check.getStatus() == CheckStatus.FAILED && check.getDetails() != null) {
                    out.print(" - " + check.getDetails());
                }
                out.println();
            }
        }
    }

    /**
     * Parses a WindowsTestResult from JSON verification results.
     *
     * @param json The JSON object from verification-checks.ps1
     * @return Parsed result
     */
    public static WindowsTestResult fromJson(JSONObject json) {
        WindowsTestResult result = new WindowsTestResult();

        result.setTimestamp(json.optString("Timestamp", null));
        result.setSuccess(json.optBoolean("AllPassed", false));

        JSONArray checksArray = json.optJSONArray("Checks");
        if (checksArray != null) {
            for (int i = 0; i < checksArray.length(); i++) {
                JSONObject checkJson = checksArray.getJSONObject(i);
                String name = checkJson.optString("Name", "Unknown");
                String statusStr = checkJson.optString("Status", "FAILED");
                String details = checkJson.optString("Details", null);

                CheckStatus status;
                switch (statusStr.toUpperCase()) {
                    case "PASSED":
                        status = CheckStatus.PASSED;
                        break;
                    case "SKIPPED":
                        status = CheckStatus.SKIPPED;
                        break;
                    default:
                        status = CheckStatus.FAILED;
                }

                result.addCheck(new Check(name, status, details));
            }
        }

        return result;
    }

    /**
     * Creates a failed result with an error message.
     */
    public static WindowsTestResult failed(String errorMessage) {
        return new WindowsTestResult()
                .setSuccess(false)
                .setErrorMessage(errorMessage);
    }

    /**
     * Creates a result indicating timeout.
     */
    public static WindowsTestResult timeout(int timeoutMinutes) {
        return failed("Windows container timed out after " + timeoutMinutes + " minutes");
    }
}
