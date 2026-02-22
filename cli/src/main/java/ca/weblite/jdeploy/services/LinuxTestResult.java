package ca.weblite.jdeploy.services;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a Linux Docker test run.
 */
public class LinuxTestResult {

    /**
     * Status of a verification check.
     */
    public enum CheckStatus {
        PASSED,
        FAILED,
        SKIPPED
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

        public Check(String name, CheckStatus status) {
            this(name, status, null);
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

    public LinuxTestResult() {
    }

    public boolean isSuccess() {
        return success;
    }

    public LinuxTestResult setSuccess(boolean success) {
        this.success = success;
        return this;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LinuxTestResult setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }

    public List<Check> getChecks() {
        return checks;
    }

    public LinuxTestResult addCheck(Check check) {
        this.checks.add(check);
        return this;
    }

    public int getExitCode() {
        return success ? 0 : 1;
    }

    /**
     * Creates a failed result with error message.
     */
    public static LinuxTestResult failed(String errorMessage) {
        return new LinuxTestResult()
                .setSuccess(false)
                .setErrorMessage(errorMessage)
                .addCheck(new Check("Error", CheckStatus.FAILED, errorMessage));
    }

    /**
     * Creates a timeout result.
     */
    public static LinuxTestResult timeout(int minutes) {
        return new LinuxTestResult()
                .setSuccess(false)
                .setErrorMessage("Test timed out after " + minutes + " minutes")
                .addCheck(new Check("Timeout", CheckStatus.FAILED,
                        "Container did not complete within " + minutes + " minutes"));
    }

    /**
     * Parses a result from JSON (from verification script output).
     */
    public static LinuxTestResult fromJson(JSONObject json) {
        LinuxTestResult result = new LinuxTestResult();

        result.setSuccess(json.optBoolean("AllPassed", false));

        JSONArray checksArray = json.optJSONArray("Checks");
        if (checksArray != null) {
            for (int i = 0; i < checksArray.length(); i++) {
                JSONObject checkObj = checksArray.getJSONObject(i);
                String name = checkObj.optString("Name", "Unknown");
                String statusStr = checkObj.optString("Status", "FAILED");
                String details = checkObj.optString("Details", null);

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
     * Prints the result to the output stream.
     */
    public void print(PrintStream out) {
        out.println();

        if (success) {
            out.println("\u2713 Linux installation verified");
        } else {
            out.println("\u2717 Linux installation verification failed");
            if (errorMessage != null) {
                out.println("  Error: " + errorMessage);
            }
        }

        if (!checks.isEmpty()) {
            int passed = 0;
            int total = checks.size();

            for (Check check : checks) {
                if (check.getStatus() == CheckStatus.PASSED) {
                    passed++;
                }
            }

            out.println("  Checks passed: " + passed + "/" + total);

            for (Check check : checks) {
                String symbol;
                switch (check.getStatus()) {
                    case PASSED:
                        symbol = "\u2713";
                        break;
                    case FAILED:
                        symbol = "\u2717";
                        break;
                    default:
                        symbol = "-";
                }

                out.print("  " + symbol + " " + check.getName() + ": " + check.getStatus());
                if (check.getDetails() != null) {
                    out.print(" (" + check.getDetails() + ")");
                }
                out.println();
            }
        }

        out.println();
    }
}
