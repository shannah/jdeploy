package ca.weblite.jdeploy.installer.services;

import java.util.Objects;

/**
 * Immutable value object representing the state of a service during update lifecycle.
 *
 * Tracks whether a service was running before update, allowing the installer to
 * restore the correct state after update completes.
 *
 * @author Steve Hannah
 */
public final class ServiceState {
    private final ServiceDescriptor descriptor;
    private final boolean wasRunning;

    public ServiceState(ServiceDescriptor descriptor, boolean wasRunning) {
        if (descriptor == null) {
            throw new IllegalArgumentException("descriptor cannot be null");
        }
        this.descriptor = descriptor;
        this.wasRunning = wasRunning;
    }

    public ServiceDescriptor getDescriptor() {
        return descriptor;
    }

    public String getCommandName() {
        return descriptor.getCommandName();
    }

    public boolean wasRunning() {
        return wasRunning;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceState that = (ServiceState) o;
        return wasRunning == that.wasRunning &&
               Objects.equals(descriptor, that.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor, wasRunning);
    }

    @Override
    public String toString() {
        return "ServiceState{" +
               "command='" + getCommandName() + '\'' +
               ", wasRunning=" + wasRunning +
               '}';
    }
}
