package ca.weblite.jdeploy.services.verification;

/**
 * Represents a single verification check result.
 */
public class VerificationCheck {

    public enum Status {
        PASSED,
        FAILED,
        SKIPPED
    }

    private final String name;
    private final Status status;
    private final String details;
    private final String skipReason;

    private VerificationCheck(String name, Status status, String details, String skipReason) {
        this.name = name;
        this.status = status;
        this.details = details;
        this.skipReason = skipReason;
    }

    public static VerificationCheck passed(String name) {
        return new VerificationCheck(name, Status.PASSED, null, null);
    }

    public static VerificationCheck failed(String name, String details) {
        return new VerificationCheck(name, Status.FAILED, details, null);
    }

    public static VerificationCheck skipped(String name, String skipReason) {
        return new VerificationCheck(name, Status.SKIPPED, null, skipReason);
    }

    public String getName() {
        return name;
    }

    public Status getStatus() {
        return status;
    }

    public String getDetails() {
        return details;
    }

    public String getSkipReason() {
        return skipReason;
    }

    public boolean isPassed() {
        return status == Status.PASSED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isSkipped() {
        return status == Status.SKIPPED;
    }

    @Override
    public String toString() {
        switch (status) {
            case PASSED:
                return "[PASS] " + name;
            case FAILED:
                return "[FAIL] " + name + ": " + details;
            case SKIPPED:
                return "[SKIP] " + name + ": " + skipReason;
            default:
                return name;
        }
    }
}
